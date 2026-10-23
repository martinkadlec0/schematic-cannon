package net.harou.schematiccannon.client.render;

import java.util.Arrays;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;

/**
 * Records vertices (position, color, uv, overlay, light, normal) so they can be replayed into any vertex consumer,
 * e.g. from a {@link net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer}.
 */
public class VertexRecorder implements VertexConsumer {
	// x, y, z, u, v, normal x, y, z
	private static final int FLOATS = 8;
	// color, overlay, light
	private static final int INTS = 3;

	private float[] floats = new float[FLOATS * 64];
	private int[] ints = new int[INTS * 64];
	private final Vector3f transformed = new Vector3f();
	private int vertexCount;
	private int f;
	private int i;

	@Override
	public VertexConsumer addVertex(float x, float y, float z) {
		f = vertexCount * FLOATS;
		i = vertexCount * INTS;
		vertexCount++;
		if (floats.length < f + FLOATS) {
			floats = Arrays.copyOf(floats, floats.length * 2);
			ints = Arrays.copyOf(ints, ints.length * 2);
		}
		floats[f] = x;
		floats[f + 1] = y;
		floats[f + 2] = z;
		floats[f + 3] = 0;
		floats[f + 4] = 0;
		floats[f + 5] = 0;
		floats[f + 6] = 1;
		floats[f + 7] = 0;
		ints[i] = -1;
		ints[i + 1] = OverlayTexture.NO_OVERLAY;
		ints[i + 2] = LightCoordsUtil.FULL_BRIGHT;
		return this;
	}

	@Override
	public VertexConsumer setColor(int r, int g, int b, int a) {
		ints[i] = a << 24 | r << 16 | g << 8 | b;
		return this;
	}

	@Override
	public VertexConsumer setColor(int argb) {
		ints[i] = argb;
		return this;
	}

	@Override
	public VertexConsumer setUv(float u, float v) {
		floats[f + 3] = u;
		floats[f + 4] = v;
		return this;
	}

	@Override
	public VertexConsumer setUv1(int u, int v) {
		ints[i + 1] = u & 0xFFFF | v << 16;
		return this;
	}

	@Override
	public VertexConsumer setUv2(int u, int v) {
		ints[i + 2] = u & 0xFFFF | v << 16;
		return this;
	}

	@Override
	public VertexConsumer setUv3(float u, float v) {
		return this;
	}

	@Override
	public VertexConsumer setLineWidth(float width) {
		return this;
	}

	@Override
	public VertexConsumer setNormal(float x, float y, float z) {
		floats[f + 5] = x;
		floats[f + 6] = y;
		floats[f + 7] = z;
		return this;
	}

	public boolean isEmpty() {
		return vertexCount == 0;
	}

	public void clear() {
		vertexCount = 0;
	}

	/**
	 * Replays all vertices, transformed by the pose. Called on the render thread only.
	 */
	public void renderInto(PoseStack.Pose pose, VertexConsumer consumer) {
		for (int v = 0; v < vertexCount; v++) {
			int fb = v * FLOATS;
			int ib = v * INTS;
			pose.pose()
				.transformPosition(floats[fb], floats[fb + 1], floats[fb + 2], transformed);
			consumer.addVertex(transformed.x, transformed.y, transformed.z)
				.setColor(ints[ib])
				.setUv(floats[fb + 3], floats[fb + 4])
				.setOverlay(ints[ib + 1])
				.setLight(ints[ib + 2]);
			pose.transformNormal(floats[fb + 5], floats[fb + 6], floats[fb + 7], transformed);
			consumer.setNormal(transformed.x, transformed.y, transformed.z);
		}
	}
}
