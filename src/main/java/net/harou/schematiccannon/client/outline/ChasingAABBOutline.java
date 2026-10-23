package net.harou.schematiccannon.client.outline;

import net.harou.schematiccannon.ModConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

public class ChasingAABBOutline extends AABBOutline {

	AABB targetBB;
	AABB prevBB;

	public ChasingAABBOutline(AABB bb) {
		super(bb);
		prevBB = bb;
		targetBB = bb;
	}

	public void target(AABB target) {
		targetBB = target;
	}

	@Override
	public void tick() {
		if (!ModConfig.get().smoothAnimations) {
			prevBB = targetBB;
			setBounds(targetBB);
			return;
		}
		prevBB = bb;
		setBounds(interpolateBBs(bb, targetBB, .5f));
	}

	@Override
	protected AABB getRenderBounds(float pt) {
		return interpolateBBs(prevBB, bb, pt);
	}

	private static AABB interpolateBBs(AABB current, AABB target, float pt) {
		return new AABB(Mth.lerp(pt, current.minX, target.minX), Mth.lerp(pt, current.minY, target.minY),
			Mth.lerp(pt, current.minZ, target.minZ), Mth.lerp(pt, current.maxX, target.maxX),
			Mth.lerp(pt, current.maxY, target.maxY), Mth.lerp(pt, current.maxZ, target.maxZ));
	}

}
