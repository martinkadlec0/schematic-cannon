package net.harou.schematiccannon.client.outline;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import net.harou.schematiccannon.client.outline.Outline.OutlineParams;
import net.harou.schematiccannon.client.render.ModBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps short-lived outlines alive while they are refreshed every tick and fades them out afterwards.
 */
public class Outliner {

	private static final Outliner INSTANCE = new Outliner();

	private final Map<Object, OutlineEntry> outlines = new HashMap<>();

	public static Outliner getInstance() {
		return INSTANCE;
	}

	public OutlineParams chaseAABB(Object slot, AABB bb) {
		OutlineEntry entry = outlines.get(slot);
		if (entry == null || !(entry.outline instanceof ChasingAABBOutline)) {
			entry = new OutlineEntry(new ChasingAABBOutline(bb));
			outlines.put(slot, entry);
		}
		entry.ticksTillRemoval = 1;
		ChasingAABBOutline outline = (ChasingAABBOutline) entry.outline;
		outline.target(bb);
		return outline.getParams();
	}

	public void clear() {
		outlines.clear();
	}

	public void tickOutlines() {
		Iterator<OutlineEntry> iterator = outlines.values()
			.iterator();
		while (iterator.hasNext()) {
			OutlineEntry entry = iterator.next();
			entry.tick();
			if (!entry.isAlive())
				iterator.remove();
		}
	}

	public void renderOutlines(PoseStack ms, ModBufferSource buffer, Vec3 camera, float pt) {
		outlines.forEach((key, entry) -> {
			Outline outline = entry.outline;
			OutlineParams params = outline.getParams();
			params.alpha = 1;
			if (entry.isFading()) {
				int prevTicks = entry.ticksTillRemoval + 1;
				float fadeticks = OutlineEntry.FADE_TICKS;
				float lastAlpha = prevTicks >= 0 ? 1 : 1 + (prevTicks / fadeticks);
				float currentAlpha = 1 + (entry.ticksTillRemoval / fadeticks);
				float alpha = Mth.lerp(pt, lastAlpha, currentAlpha);

				params.alpha = alpha * alpha * alpha;
				if (params.alpha < 1 / 8f)
					return;
			}
			outline.render(ms, buffer, camera, pt);
		});
	}

	private static class OutlineEntry {
		static final int FADE_TICKS = 8;

		final Outline outline;
		int ticksTillRemoval = 1;

		OutlineEntry(Outline outline) {
			this.outline = outline;
		}

		boolean isAlive() {
			return ticksTillRemoval >= -FADE_TICKS;
		}

		boolean isFading() {
			return ticksTillRemoval < 0;
		}

		void tick() {
			ticksTillRemoval--;
			outline.tick();
		}
	}

}
