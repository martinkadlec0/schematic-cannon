package net.harou.schematiccannon.client.tools;

import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.harou.schematiccannon.client.outline.Outline;
import net.harou.schematiccannon.client.render.ModBufferSource;
import net.harou.schematiccannon.client.render.ModRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class RotateTool extends SchematicToolBase {

	@Override
	public boolean handleMouseWheel(int delta) {
		schematicHandler.getTransformation()
			.rotate90(delta > 0);
		schematicHandler.markDirty();
		return true;
	}

	@Override
	public void renderOnSchematic(PoseStack ms, ModBufferSource buffer, float pt) {
		AABB bounds = schematicHandler.getBounds();
		double height = bounds.getYsize() + Math.max(20, bounds.getYsize());
		Vec3 center = bounds.getCenter()
			.add(schematicHandler.getTransformation()
				.getRotationOffset(true));
		Vec3 bottom = center.subtract(0, height / 2, 0);

		// Rotation axis
		Outline.bufferCuboidLine(ms.last(), buffer.getBuffer(RenderTypes.entitySolid(ModRenderTypes.BLANK)),
			bottom.toVector3f(), Direction.UP, (float) height, 1 / 16f, new Vector4f(.867f, .867f, .867f, 1), true);

		super.renderOnSchematic(ms, buffer, pt);
	}

}
