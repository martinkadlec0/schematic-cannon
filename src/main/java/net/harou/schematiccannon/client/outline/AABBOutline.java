package net.harou.schematiccannon.client.outline;

import org.joml.Matrix4f;
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

		box = box.move(camera.scale(-1));
		PoseStack.Pose pose = ms.last();
		// Where the camera is in the box's coordinates: the origin for a box in the world (made camera-relative above),
		// elsewhere for a box drawn in another space, e.g. the schematic's own while it is moved and turned
		Vector3f eye = new Matrix4f(pose.pose()).invert()
			.transformPosition(new Vector3f());
		boolean cameraInside = box.contains(eye.x, eye.y, eye.z);
		boolean cull = !cameraInside;
		float inflate = cameraInside ? -1 / 128f : 1 / 128f;

		Vector3f minPos = new Vector3f((float) box.minX - inflate, (float) box.minY - inflate, (float) box.minZ - inflate);
		Vector3f maxPos = new Vector3f((float) box.maxX + inflate, (float) box.maxY + inflate, (float) box.maxZ + inflate);

		if (params.faceTexture != null) {
			// Translucent without writing depth, so water and other translucent blocks behind the faces still render.
			// It does not cull, so faces turned away from the camera are skipped here while it is outside the box.
			VertexConsumer faces = buffer.getLateBuffer(RenderTypes.entityTranslucentEmissive(params.faceTexture));
			for (Direction face : Direction.values())
				if ((!params.sidesOnly || face.getAxis()
					.isHorizontal()) && (!cull || facesCamera(face, minPos, maxPos, eye)))
					renderBoxFace(pose, faces, minPos, maxPos, face, new Vector4f(colorTemp.x(), colorTemp.y(),
						colorTemp.z(), colorTemp.w() * (face == params.highlightedFace ? 1 : .5f)));
		}

		if (params.fillAlpha > 0) {
			// Debug quads blend without writing depth, so they do not hide water and other translucent blocks
			VertexConsumer fill = buffer.getLateBuffer(RenderTypes.debugQuads());
			Vector4f fillColor = new Vector4f(colorTemp.x(), colorTemp.y(), colorTemp.z(), colorTemp.w() * params.fillAlpha);
			for (Direction face : Direction.values())
				if (!params.sidesOnly || face.getAxis()
					.isHorizontal())
					renderBoxFace(pose, fill, minPos, maxPos, face, fillColor);
		}

		float lineWidth = params.getLineWidth();
		if (lineWidth == 0)
			return;
		VertexConsumer lines = buffer.getBuffer(RenderTypes.entitySolid(ModRenderTypes.BLANK));
		if (params.sidesOnly)
			renderVerticalEdges(pose, lines, minPos, maxPos, lineWidth, colorTemp, params.disableLineNormals);
		else
			renderBoxEdges(pose, lines, minPos, maxPos, lineWidth, colorTemp, params.disableLineNormals);
	}

	/**
	 * Whether the camera is on the outer side of the face.
	 */
	private static boolean facesCamera(Direction face, Vector3f min, Vector3f max, Vector3f eye) {
		return switch (face) {
			case DOWN -> min.y > eye.y;
			case UP -> max.y < eye.y;
			case NORTH -> min.z > eye.z;
			case SOUTH -> max.z < eye.z;
			case WEST -> min.x > eye.x;
			case EAST -> max.x < eye.x;
		};
	}

	protected void renderBoxFace(PoseStack.Pose pose, VertexConsumer consumer, Vector3f min, Vector3f max,
		Direction face, Vector4f faceColor) {
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

	protected void renderVerticalEdges(PoseStack.Pose pose, VertexConsumer consumer, Vector3f min, Vector3f max,
		float lineWidth, Vector4f color, boolean disableNormals) {
		float lengthY = max.y - min.y;
		for (Vector3f origin : new Vector3f[] { new Vector3f(min.x, min.y, min.z), new Vector3f(max.x, min.y, min.z),
			new Vector3f(min.x, min.y, max.z), new Vector3f(max.x, min.y, max.z) })
			bufferCuboidLine(pose, consumer, origin, Direction.UP, lengthY, lineWidth, color, disableNormals);
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
