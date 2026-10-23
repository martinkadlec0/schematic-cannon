package net.harou.schematiccannon.client.tools;

import com.mojang.blaze3d.vertex.PoseStack;

import net.harou.schematiccannon.ModConfig;
import net.harou.schematiccannon.client.SchematicCannonClient;
import net.harou.schematiccannon.client.SchematicHandler;
import net.harou.schematiccannon.client.SchematicTransformation;
import net.harou.schematiccannon.client.outline.AABBOutline;
import net.harou.schematiccannon.client.render.ModBufferSource;
import net.harou.schematiccannon.client.render.ModRenderTypes;
import net.harou.schematiccannon.util.RaycastHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public abstract class SchematicToolBase {

	protected SchematicHandler schematicHandler;

	protected BlockPos selectedPos;
	protected Vec3 chasingSelectedPos;
	protected Vec3 lastChasingSelectedPos;

	protected boolean selectIgnoreBlocks;
	protected int selectionRange;
	protected boolean schematicSelected;
	protected boolean renderSelectedFace;
	protected Direction selectedFace;

	public void init() {
		schematicHandler = SchematicCannonClient.SCHEMATIC_HANDLER;
		selectedPos = null;
		selectedFace = null;
		schematicSelected = false;
		chasingSelectedPos = Vec3.ZERO;
		lastChasingSelectedPos = Vec3.ZERO;
	}

	public void updateSelection() {
		updateTargetPos();

		if (selectedPos == null)
			return;
		lastChasingSelectedPos = chasingSelectedPos;
		Vec3 target = Vec3.atLowerCornerOf(selectedPos);
		if (!ModConfig.get().smoothAnimations) {
			lastChasingSelectedPos = chasingSelectedPos = target;
			return;
		}
		if (target.distanceTo(chasingSelectedPos) < 1 / 512f) {
			chasingSelectedPos = target;
			return;
		}

		chasingSelectedPos = chasingSelectedPos.add(target.subtract(chasingSelectedPos)
			.scale(1 / 2f));
	}

	public void updateTargetPos() {
		LocalPlayer player = Minecraft.getInstance().player;
		float pt = 1;

		// Select Blueprint
		if (schematicHandler.isDeployed()) {
			SchematicTransformation transformation = schematicHandler.getTransformation();
			AABB localBounds = schematicHandler.getBounds();

			Vec3 traceOrigin = player.getEyePosition();
			Vec3 start = transformation.toLocalSpace(traceOrigin, pt);
			Vec3 end = transformation.toLocalSpace(traceOrigin.add(player.getLookAngle()
				.scale(70)), pt);
			BlockHitResult result =
				RaycastHelper.rayTraceUntil(start, end, pos -> localBounds.contains(Vec3.atCenterOf(pos)));

			schematicSelected = result.getType() != Type.MISS;
			selectedFace = schematicSelected ? result.getDirection() : null;
		}

		boolean snap = this.selectedPos == null;

		// Select location at distance
		if (selectIgnoreBlocks) {
			selectedPos = BlockPos.containing(player.getEyePosition()
				.add(player.getLookAngle()
					.scale(selectionRange)));
			if (snap)
				lastChasingSelectedPos = chasingSelectedPos = Vec3.atLowerCornerOf(selectedPos);
			return;
		}

		// Select targeted Block
		selectedPos = null;
		BlockHitResult trace = RaycastHelper.rayTraceRange(player.level(), player, 75);
		if (trace.getType() != Type.BLOCK)
			return;

		BlockPos hit = BlockPos.containing(trace.getLocation());
		boolean replaceable = player.level()
			.getBlockState(hit)
			.canBeReplaced();
		if (trace.getDirection()
			.getAxis()
			.isVertical() && !replaceable)
			hit = hit.relative(trace.getDirection());
		selectedPos = hit;
		if (snap)
			lastChasingSelectedPos = chasingSelectedPos = Vec3.atLowerCornerOf(selectedPos);
	}

	public boolean handleRightClick() {
		return false;
	}

	public boolean handleMouseWheel(int delta) {
		return false;
	}

	/**
	 * Whether sprint key + scroll is used by this tool, otherwise the wheel stays with vanilla.
	 */
	public boolean usesMouseWheel() {
		return true;
	}

	public void renderTool(PoseStack ms, ModBufferSource buffer, Vec3 camera, float pt) {
	}

	public void renderOnSchematic(PoseStack ms, ModBufferSource buffer, float pt) {
		if (!schematicHandler.isDeployed())
			return;

		ms.pushPose();
		AABBOutline outline = schematicHandler.getOutline();
		outline.getParams()
			.highlightFace(renderSelectedFace ? selectedFace : null)
			.colored(0x6886c5)
			.withFaceTexture(ModRenderTypes.CHECKERED)
			.lineWidth(1 / 16f);
		outline.render(ms, buffer, Vec3.ZERO, pt);
		outline.getParams()
			.withFaceTexture(null);
		ms.popPose();
	}

}
