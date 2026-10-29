package net.harou.schematiccannon.client;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;

import com.mojang.blaze3d.vertex.PoseStack;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.client.gui.GuiTextures;
import net.harou.schematiccannon.client.outline.AABBOutline;
import net.harou.schematiccannon.client.render.ModBufferSource;
import net.harou.schematiccannon.client.screen.ToolSelectionScreen;
import net.harou.schematiccannon.client.tools.DeployTool;
import net.harou.schematiccannon.client.tools.SchematicTool;
import net.harou.schematiccannon.client.tools.ToolType;
import net.harou.schematiccannon.network.SchematicPlacePacket;
import net.harou.schematiccannon.network.SchematicSyncPacket;
import net.harou.schematiccannon.registry.ModBlocks;
import net.harou.schematiccannon.registry.ModDataComponents;
import net.harou.schematiccannon.schematic.SchematicItem;
import net.harou.schematiccannon.schematic.level.SchematicLevel;
import net.harou.schematiccannon.util.Lang;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Client state of the schematic in the player's main hand: preview, tools and syncing placement to the server.
 */
public class SchematicHandler {

	private static final int SYNC_DELAY = 10;
	/** Downloads that answer within this many ticks show no indicator, so it does not flicker */
	private static final int DOWNLOAD_INDICATOR_DELAY = 5;
	private static final int OUTLINE_COLOR = 0x6886c5;
	private static final int INVALID_OUTLINE_COLOR = 0xd04040;
	/** The action bar fades after a few seconds, so a lasting message (e.g. a placement problem) is shown again this often */
	private static final int MESSAGE_REFRESH_TICKS = 40;
	/** How long a message about something that happened (a tool's message, a failed download) is shown */
	private static final int EVENT_MESSAGE_TICKS = 40;

	/** The cache key of the shown schematic's source */
	private String displayedSchematic;
	/** Deployed, but the template is still downloading */
	private boolean waitingForTemplate;
	/** The download's progress or failure, shown before placement problems, see {@link #updateActionBar} */
	@Nullable
	private Component downloadMessage;
	private int downloadMessageTicks;
	/** How long the answer to a sync is waited for, e.g. in case the server had another item in the slot */
	private static final int SYNC_ANSWER_TIMEOUT = 200;
	/** Our syncs the server hasn't answered yet, by inventory slot; see tickPendingSyncs */
	private final Int2ObjectMap<PendingSync> pendingSyncs = new Int2ObjectOpenHashMap<>();

	/** A placement of the schematic: where, turned and mirrored how */
	private record Placement(BlockPos anchor, Rotation rotation, Mirror mirror) {
	}

	/** The latest sync of a slot's schematic, until the server's answer to it arrives */
	private static class PendingSync {
		/** The schematic's cache key */
		String schematic;
		/** Of all syncs since the last answer, the latest last */
		final IntList ids = new IntArrayList();
		boolean deployed;
		Placement placement;
		int ticksLeft;

		void applyTo(ItemStack stack) {
			stack.set(ModDataComponents.SCHEMATIC_DEPLOYED, deployed);
			stack.set(ModDataComponents.SCHEMATIC_ANCHOR, placement.anchor());
			stack.set(ModDataComponents.SCHEMATIC_ROTATION, placement.rotation());
			stack.set(ModDataComponents.SCHEMATIC_MIRROR, placement.mirror());
		}
	}

	/** Why the server would not print the schematic at serverCheckedPlacement, null if it would */
	@Nullable
	private Component serverPlacementProblem;
	/** What the server's answer is for; it doesn't count for anything else, e.g. once the schematic was moved or turned */
	@Nullable
	private Placement serverCheckedPlacement;
	/** Why the deployed schematic may not be printed: the source's client check (see {@link ClientPlacementChecks}), else the server's */
	@Nullable
	private Component deployedProblem;
	/** Why the schematic may not be deployed where the deploy tool would put it, from the client check */
	@Nullable
	private Component previewProblem;
	/**
	 * A tool's message, shown before anything else as it lasts only briefly, see {@link #showToolMessage}; kept across
	 * re-inits
	 */
	@Nullable
	private Component toolMessage;
	private int toolMessageTicks;
	/** What the action bar shows, null if nothing of this handler */
	@Nullable
	private Component shownMessage;
	private int shownMessageTicks;
	private SchematicTransformation transformation;
	private AABB bounds;
	private boolean deployed;
	private boolean active;
	private SchematicTool currentTool;

