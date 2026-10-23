package net.harou.schematiccannon.client.outline;

import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.harou.schematiccannon.client.render.ModBufferSource;
import net.harou.schematiccannon.client.render.ModRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class AABBOutline extends Outline {

	protected AABB bb;

	public AABBOutline(AABB bb) {
		setBounds(bb);
	}

	public void setBounds(AABB bb) {
		this.bb = bb;
	}

	protected AABB getRenderBounds(float pt) {
		return bb;
	}

	@Override
	public void render(PoseStack ms, ModBufferSource buffer, Vec3 camera, float pt) {
		AABB box = getRenderBounds(pt);
		params.loadColor(colorTemp);

		boolean cameraInside = box.contains(camera);
		boolean cull = !cameraInside;
		float inflate = cameraInside ? -1 / 128f : 1 / 128f;

		box = box.move(camera.scale(-1));
		Vector3f minPos = new Vector3f((float) box.minX - inflate, (float) box.minY - inflate, (float) box.minZ - inflate);
		Vector3f maxPos = new Vector3f((float) box.maxX + inflate, (float) box.maxY + inflate, (float) box.maxZ + inflate);
		PoseStack.Pose pose = ms.last();

		if (params.faceTexture != null) {
			VertexConsumer faces = buffer.getLateBuffer(cull ? RenderTypes.entityTranslucentCull(params.faceTexture)
				: RenderTypes.entityTranslucent(params.faceTexture));
			for (Direction face : Direction.values())
				renderBoxFace(pose, faces, minPos, maxPos, face, colorTemp, face == params.highlightedFace);
		}

		float lineWidth = params.getLineWidth();
		if (lineWidth == 0)
			return;
		VertexConsumer lines = buffer.getBuffer(RenderTypes.entitySolid(ModRenderTypes.BLANK));
		renderBoxEdges(pose, lines, minPos, maxPos, lineWidth, colorTemp, params.disableLineNormals);
	}

	protected void renderBoxFace(PoseStack.Pose pose, VertexConsumer consumer, Vector3f min, Vector3f max,
		Direction face, Vector4f color, boolean highlighted) {
		Vector4f faceColor = new Vector4f(color.x(), color.y(), color.z(), color.w() * (highlighted ? 1 : .5f));
		float sizeX = max.x - min.x;
		float sizeY = max.y - min.y;
		float sizeZ = max.z - min.z;
		Vector3f normal = new Vector3f(face.getUnitVec3f());

		// The checkerboard repeats once per block
		switch (face) {
		case DOWN -> bufferQuad(pose, consumer, new Vector3f(min.x, min.y, max.z), new Vector3f(min.x, min.y, min.z),
			new Vector3f(max.x, min.y, min.z), new Vector3f(max.x, min.y, max.z), faceColor, 0, 0, sizeX, sizeZ, normal);
		case UP -> bufferQuad(pose, consumer, new Vector3f(min.x, max.y, min.z), new Vector3f(min.x, max.y, max.z),
			new Vector3f(max.x, max.y, max.z), new Vector3f(max.x, max.y, min.z), faceColor, 0, 0, sizeX, sizeZ, normal);
		case NORTH -> bufferQuad(pose, consumer, new Vector3f(max.x, max.y, min.z), new Vector3f(max.x, min.y, min.z),
			new Vector3f(min.x, min.y, min.z), new Vector3f(min.x, max.y, min.z), faceColor, 0, 0, sizeX, sizeY, normal);
		case SOUTH -> bufferQuad(pose, consumer, new Vector3f(min.x, max.y, max.z), new Vector3f(min.x, min.y, max.z),
			new Vector3f(max.x, min.y, max.z), new Vector3f(max.x, max.y, max.z), faceColor, 0, 0, sizeX, sizeY, normal);
		case WEST -> bufferQuad(pose, consumer, new Vector3f(min.x, max.y, min.z), new Vector3f(min.x, min.y, min.z),
			new Vector3f(min.x, min.y, max.z), new Vector3f(min.x, max.y, max.z), faceColor, 0, 0, sizeZ, sizeY, normal);
		case EAST -> bufferQuad(pose, consumer, new Vector3f(max.x, max.y, max.z), new Vector3f(max.x, min.y, max.z),
			new Vector3f(max.x, min.y, min.z), new Vector3f(max.x, max.y, min.z), faceColor, 0, 0, sizeZ, sizeY, normal);
		}
	}

	protected void renderBoxEdges(PoseStack.Pose pose, VertexConsumer consumer, Vector3f min, Vector3f max,
		float lineWidth, Vector4f color, boolean disableNormals) {
		float lengthX = max.x - min.x;
		float lengthY = max.y - min.y;
		float lengthZ = max.z - min.z;

		Vector3f origin = new Vector3f(min);
		bufferCuboidLine(pose, consumer, origin, Direction.EAST, lengthX, lineWidth, color, disableNormals);
		bufferCuboidLine(pose, consumer, origin, Direction.UP, lengthY, lineWidth, color, disableNormals);
		bufferCuboidLine(pose, consumer, origin, Direction.SOUTH, lengthZ, lineWidth, color, disableNormals);

		origin.set(max.x, min.y, min.z);
		bufferCuboidLine(pose, consumer, origin, Direction.UP, lengthY, lineWidth, color, disableNormals);
		bufferCuboidLine(pose, consumer, origin, Direction.SOUTH, lengthZ, lineWidth, color, disableNormals);

		origin.set(min.x, max.y, min.z);
		bufferCuboidLine(pose, consumer, origin, Direction.EAST, lengthX, lineWidth, color, disableNormals);
		bufferCuboidLine(pose, consumer, origin, Direction.SOUTH, lengthZ, lineWidth, color, disableNormals);

		origin.set(min.x, min.y, max.z);
		bufferCuboidLine(pose, consumer, origin, Direction.EAST, lengthX, lineWidth, color, disableNormals);
		bufferCuboidLine(pose, consumer, origin, Direction.UP, lengthY, lineWidth, color, disableNormals);

		origin.set(min.x, max.y, max.z);
		bufferCuboidLine(pose, consumer, origin, Direction.EAST, lengthX, lineWidth, color, disableNormals);

		origin.set(max.x, min.y, max.z);
		bufferCuboidLine(pose, consumer, origin, Direction.UP, lengthY, lineWidth, color, disableNormals);

		origin.set(max.x, max.y, min.z);
		bufferCuboidLine(pose, consumer, origin, Direction.SOUTH, lengthZ, lineWidth, color, disableNormals);
	}

}
