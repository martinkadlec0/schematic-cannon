package net.harou.schematiccannon.client.render;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Collects schematic overlay geometry for one frame and submits it to the level renderer. Vertices are recorded
 * already transformed, so they are submitted with an identity pose. Late buffers are submitted in a later order, so
 * translucent outline faces are drawn after other translucent geometry such as the schematic preview.
 * <p>
 * The level renderer replays the submitted geometry within the same frame, so the recorders can be reused on the next
 * one.
 */
public class ModBufferSource {

	private static final ModBufferSource INSTANCE = new ModBufferSource();
	private static final PoseStack IDENTITY = new PoseStack();

	private final Map<RenderType, VertexRecorder> regular = new LinkedHashMap<>();
	private final Map<RenderType, VertexRecorder> late = new LinkedHashMap<>();

	public static ModBufferSource getInstance() {
		return INSTANCE;
	}

	public VertexConsumer getBuffer(RenderType type) {
		return regular.computeIfAbsent(type, $ -> new VertexRecorder());
	}

	public VertexConsumer getLateBuffer(RenderType type) {
		return late.computeIfAbsent(type, $ -> new VertexRecorder());
	}

	/**
	 * Clears what was recorded for the previous frame.
	 */
	public void begin() {
		regular.values()
			.forEach(VertexRecorder::clear);
		late.values()
			.forEach(VertexRecorder::clear);
	}

	public void submit(SubmitNodeCollector collector) {
		regular.forEach((type, recorder) -> {
			if (!recorder.isEmpty())
				collector.submitCustomGeometry(IDENTITY, type, recorder::renderInto);
		});
		late.forEach((type, recorder) -> {
			if (!recorder.isEmpty())
				collector.order(1)
					.submitCustomGeometry(IDENTITY, type, recorder::renderInto);
		});
	}

}