	private int syncCooldown;
	private int activeHotbarSlot;
	private ItemStack activeSchematicItem;
	private AABBOutline outline;

	private final SchematicRenderer[] renderers = new SchematicRenderer[3];
	private ToolSelectionScreen selectionScreen;
	private boolean toolMenuKeyDown;
	private boolean creative;

	public SchematicHandler() {
		currentTool = ToolType.DEPLOY;
		selectionScreen = new ToolSelectionScreen(List.of(ToolType.DEPLOY), this::equip);
		transformation = new SchematicTransformation();
	}

	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		tickPendingSyncs(player.getInventory());
		ItemStack stack = mc.gameMode.getPlayerMode() == GameType.SPECTATOR ? null : findBlueprintInHand(player);
		int slot = player.getInventory()
			.getSelectedSlot();

		// Switched to another item, slot or schematic: the previous one keeps its pending changes
		if (active && (stack == null || slot != activeHotbarSlot || player.isCreative() != creative
			|| !SchematicItem.getSource(stack)
				.cacheKey(stack)
				.equals(displayedSchematic)))
			deactivate(true);
		if (stack == null)
			return;
		if (active && stack != activeSchematicItem) {
			// The server corrected an outdated size (see SchematicDownloads): shown again, keeping a change of ours
			if (!showsSizeOf(stack))
				deactivate(true);
			// Another stack of the same schematic, e.g. a copy swapped into the slot, shows its own placement, as does a
			// placement set elsewhere, e.g. by a command. A stack the server replaced has the shown placement, as ours was
			// synced (see tickPendingSyncs); while a change of ours is not, it wins.
			else if (syncCooldown == 0 && !showsPlacementOf(stack))
				deactivate(false);
		}

		// The client stack is replaced whenever the server syncs it
		activeSchematicItem = stack;
		if (!active)
			init(player, stack, slot);
		transformation.tick();
		if (waitingForTemplate)
			tickDownload();

		boolean keyDown = mc.gui.screen() == null && ModKeys.TOOL_MENU.isDown();
		if (keyDown != toolMenuKeyDown)
			onToolMenuKey(keyDown);

		if (syncCooldown > 0)
			syncCooldown--;
		if (syncCooldown == 1)
			sync();

