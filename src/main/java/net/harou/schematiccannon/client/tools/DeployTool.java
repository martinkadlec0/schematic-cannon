package net.harou.schematiccannon.client.tools;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.harou.schematiccannon.client.ModKeys;
import net.harou.schematiccannon.client.SchematicTransformation;
import net.harou.schematiccannon.client.outline.AABBOutline;
import net.harou.schematiccannon.client.render.ModBufferSource;
import net.harou.schematiccannon.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DeployTool extends SchematicToolBase {

	@Override
	public void init() {
		super.init();
		selectionRange = -1;
	}

	@Override
	public void updateSelection() {
		if (schematicHandler.isActive() && selectionRange == -1) {
			selectionRange = (int) (schematicHandler.getBounds()
				.getCenter()
				.length() / 2);
			selectionRange = Mth.clamp(selectionRange, 1, 100);
		}
		selectIgnoreBlocks = ModKeys.sprintKeyDown();
		super.updateSelection();
	}

	@Override
	public void renderTool(PoseStack ms, ModBufferSource buffer, Vec3 camera, float pt) {
		super.renderTool(ms, buffer, camera, pt);

		if (selectedPos == null)
			return;

		ms.pushPose();
		double x = Mth.lerp(pt, lastChasingSelectedPos.x, chasingSelectedPos.x);
		double y = Mth.lerp(pt, lastChasingSelectedPos.y, chasingSelectedPos.y);
		double z = Mth.lerp(pt, lastChasingSelectedPos.z, chasingSelectedPos.z);

		SchematicTransformation transformation = schematicHandler.getTransformation();
		AABB bounds = schematicHandler.getBounds();
		Vec3 center = bounds.getCenter();
		Vec3 rotationOffset = transformation.getRotationOffset(true);
		int centerX = (int) center.x;
		int centerZ = (int) center.z;
		double xOrigin = bounds.getXsize() / 2f;
		double zOrigin = bounds.getZsize() / 2f;

		ms.translate(x - centerX - camera.x, y - camera.y, z - centerZ - camera.z);
		ms.translate(xOrigin + rotationOffset.x, 0, zOrigin + rotationOffset.z);
		boolean bothMirrored = transformation.getScaleFB()
			.getValue(pt) < 0 && transformation.getScaleLR()
				.getValue(pt) < 0;
		ms.rotateDegrees(Axis.YP, transformation.getCurrentRotation(pt) + (bothMirrored ? 180 : 0));
		ms.translate(-xOrigin - rotationOffset.x, 0, -zOrigin - rotationOffset.z);

		AABBOutline outline = schematicHandler.getOutline();
		outline.getParams()
			.colored(schematicHandler.getPreviewOutlineColor());
		outline.render(ms, buffer, Vec3.ZERO, pt);
		outline.getParams()
			.withFaceTexture(null);
		ms.popPose();
	}

	@Override
	public boolean handleMouseWheel(int delta) {
		if (!selectIgnoreBlocks)
			return super.handleMouseWheel(delta);
		selectionRange += delta;
		selectionRange = Mth.clamp(selectionRange, 1, 100);
		return true;
	}

	/**
	 * Where a right click would deploy the schematic, see {@link SchematicTransformation#startAt}, or null if nothing is
	 * targeted.
	 */
	@Nullable
	public BlockPos getDeployTarget() {
		if (selectedPos == null)
			return null;
		Vec3 center = schematicHandler.getBounds()
			.getCenter();
		return selectedPos.offset(-((int) center.x), 0, -((int) center.z));
	}

	@Override
	public boolean handleRightClick() {
		BlockPos target = getDeployTarget();
		if (target == null)
			return super.handleRightClick();

		ItemStack item = schematicHandler.getActiveSchematicItem();
		if (item != null) {
			schematicHandler.getTransformation()
				.startAt(target);
			item.set(ModDataComponents.SCHEMATIC_DEPLOYED, true);
			item.set(ModDataComponents.SCHEMATIC_ANCHOR, schematicHandler.getTransformation()
				.getAnchor());
		}
		schematicHandler.markDirty();
		schematicHandler.deploy();
		return true;
	}

}
