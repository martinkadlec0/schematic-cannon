package net.harou.schematiccannon.client.tools;

import net.harou.schematiccannon.client.SchematicTransformation;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.Vec3;

public class MoveTool extends SchematicToolBase {

	@Override
	public void init() {
		super.init();
		renderSelectedFace = true;
	}

	@Override
	public boolean handleMouseWheel(int delta) {
		if (!schematicSelected || !selectedFace.getAxis()
			.isHorizontal())
			return true;

		SchematicTransformation transformation = schematicHandler.getTransformation();
		Vec3 vec = Vec3.atLowerCornerOf(selectedFace.getUnitVec3i())
			.scale(-delta);
		vec = vec.multiply(transformation.getMirrorModifier(Axis.X), 1, transformation.getMirrorModifier(Axis.Z));
		vec = SchematicTransformation.rotateY(vec, transformation.getRotationTarget());
		transformation.move((int) Math.round(vec.x), 0, (int) Math.round(vec.z));
		schematicHandler.markDirty();

		return true;
	}

}