		selectionScreen.update();
		currentTool.getTool()
			.updateSelection();
		updatePlacementProblem();
	}

	/**
	 * Checks where the schematic is deployed and where the deploy tool would put it, which may both be shown at once, and
	 * shows the problem with the one the player is placing.
	 */
	private void updatePlacementProblem() {
		BlockPos previewTarget = currentTool.getTool() instanceof DeployTool deployTool ? deployTool.getDeployTarget()
			: null;
		previewProblem = previewTarget == null ? null : checkOnClient(transformation.anchorFor(previewTarget));
		// The client check covers the schematic while it moves, the server's answer the synced placement
		Component deployedClientProblem = deployed ? checkOnClient(transformation.getAnchor()) : null;
		deployedProblem = deployedClientProblem != null ? deployedClientProblem
			: deployed && shownPlacement().equals(serverCheckedPlacement) ? serverPlacementProblem : null;

		// Not known before the template is here
		Component problem = waitingForTemplate ? null : previewTarget != null ? previewProblem : deployedProblem;
		updateActionBar(problem);
	}

	/**
	 * Shows one message in the action bar, the first of: a recent tool message, the download's progress or failure,
	 * the placement problem.
	 */
	private void updateActionBar(@Nullable Component problem) {
		if (downloadMessageTicks > 0 && --downloadMessageTicks == 0)
			downloadMessage = null;
		if (toolMessageTicks > 0 && --toolMessageTicks == 0)
			toolMessage = null;
		Component message = toolMessage != null ? toolMessage
			: downloadMessage != null ? downloadMessage
			: problem != null ? problem.copy()
				.withStyle(ChatFormatting.RED) : null;

		Hud hud = Minecraft.getInstance().gui.hud;
		if (Objects.equals(message, shownMessage)) {
			if (message != null && ++shownMessageTicks >= MESSAGE_REFRESH_TICKS) {
				shownMessageTicks = 0;
				hud.setOverlayMessage(message, false);
			}
			return;
		}
		if (message != null)
			hud.setOverlayMessage(message, false);
		else if (shownMessage != null)
			hud.setOverlayMessage(CommonComponents.EMPTY, false);
		shownMessage = message;
		shownMessageTicks = 0;
	}

	@Nullable
	private Component checkOnClient(BlockPos anchor) {
		StructurePlaceSettings settings = transformation.toSettings();
		return ClientPlacementChecks.check(activeSchematicItem, SchematicItem.getWorldBounds(anchor,
			settings.getRotation(), settings.getMirror(), activeSchematicItem.get(ModDataComponents.SCHEMATIC_BOUNDS)));
	}

	private void init(LocalPlayer player, ItemStack stack, int slot) {
		loadSettings(stack);
		displayedSchematic = SchematicItem.getSource(stack)
			.cacheKey(stack);
		// Selecting the schematic again retries a failed download; start it early, so it is there when deployed
		ClientSchematicCache cache = SchematicCannonClient.SCHEMATIC_CACHE;
		cache.clearFailure(displayedSchematic);
		StructureTemplate cached = cache.get(displayedSchematic, slot);
		// The stack's size is outdated, e.g. another copy got the file uploaded again: downloading it again makes the
		// server correct it (see SchematicDownloads)
		if (cached != null && !cached.getSize()
			.equals(stack.get(ModDataComponents.SCHEMATIC_BOUNDS))) {
			cache.invalidate(displayedSchematic);
			cache.get(displayedSchematic, slot);
		}
		activeHotbarSlot = slot;
		creative = player.isCreative();
		active = true;
		serverPlacementProblem = null;
		deployedProblem = null;
		previewProblem = null;
		// Shows the message for this schematic, even if it reads the same
		shownMessage = null;
		// A download message was about the schematic shown before
		downloadMessage = null;
		downloadMessageTicks = 0;
		// Asks the server to check the placement, right away: it was just read from this stack, and a later sync could
		// land on another copy of the schematic swapped into the slot meanwhile
		if (deployed)
			sync();
		clearRenderers();
		if (deployed) {
			setupRenderer();
			// Keeps the tool, e.g. when the schematic changed while it was being used
			List<SchematicTool> tools = ToolType.getTools(stack, creative);
			selectionScreen = new ToolSelectionScreen(tools, tools.contains(currentTool) ? currentTool : ToolType.MOVE,
				this::equip);
		} else
			selectionScreen = new ToolSelectionScreen(List.of(ToolType.DEPLOY), this::equip);
	}

	private void setupRenderer() {
		clearRenderers();
		Level clientWorld = Minecraft.getInstance().level;
		StructureTemplate schematic = SchematicCannonClient.SCHEMATIC_CACHE.get(displayedSchematic, activeHotbarSlot);
		// Until it is downloaded the outline shows the size, and the tools already work on it
		waitingForTemplate = schematic == null;
		if (schematic == null)
			return;
		Vec3i size = schematic.getSize();
		if (size.equals(Vec3i.ZERO))
			return;

		SchematicLevel w = new SchematicLevel(clientWorld);
		SchematicLevel wMirroredFB = new SchematicLevel(clientWorld);
		SchematicLevel wMirroredLR = new SchematicLevel(clientWorld);
		StructurePlaceSettings placementSettings = new StructurePlaceSettings();
		BlockPos pos = BlockPos.ZERO;

		try {
			schematic.placeInWorld(w, pos, pos, placementSettings, w.getRandom(), Block.UPDATE_CLIENTS);
		} catch (Exception e) {
			Minecraft.getInstance().player.sendSystemMessage(Lang.translate("schematic.error"));
			SchematicCannon.LOGGER.error("Failed to load Schematic for Previewing", e);
			return;
		}

		placementSettings.setMirror(Mirror.FRONT_BACK);
		pos = BlockPos.ZERO.east(size.getX() - 1);
		schematic.placeInWorld(wMirroredFB, pos, pos, placementSettings, wMirroredFB.getRandom(), Block.UPDATE_CLIENTS);

		placementSettings.setMirror(Mirror.LEFT_RIGHT);
		pos = BlockPos.ZERO.south(size.getZ() - 1);
		schematic.placeInWorld(wMirroredLR, pos, pos, placementSettings, wMirroredLR.getRandom(), Block.UPDATE_CLIENTS);

		renderers[0] = new SchematicRenderer(w, this::getLocalToWorld);
		renderers[1] = new SchematicRenderer(wMirroredFB, this::getLocalToWorld);
		renderers[2] = new SchematicRenderer(wMirroredLR, this::getLocalToWorld);
	}

	/**
	 * Builds the preview once the template arrived, and shows the download in the action bar while it takes long.
	 */
	private void tickDownload() {
		ClientSchematicCache cache = SchematicCannonClient.SCHEMATIC_CACHE;
		switch (cache.getStatus(displayedSchematic)) {
			case READY -> {
				setupRenderer();
				downloadMessage = null;
			}
			case FAILED -> {
				waitingForTemplate = false;
				downloadMessage = Lang.translate("schematic.downloadFailed")
					.withStyle(ChatFormatting.RED);
				downloadMessageTicks = EVENT_MESSAGE_TICKS;
			}
			case DOWNLOADING -> {
				// Asks again, in case the download was dropped, e.g. by a cache reset
				cache.get(displayedSchematic, activeHotbarSlot);
				if (cache.getDownloadTicks(displayedSchematic) > DOWNLOAD_INDICATOR_DELAY) {
					downloadMessage = Lang.translate("schematic.downloading",
						(int) (cache.getProgress(displayedSchematic) * 100));
					// Lasts while the download is running
					downloadMessageTicks = 2;
				}
			}
		}
	}

	/**
	 * Maps the preview's schematic coordinates to the world, like the transformation applied when rendering.
	 */
	private Matrix4fc getLocalToWorld() {
		PoseStack ms = new PoseStack();
		transformation.applyTransformations(ms, Vec3.ZERO, 1);
		return ms.last()
			.pose();
	}

	private void clearRenderers() {
		Arrays.fill(renderers, null);
	}

	/**
	 * Stops showing the schematic.
	 *
	 * @param syncPending send placement changes that are still waiting for the sync delay
	 */
	private void deactivate(boolean syncPending) {
		if (syncPending && syncCooldown > 0) {
			// The selected slot may hold another item by now
			int slot = findActiveItemSlot();
			if (slot != -1)
				sync(slot);
		}
		active = false;
		waitingForTemplate = false;
		toolMenuKeyDown = false;
		syncCooldown = 0;
		activeSchematicItem = null;
		displayedSchematic = null;
		clearRenderers();
	}

	/**
	 * Drops all state of the current world, e.g. on disconnect.
	 */
	public void reset() {
		deactivate(false);
		pendingSyncs.clear();
	}

	/**
	 * The preview holds on to the level it was built in.
	 */
	public void onLevelChange() {
		deactivate(true);
	}

	/**
	 * Submits the schematic preview to the world's render queue, so it is drawn together with entities.
	 */
	public void renderSchematic(PoseStack ms, SubmitNodeCollector collector, CameraRenderState cameraState, Vec3 camera,
		float pt) {
		if (!active || !deployed)
			return;

		ms.pushPose();
		transformation.applyTransformations(ms, camera, pt);
		boolean lr = transformation.getScaleLR()
			.getValue(pt) < 0;
		boolean fb = transformation.getScaleFB()
			.getValue(pt) < 0;
		if (lr && !fb && renderers[2] != null)
			renderers[2].render(ms, collector, cameraState, camera, pt);
		else if (fb && !lr && renderers[1] != null)
			renderers[1].render(ms, collector, cameraState, camera, pt);
		else if (renderers[0] != null)
			renderers[0].render(ms, collector, cameraState, camera, pt);
		ms.popPose();
	}

	/**
	 * Draws outlines and tool overlays on top of the world.
	 */
	public void renderOverlays(PoseStack ms, ModBufferSource buffer, Vec3 camera, float pt) {
		if (!active)
			return;

		ms.pushPose();
		currentTool.getTool()
			.renderTool(ms, buffer, camera, pt);
		ms.popPose();

		ms.pushPose();
		transformation.applyTransformations(ms, camera, pt);
		currentTool.getTool()
			.renderOnSchematic(ms, buffer, pt);
		ms.popPose();
	}

	public void updateRenderers() {
		for (SchematicRenderer renderer : renderers)
			if (renderer != null)
				renderer.update();
	}

	public void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!active)
			return;
		float pt = deltaTracker.getGameTimeDeltaPartialTick(false);
		renderHotbarSlotOverlay(graphics);
		selectionScreen.renderPassive(graphics, pt);
	}

	private void renderHotbarSlotOverlay(GuiGraphicsExtractor graphics) {
		int x = graphics.guiWidth() / 2 - 88;
		int y = graphics.guiHeight() - 19;
		x += 20 * activeHotbarSlot;
		GuiTextures.SCHEMATIC_SLOT.render(graphics, x, y);
		// Upstream drew the highlight behind the hotbar items; the 2D HUD has no depth, so draw the item again on top
		Minecraft mc = Minecraft.getInstance();
		graphics.item(mc.player, activeSchematicItem, x, y, activeHotbarSlot + 1);
		graphics.itemDecorations(mc.font, activeSchematicItem, x, y);
	}

	public boolean onUse() {
		if (!active)
			return false;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player.isShiftKeyDown())
			return false;
		if (mc.hitResult instanceof BlockHitResult blockHitResult
			&& mc.level.getBlockState(blockHitResult.getBlockPos())
				.is(ModBlocks.SCHEMATICANNON))
			return false;
		return currentTool.getTool()
			.handleRightClick();
	}

	private void onToolMenuKey(boolean pressed) {
		toolMenuKeyDown = pressed;
		if (pressed && !selectionScreen.focused)
			selectionScreen.focused = true;
		if (!pressed && selectionScreen.focused) {
			selectionScreen.focused = false;
			selectionScreen.onClose();
		}
	}

	/**
	 * Whether the mouse wheel is used for the tools right now, so vanilla must not get it.
	 */
	public boolean wantsScroll() {
		return active && (selectionScreen.focused || ModKeys.sprintKeyDown() && currentTool.getTool()
			.usesMouseWheel());
	}

	public boolean mouseScrolled(int delta) {
		if (!active)
			return false;

		if (selectionScreen.focused) {
			selectionScreen.cycle(delta);
			return true;
		}
		if (ModKeys.sprintKeyDown())
			return currentTool.getTool()
				.handleMouseWheel(delta);
		return false;
	}

	@Nullable
	private ItemStack findBlueprintInHand(Player player) {
		ItemStack stack = player.getMainHandItem();
		return SchematicItem.isValid(stack) ? stack : null;
	}

	public void markDirty() {
		syncCooldown = SYNC_DELAY;
	}

	public void sync() {
		if (activeSchematicItem != null)
			sync(activeHotbarSlot);
	}

	private void sync(int slot) {
		StructurePlaceSettings settings = transformation.toSettings();
		BlockPos anchor = transformation.getAnchor();
		int syncId = ThreadLocalRandom.current()
			.nextInt();
		ClientPlayNetworking.send(new SchematicSyncPacket(slot, settings, anchor, deployed, syncId));
		ItemStack stack = Minecraft.getInstance().player.getInventory()
			.getItem(slot);
		if (!SchematicItem.isValid(stack))
			return;

		String schematic = SchematicItem.getSource(stack)
			.cacheKey(stack);
		PendingSync pending = pendingSyncs.computeIfAbsent(slot, s -> new PendingSync());
		// Earlier ids were for another schematic swapped into the slot, whose stack must not get this placement
		if (!schematic.equals(pending.schematic))
			pending.ids.clear();
		pending.schematic = schematic;
		pending.ids.add(syncId);
		pending.deployed = deployed;
		pending.placement = new Placement(anchor, settings.getRotation(), settings.getMirror());
		pending.ticksLeft = SYNC_ANSWER_TIMEOUT;
		// Same as the server does, so a re-init before its answer arrives shows this placement; without the id, which
		// tells the answer apart
		pending.applyTo(stack);
	}

	/**
	 * Keeps the placements of our syncs on their stacks until the server answers the latest. It answers in order, each
	 * with the id of the sync (see {@link ModDataComponents#SCHEMATIC_SYNC_ID}); a stack with an earlier id is the answer
	 * to an earlier sync, which would show where the schematic was a moment ago, so it gets the latest placement again.
	 * Stacks with other ids are not ours: another copy swapped into the slot, or a placement set elsewhere after the
	 * answer, e.g. by a command.
	 */
	private void tickPendingSyncs(Inventory inventory) {
		pendingSyncs.int2ObjectEntrySet()
			.removeIf(entry -> {
				PendingSync pending = entry.getValue();
				ItemStack stack = inventory.getItem(entry.getIntKey());
				Integer syncId = stack.get(ModDataComponents.SCHEMATIC_SYNC_ID);
				if (syncId != null && syncId == pending.ids.getInt(pending.ids.size() - 1))
					return true;
				if (syncId != null && pending.ids.contains(syncId.intValue()))
					pending.applyTo(stack);
				return --pending.ticksLeft <= 0;
			});
	}

	/**
	 * The inventory slot of the schematic this handler showed, or -1 if it is gone. A stack the server replaced is only
	 * recognised in its hotbar slot: elsewhere a schematic with the same cache key may be a copy (see the cloning
	 * recipe), whose own placement must not be overwritten.
	 */
	private int findActiveItemSlot() {
		Inventory inventory = Minecraft.getInstance().player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++)
			if (inventory.getItem(i) == activeSchematicItem)
				return i;
		return showsSchematic(inventory.getItem(activeHotbarSlot)) ? activeHotbarSlot : -1;
	}

	/**
	 * Whether the schematic with the cache key is the one shown.
	 */
	public boolean showsSchematic(String key) {
		return showsSchematic(key::equals);
	}

	/**
	 * Whether the shown schematic's cache key matches.
	 */
	public boolean showsSchematic(Predicate<String> keys) {
		return active && displayedSchematic != null && keys.test(displayedSchematic);
	}

	private boolean showsSchematic(ItemStack stack) {
		return SchematicItem.isValid(stack) && displayedSchematic.equals(SchematicItem.getSource(stack)
			.cacheKey(stack));
	}

	public void equip(SchematicTool tool) {
		this.currentTool = tool;
		currentTool.getTool()
			.init();
	}

	public void loadSettings(ItemStack blueprint) {
		StructurePlaceSettings settings = SchematicItem.getSettings(blueprint);
		transformation = new SchematicTransformation();

		deployed = blueprint.getOrDefault(ModDataComponents.SCHEMATIC_DEPLOYED, false);
		BlockPos anchor = blueprint.getOrDefault(ModDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO);
		Vec3i size = blueprint.get(ModDataComponents.SCHEMATIC_BOUNDS);
		bounds = new AABB(0, 0, 0, size.getX(), size.getY(), size.getZ());
		outline = new AABBOutline(bounds);
		outline.getParams()
			.colored(getOutlineColor())
			.lineWidth(1 / 16f);
		transformation.init(anchor, settings, bounds);
	}

	public void deploy() {
		if (!active)
			return;
		if (!deployed) {
			List<SchematicTool> tools = ToolType.getTools(activeSchematicItem, creative);
			selectionScreen = new ToolSelectionScreen(tools, ToolType.MOVE, this::equip);
		}
		deployed = true;
		// The server checks the new placement once it is synced
		serverPlacementProblem = null;
		setupRenderer();
	}

	public String getCurrentSchematicName() {
		return activeSchematicItem != null ? SchematicItem.getSource(activeSchematicItem)
			.displayName(activeSchematicItem)
			.getString() : "-";
	}

	public void printInstantly() {
		// The server prints this stack, so it needs the placement that is shown, which may not be synced yet
		ItemStack print = activeSchematicItem.copy();
		StructurePlaceSettings settings = transformation.toSettings();
		print.set(ModDataComponents.SCHEMATIC_DEPLOYED, true);
		print.set(ModDataComponents.SCHEMATIC_ANCHOR, transformation.getAnchor());
		print.set(ModDataComponents.SCHEMATIC_ROTATION, settings.getRotation());
		print.set(ModDataComponents.SCHEMATIC_MIRROR, settings.getMirror());
		ClientPlayNetworking.send(new SchematicPlacePacket(print));
		// Undeploy, keeping the placement for the next deploy
		deployed = false;
		sync();
		// Shown again undeployed on the next tick
		deactivate(false);
	}

	public boolean isActive() {
		return active;
	}

	public AABB getBounds() {
		return bounds;
	}

	public SchematicTransformation getTransformation() {
		return transformation;
	}

	public boolean isDeployed() {
		return deployed;
	}

	public ItemStack getActiveSchematicItem() {
		return activeSchematicItem;
	}

	/** Whether the preview has the stack's size */
	private boolean showsSizeOf(ItemStack stack) {
		Vec3i size = stack.get(ModDataComponents.SCHEMATIC_BOUNDS);
		return size != null && size.getX() == bounds.getXsize() && size.getY() == bounds.getYsize()
			&& size.getZ() == bounds.getZsize();
	}

	/** Whether the stack is placed where the preview shows it */
	private boolean showsPlacementOf(ItemStack stack) {
		return Objects.equals(placementOf(stack), deployed ? shownPlacement() : null);
	}

	/** The stack's placement, null if it isn't deployed */
	@Nullable
	private static Placement placementOf(ItemStack stack) {
		if (!stack.getOrDefault(ModDataComponents.SCHEMATIC_DEPLOYED, false))
			return null;
		return new Placement(stack.getOrDefault(ModDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO),
			stack.getOrDefault(ModDataComponents.SCHEMATIC_ROTATION, Rotation.NONE),
			stack.getOrDefault(ModDataComponents.SCHEMATIC_MIRROR, Mirror.NONE));
	}

	/**
	 * The server's check of the synced placement, shown with a red outline and the reason in the action bar. Only an
	 * answer for the placement shown now counts: not for an earlier one, or another copy of the schematic.
	 */
	public void onPlacementChecked(String key, BlockPos anchor, Rotation rotation, Mirror mirror,
		@Nullable Component problem) {
		Placement checked = new Placement(anchor, rotation, mirror);
		if (active && deployed && key.equals(displayedSchematic) && checked.equals(shownPlacement())) {
			serverPlacementProblem = problem;
			serverCheckedPlacement = checked;
		}
	}

	private Placement shownPlacement() {
		StructurePlaceSettings settings = transformation.toSettings();
		return new Placement(transformation.getAnchor(), settings.getRotation(), settings.getMirror());
	}

	/**
	 * Shows a tool's message in the action bar for a while, e.g. a value the player just changed with it. It comes
	 * before the download and placement problems, see {@link #updateActionBar}.
	 */
	public void showToolMessage(Component message) {
		toolMessage = message;
		toolMessageTicks = EVENT_MESSAGE_TICKS;
	}

	/**
	 * Color of the deployed schematic's outline.
	 */
	public int getOutlineColor() {
		return deployedProblem != null ? INVALID_OUTLINE_COLOR : OUTLINE_COLOR;
	}

	/**
	 * Color of the outline the deploy tool shows at the cursor.
	 */
	public int getPreviewOutlineColor() {
		return previewProblem != null ? INVALID_OUTLINE_COLOR : OUTLINE_COLOR;
	}

	public AABBOutline getOutline() {
		return outline;
	}

}
