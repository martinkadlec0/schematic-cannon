package net.harou.schematiccannon.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.client.outline.Outliner;
import net.harou.schematiccannon.client.render.ModRenderTypes;
import net.harou.schematiccannon.network.InstantSchematicPacket;
import net.harou.schematiccannon.registry.ModItems;
import net.harou.schematiccannon.schematic.SchematicExport;
import net.harou.schematiccannon.schematic.SchematicExport.SchematicExportResult;
import net.harou.schematiccannon.util.Lang;
import net.harou.schematiccannon.util.RaycastHelper;
import net.harou.schematiccannon.util.SchematicFiles;
import net.harou.schematiccannon.client.screen.SchematicPromptScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

/**
 * Selecting the area to save with the Schematic and Pen.
 */
public class SchematicAndPenHandler {

	private final Object outlineSlot = new Object();

	public BlockPos firstPos;
	public BlockPos secondPos;
	private BlockPos selectedPos;
	private Direction selectedFace;
	private int range = 10;

	/**
	 * Whether the mouse wheel is used for the selection right now, so vanilla must not get it.
	 */
	public boolean wantsScroll() {
		return isActive() && ModKeys.sprintKeyDown();
	}

	public boolean mouseScrolled(int delta) {
		if (!wantsScroll())
			return false;
		if (secondPos == null)
			range = Mth.clamp(range + delta, 1, 100);
		if (selectedFace == null || firstPos == null || secondPos == null)
			return true;

		AABB bb = new AABB(Vec3.atLowerCornerOf(firstPos), Vec3.atLowerCornerOf(secondPos));
		Vec3i vec = selectedFace.getUnitVec3i();
		Vec3 projectedView = Minecraft.getInstance().gameRenderer.mainCamera()
			.position();
		if (bb.contains(projectedView))
			delta *= -1;

		int x = vec.getX() * delta;
		int y = vec.getY() * delta;
		int z = vec.getZ() * delta;

		AxisDirection axisDirection = selectedFace.getAxisDirection();
		if (axisDirection == AxisDirection.NEGATIVE)
			bb = bb.move(-x, -y, -z);

		double maxX = Math.max(bb.maxX - x * axisDirection.getStep(), bb.minX);
		double maxY = Math.max(bb.maxY - y * axisDirection.getStep(), bb.minY);
		double maxZ = Math.max(bb.maxZ - z * axisDirection.getStep(), bb.minZ);
		bb = new AABB(bb.minX, bb.minY, bb.minZ, maxX, maxY, maxZ);

		firstPos = BlockPos.containing(bb.minX, bb.minY, bb.minZ);
		secondPos = BlockPos.containing(bb.maxX, bb.maxY, bb.maxZ);
		LocalPlayer player = Minecraft.getInstance().player;
		Lang.sendStatus(player, Lang.translate("schematicAndPen.dimensions", (int) bb.getXsize() + 1,
			(int) bb.getYsize() + 1, (int) bb.getZsize() + 1));

		return true;
	}

	/**
	 * Drops the selection of the current world, e.g. on disconnect.
	 */
	public void reset() {
		firstPos = null;
		secondPos = null;
		selectedPos = null;
		selectedFace = null;
	}

	public boolean onUse() {
		if (!isActive())
			return false;

		LocalPlayer player = Minecraft.getInstance().player;

		if (player.isShiftKeyDown()) {
			discard();
			return true;
		}

		if (secondPos != null) {
			Minecraft.getInstance()
				.gui.setScreen(new SchematicPromptScreen());
			return true;
		}

		if (selectedPos == null) {
			Lang.sendStatus(player, Lang.translate("schematicAndPen.noTarget", ModKeys.getSprintKeyName()));
			return true;
		}

		if (firstPos != null) {
			secondPos = selectedPos;
			Lang.sendStatus(player, Lang.translate("schematicAndPen.secondPos"));
			return true;
		}

		firstPos = selectedPos;
		Lang.sendStatus(player, Lang.translate("schematicAndPen.firstPos"));
		return true;
	}

