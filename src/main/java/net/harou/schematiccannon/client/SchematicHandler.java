package net.harou.schematiccannon.client;

import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.client.gui.GuiTextures;
import net.harou.schematiccannon.client.outline.AABBOutline;
import net.harou.schematiccannon.client.render.ModBufferSource;
import net.harou.schematiccannon.client.screen.ToolSelectionScreen;
import net.harou.schematiccannon.client.tools.ToolType;
import net.harou.schematiccannon.network.SchematicPlacePacket;
import net.harou.schematiccannon.network.SchematicSyncPacket;
import net.harou.schematiccannon.registry.ModBlocks;
import net.harou.schematiccannon.registry.ModDataComponents;
import net.harou.schematiccannon.registry.ModItems;
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

	private String displayedSchematic;
	private SchematicTransformation transformation;
	private AABB bounds;
	private boolean deployed;
	private boolean active;
	private ToolType currentTool;

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
		ItemStack stack = mc.gameMode.getPlayerMode() == GameType.SPECTATOR ? null : findBlueprintInHand(player);
		int slot = player.getInventory()
			.getSelectedSlot();

		// Switched to another item, slot or schematic: the previous one keeps its pending changes
		if (active && (stack == null || slot != activeHotbarSlot || player.isCreative() != creative
			|| !stack.get(ModDataComponents.SCHEMATIC_FILE)
				.equals(displayedSchematic)))
			deactivate(true);
		if (stack == null)
			return;

		// The client stack is replaced whenever the server syncs it
		activeSchematicItem = stack;
		if (!active)
			init(player, stack, slot);
		transformation.tick();

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
	}

	private void init(LocalPlayer player, ItemStack stack, int slot) {
		loadSettings(stack);
		displayedSchematic = stack.get(ModDataComponents.SCHEMATIC_FILE);
		activeHotbarSlot = slot;
		creative = player.isCreative();
		active = true;
		clearRenderers();
		if (deployed) {
			setupRenderer();
			ToolType toolBefore = currentTool;
			List<ToolType> tools = ToolType.getTools(creative);
			selectionScreen = new ToolSelectionScreen(tools, this::equip);
			if (tools.contains(toolBefore)) {
				selectionScreen.setSelectedElement(toolBefore);
				equip(toolBefore);
			}
		} else
			selectionScreen = new ToolSelectionScreen(List.of(ToolType.DEPLOY), this::equip);
	}

	private void setupRenderer() {
		clearRenderers();
		Level clientWorld = Minecraft.getInstance().level;
		StructureTemplate schematic = SchematicItem.loadSchematic(clientWorld, activeSchematicItem);
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
		if (!stack.is(ModItems.SCHEMATIC))
			return null;
		if (!stack.has(ModDataComponents.SCHEMATIC_FILE) || !stack.has(ModDataComponents.SCHEMATIC_BOUNDS))
			return null;
		return stack;
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
		ClientPlayNetworking.send(new SchematicSyncPacket(slot, settings, anchor, deployed));
		// Same as the server does, so a re-init before its update arrives shows this placement
		ItemStack stack = Minecraft.getInstance().player.getInventory()
			.getItem(slot);
		if (stack.is(ModItems.SCHEMATIC)) {
			stack.set(ModDataComponents.SCHEMATIC_DEPLOYED, deployed);
			stack.set(ModDataComponents.SCHEMATIC_ANCHOR, anchor);
			stack.set(ModDataComponents.SCHEMATIC_ROTATION, settings.getRotation());
			stack.set(ModDataComponents.SCHEMATIC_MIRROR, settings.getMirror());
		}
	}

	/**
	 * The inventory slot of the schematic this handler showed, or -1 if it is gone.
	 */
	private int findActiveItemSlot() {
		Inventory inventory = Minecraft.getInstance().player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++)
			if (inventory.getItem(i) == activeSchematicItem)
				return i;
		if (displayedSchematic.equals(inventory.getItem(activeHotbarSlot)
			.get(ModDataComponents.SCHEMATIC_FILE)))
			return activeHotbarSlot;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ModItems.SCHEMATIC) && displayedSchematic.equals(stack.get(ModDataComponents.SCHEMATIC_FILE)))
				return i;
		}
		return -1;
	}

	public void equip(ToolType tool) {
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
			.colored(0x6886c5)
			.lineWidth(1 / 16f);
		transformation.init(anchor, settings, bounds);
	}

	public void deploy() {
		if (!active)
			return;
		if (!deployed) {
			List<ToolType> tools = ToolType.getTools(creative);
			selectionScreen = new ToolSelectionScreen(tools, this::equip);
		}
		deployed = true;
		setupRenderer();
	}

	public String getCurrentSchematicName() {
		return displayedSchematic != null ? displayedSchematic : "-";
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

	public AABBOutline getOutline() {
		return outline;
	}

}
