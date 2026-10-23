package net.harou.schematiccannon.util;

import net.minecraft.util.Mth;

public class LerpedFloat {

	protected Interpolator interpolator;
	protected float previousValue;
	protected float value;

	protected Chaser chaseFunction;
	protected float chaseTarget;
	protected float chaseSpeed;
	protected boolean angularChase;

	public LerpedFloat(Interpolator interpolator) {
		this.interpolator = interpolator;
		startWithValue(0);
	}

	public static LerpedFloat linear() {
		return new LerpedFloat((p, c, t) -> (float) Mth.lerp(p, c, t));
	}

	public static LerpedFloat angular() {
		LerpedFloat lerpedFloat = new LerpedFloat((p, c, t) -> (float) (c + getShortestAngleDiff(c, t) * p));
		lerpedFloat.angularChase = true;
		return lerpedFloat;
	}

	public static float getShortestAngleDiff(double current, double target) {
		current = current % 360;
		target = target % 360;
		return (float) (((((target - current) % 360) + 540) % 360) - 180);
	}

	public LerpedFloat startWithValue(double value) {
		float f = (float) value;
		this.previousValue = f;
		this.chaseTarget = f;
		this.value = f;
		return this;
	}

	public LerpedFloat chase(double value, double speed, Chaser chaseFunction) {
		updateChaseTarget((float) value);
		this.chaseSpeed = (float) speed;
		this.chaseFunction = chaseFunction;
		return this;
	}

	/**
	 * Jumps to the chase target without animating.
	 */
	public void snapToTarget() {
		previousValue = value = chaseTarget;
	}

	public void updateChaseTarget(float target) {
		if (angularChase)
			target = value + getShortestAngleDiff(value, target);
		this.chaseTarget = target;
	}

	public void tickChaser() {
		previousValue = value;
		if (chaseFunction == null)
			return;
		if (Mth.equal((double) value, chaseTarget)) {
			value = chaseTarget;
			return;
		}
		value = chaseFunction.chase(value, chaseSpeed, chaseTarget);
	}

	public float getValue(float partialTicks) {
		return interpolator.interpolate(partialTicks, previousValue, value);
	}

	public float getChaseTarget() {
		return chaseTarget;
	}

	@FunctionalInterface
	public interface Interpolator {
		float interpolate(double progress, double current, double target);
	}

	@FunctionalInterface
	public interface Chaser {

		Chaser EXP = (c, s, t) -> (float) (c + (t - c) * s);

		float chase(double current, double speed, double target);
	}

}