	public void discard() {
		LocalPlayer player = Minecraft.getInstance().player;
		firstPos = null;
		secondPos = null;
		Lang.sendStatus(player, Lang.translate("schematicAndPen.abort"));
	}

	public void tick() {
		if (!isActive())
			return;

		LocalPlayer player = Minecraft.getInstance().player;
		if (ModKeys.sprintKeyDown()) {
			Vec3 targetVec = player.getEyePosition()
				.add(player.getLookAngle()
					.scale(range));
			selectedPos = BlockPos.containing(targetVec);

		} else {
			BlockHitResult trace = RaycastHelper.rayTraceRange(player.level(), player, 75);
			if (trace.getType() == Type.BLOCK) {

				BlockPos hit = trace.getBlockPos();
				boolean replaceable = player.level()
					.getBlockState(hit)
					.canBeReplaced(new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, trace)));
				if (trace.getDirection()
					.getAxis()
					.isVertical() && !replaceable)
					hit = hit.relative(trace.getDirection());
				selectedPos = hit;
			} else
				selectedPos = null;
		}

		selectedFace = null;
		if (secondPos != null) {
			AABB bb = new AABB(Vec3.atLowerCornerOf(firstPos), Vec3.atLowerCornerOf(secondPos)).expandTowards(1, 1, 1)
				.inflate(.45f);
			Vec3 projectedView = Minecraft.getInstance().gameRenderer.mainCamera()
				.position();
			boolean inside = bb.contains(projectedView);
			BlockHitResult result =
				RaycastHelper.rayTraceUntil(player, 70, pos -> inside ^ bb.contains(Vec3.atCenterOf(pos)));
			selectedFace = result.getType() == HitResult.Type.MISS ? null
				: inside ? result.getDirection()
					.getOpposite() : result.getDirection();
		}

		AABB currentSelectionBox = getCurrentSelectionBox();
		if (currentSelectionBox != null)
			Outliner.getInstance()
				.chaseAABB(outlineSlot, currentSelectionBox)
				.colored(0x6886c5)
				.withFaceTexture(ModRenderTypes.CHECKERED)
				.lineWidth(1 / 16f)
				.highlightFace(selectedFace);
	}

	private AABB getCurrentSelectionBox() {
		if (secondPos == null) {
			if (firstPos == null)
				return selectedPos == null ? null : new AABB(selectedPos);
			return selectedPos == null ? new AABB(firstPos)
				: new AABB(Vec3.atLowerCornerOf(firstPos), Vec3.atLowerCornerOf(selectedPos)).expandTowards(1, 1, 1);
		}
		return new AABB(Vec3.atLowerCornerOf(firstPos), Vec3.atLowerCornerOf(secondPos)).expandTowards(1, 1, 1);
	}

	private boolean isActive() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && mc.gui.screen() == null && !mc.player.isSpectator() && mc.player.getMainHandItem()
			.is(ModItems.SCHEMATIC_AND_PEN);
	}

	public void saveSchematic(String string, boolean convertImmediately) {
		SchematicExportResult result = SchematicExport.saveSchematic(SchematicFiles.SCHEMATICS_DIR, string, false,
			Minecraft.getInstance().level, firstPos, secondPos);
		LocalPlayer player = Minecraft.getInstance().player;
		if (result == null) {
			Lang.sendStatus(player, Lang.translate("schematicAndPen.failed")
				.withStyle(ChatFormatting.RED));
			return;
		}
		Path file = result.file();
		Lang.sendStatus(player, Lang.translate("schematicAndPen.saved", file.getFileName()
			.toString()));
		firstPos = null;
		secondPos = null;
		if (!convertImmediately)
			return;
		try {
			if (!ClientSchematicLoader.validateSizeLimitation(Files.size(file)))
				return;
			ClientPlayNetworking.send(new InstantSchematicPacket(result.fileName(), result.origin(), result.bounds()));
		} catch (IOException e) {
			SchematicCannon.LOGGER.error("Error instantly uploading Schematic file: " + file, e);
		}
	}

}
