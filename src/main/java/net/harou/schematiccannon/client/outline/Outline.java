package net.harou.schematiccannon.client.outline;

import org.jetbrains.annotations.Nullable;

import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.harou.schematiccannon.client.render.ModBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

public abstract class Outline {

	protected final OutlineParams params = new OutlineParams();
	protected final Vector4f colorTemp = new Vector4f();

	public OutlineParams getParams() {
		return params;
	}

	public abstract void render(PoseStack ms, ModBufferSource buffer, Vec3 camera, float pt);

	public void tick() {
	}

	/**
	 * Buffers a square beam of the given width, starting at the origin and extending along the direction.
	 */
	public static void bufferCuboidLine(PoseStack.Pose pose, VertexConsumer consumer, Vector3f origin,
		Direction direction, float length, float width, Vector4f color, boolean disableNormals) {
		float halfWidth = width / 2;
		Vector3f minPos = new Vector3f(origin).sub(halfWidth, halfWidth, halfWidth);
		Vector3f maxPos = new Vector3f(origin).add(halfWidth, halfWidth, halfWidth);
		Vector3f extent = new Vector3f(direction.getUnitVec3f()).mul(length);
		if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE)
			maxPos.add(extent);
		else
			minPos.add(extent);
		bufferCuboid(pose, consumer, minPos, maxPos, color, disableNormals);
	}

	/**
	 * @param disableNormals light all faces like a top face
	 */
	public static void bufferCuboid(PoseStack.Pose pose, VertexConsumer consumer, Vector3f min, Vector3f max,
		Vector4f color, boolean disableNormals) {
		Vector3f p0 = new Vector3f(min.x, min.y, max.z);
		Vector3f p1 = new Vector3f(min.x, min.y, min.z);
		Vector3f p2 = new Vector3f(max.x, min.y, min.z);
		Vector3f p3 = new Vector3f(max.x, min.y, max.z);
		Vector3f p4 = new Vector3f(min.x, max.y, min.z);
		Vector3f p5 = new Vector3f(min.x, max.y, max.z);
		Vector3f p6 = new Vector3f(max.x, max.y, max.z);
		Vector3f p7 = new Vector3f(max.x, max.y, min.z);
		Vector3f up = new Vector3f(0, 1, 0);

		bufferQuad(pose, consumer, p0, p1, p2, p3, color, 0, 0, 1, 1, disableNormals ? up : new Vector3f(0, -1, 0));
		bufferQuad(pose, consumer, p4, p5, p6, p7, color, 0, 0, 1, 1, up);
		bufferQuad(pose, consumer, p7, p2, p1, p4, color, 0, 0, 1, 1, disableNormals ? up : new Vector3f(0, 0, -1));
		bufferQuad(pose, consumer, p5, p0, p3, p6, color, 0, 0, 1, 1, disableNormals ? up : new Vector3f(0, 0, 1));
		bufferQuad(pose, consumer, p4, p1, p0, p5, color, 0, 0, 1, 1, disableNormals ? up : new Vector3f(-1, 0, 0));
		bufferQuad(pose, consumer, p6, p3, p2, p7, color, 0, 0, 1, 1, disableNormals ? up : new Vector3f(1, 0, 0));
	}

	public static void bufferQuad(PoseStack.Pose pose, VertexConsumer consumer, Vector3f pos0, Vector3f pos1,
		Vector3f pos2, Vector3f pos3, Vector4f color, float minU, float minV, float maxU, float maxV, Vector3f normal) {
		bufferVertex(pose, consumer, pos0, color, minU, minV, normal);
		bufferVertex(pose, consumer, pos1, color, minU, maxV, normal);
		bufferVertex(pose, consumer, pos2, color, maxU, maxV, normal);
		bufferVertex(pose, consumer, pos3, color, maxU, minV, normal);
	}

	private static void bufferVertex(PoseStack.Pose pose, VertexConsumer consumer, Vector3f pos, Vector4f color,
		float u, float v, Vector3f normal) {
		consumer.addVertex(pose, pos)
			.setColor(color.x(), color.y(), color.z(), color.w())
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(LightCoordsUtil.FULL_BRIGHT)
			.setNormal(pose, normal);
	}

	public static class OutlineParams {
		@Nullable
		protected Identifier faceTexture;
		@Nullable
		Direction highlightedFace;
		protected boolean disableLineNormals;
		/** Opacity of plain colored faces, 0 for none */
		protected float fillAlpha;
		/** Only the sides: no horizontal edges and no top or bottom faces, e.g. for columns spanning the world */
		protected boolean sidesOnly;
		protected float alpha = 1;
		protected int rgb = 0xFFFFFF;
		private float lineWidth = 1 / 32f;

		// builder

		public OutlineParams colored(int color) {
			rgb = color;
			return this;
		}

		public OutlineParams lineWidth(float width) {
			this.lineWidth = width;
			return this;
		}

		public OutlineParams withFaceTexture(@Nullable Identifier texture) {
			this.faceTexture = texture;
			return this;
		}

		/**
		 * Fills the faces with the outline color at the given opacity. Unlike textured faces, the fill does not write
		 * depth, so translucent blocks such as water behind or inside it still render.
		 */
		public OutlineParams filled(float alpha) {
			this.fillAlpha = alpha;
			return this;
		}

		/**
		 * Draws only the vertical edges and the side faces, so tall boxes such as whole chunk columns fade out at the
		 * bottom and top of the world instead of ending in a frame.
		 */
		public OutlineParams sidesOnly(boolean sidesOnly) {
			this.sidesOnly = sidesOnly;
			return this;
		}

		public OutlineParams highlightFace(@Nullable Direction face) {
			highlightedFace = face;
			return this;
		}

		public OutlineParams disableLineNormals() {
			disableLineNormals = true;
			return this;
		}

		// getter

		/**
		 * Lines get thinner while the outline fades out.
		 */
		public float getLineWidth() {
			return alpha * lineWidth;
		}

		public void loadColor(Vector4f vec) {
			vec.set((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha);
		}
	}

}
