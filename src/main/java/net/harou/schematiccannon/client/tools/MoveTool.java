package net.harou.schematiccannon.client.tools;

import net.harou.schematiccannon.client.SchematicTransformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
		// Scrolling up moves the schematic away from the player. The selected face points back at the player from
		// outside, but along the view from inside the schematic, so the direction comes from the view instead.
		LocalPlayer player = Minecraft.getInstance().player;
		Vec3 eye = player.getEyePosition();
		Vec3 localLook = transformation.toLocalSpace(eye.add(player.getLookAngle()), 1)
			.subtract(transformation.toLocalSpace(eye, 1));
		Vec3 normal = Vec3.atLowerCornerOf(selectedFace.getUnitVec3i());
		Vec3 vec = normal.scale(normal.dot(localLook) > 0 ? delta : -delta);
		vec = vec.multiply(transformation.getMirrorModifier(Axis.X), 1, transformation.getMirrorModifier(Axis.Z));
		vec = SchematicTransformation.rotateY(vec, transformation.getRotationTarget());
		transformation.move((int) Math.round(vec.x), 0, (int) Math.round(vec.z));
		schematicHandler.markDirty();

		return true;
	}

}
