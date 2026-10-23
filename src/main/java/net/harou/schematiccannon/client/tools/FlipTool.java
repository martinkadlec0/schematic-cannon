package net.harou.schematiccannon.client.tools;

import com.mojang.blaze3d.vertex.PoseStack;

import net.harou.schematiccannon.client.outline.AABBOutline;
import net.harou.schematiccannon.client.render.ModBufferSource;
import net.harou.schematiccannon.client.render.ModRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class FlipTool extends SchematicToolBase {

	private final AABBOutline outline = new AABBOutline(new AABB(BlockPos.ZERO));

	@Override
	public void init() {
		super.init();
		renderSelectedFace = false;
	}

	@Override
	public boolean handleRightClick() {
		mirror();
		return true;
	}

	@Override
	public boolean handleMouseWheel(int delta) {
		mirror();
		return true;
	}

	private void mirror() {
		if (schematicSelected && selectedFace.getAxis()
			.isHorizontal()) {
			schematicHandler.getTransformation()
				.flip(selectedFace.getAxis());
			schematicHandler.markDirty();
		}
	}

	@Override
	public void renderOnSchematic(PoseStack ms, ModBufferSource buffer, float pt) {
		if (!schematicSelected || !selectedFace.getAxis()
			.isHorizontal()) {
			super.renderOnSchematic(ms, buffer, pt);
			return;
		}

		Direction facing = selectedFace.getClockWise();
		AABB bounds = schematicHandler.getBounds();

		Vec3 directionVec = Vec3.atLowerCornerOf(Direction.get(AxisDirection.POSITIVE, facing.getAxis())
			.getUnitVec3i());
		Vec3 boundsSize = new Vec3(bounds.getXsize(), bounds.getYsize(), bounds.getZsize());
		Vec3 vec = boundsSize.multiply(directionVec);
		bounds = bounds.contract(vec.x, vec.y, vec.z)
			.inflate(1 - directionVec.x, 1 - directionVec.y, 1 - directionVec.z);
		bounds = bounds.move(directionVec.scale(.5f)
			.multiply(boundsSize));

		outline.setBounds(bounds);
		outline.getParams()
			.lineWidth(1 / 16f)
			.disableLineNormals()
			.colored(0xdddddd)
			.withFaceTexture(ModRenderTypes.CHECKERED);
		outline.render(ms, buffer, Vec3.ZERO, pt);

		super.renderOnSchematic(ms, buffer, pt);
	}

}
