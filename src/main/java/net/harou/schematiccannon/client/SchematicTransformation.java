package net.harou.schematiccannon.client;

import static java.lang.Math.abs;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.harou.schematiccannon.ModConfig;
import net.harou.schematiccannon.util.LerpedFloat;
import net.harou.schematiccannon.util.LerpedFloat.Chaser;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Animated position, rotation and mirroring of the schematic preview.
 */
public class SchematicTransformation {

	private Vec3 chasingPos;
	private Vec3 prevChasingPos;
	private BlockPos target;

	private final LerpedFloat scaleFrontBack;
	private final LerpedFloat scaleLeftRight;
	private final LerpedFloat rotation;
	private double xOrigin;
	private double zOrigin;

	public SchematicTransformation() {
		chasingPos = Vec3.ZERO;
		prevChasingPos = Vec3.ZERO;
		target = BlockPos.ZERO;
		scaleFrontBack = LerpedFloat.linear();
		scaleLeftRight = LerpedFloat.linear();
		rotation = LerpedFloat.angular();
	}

	public void init(BlockPos anchor, StructurePlaceSettings settings, AABB bounds) {
		int leftRight = settings.getMirror() == Mirror.LEFT_RIGHT ? -1 : 1;
		int frontBack = settings.getMirror() == Mirror.FRONT_BACK ? -1 : 1;
		getScaleFB().chase(0, 0.45f, Chaser.EXP)
			.startWithValue(frontBack);
		getScaleLR().chase(0, 0.45f, Chaser.EXP)
			.startWithValue(leftRight);
		xOrigin = bounds.getXsize() / 2f;
		zOrigin = bounds.getZsize() / 2f;

		int r = -(settings.getRotation()
			.ordinal() * 90);
		rotation.chase(0, 0.45f, Chaser.EXP)
			.startWithValue(r);

		target = fromAnchor(anchor);
		chasingPos = Vec3.atLowerCornerOf(target);
		prevChasingPos = chasingPos;
	}

	public void applyTransformations(PoseStack ms, Vec3 camera, float pt) {
		// Translation
		Vec3 translation = prevChasingPos.lerp(chasingPos, pt)
			.subtract(camera);
		ms.translate(translation.x, translation.y, translation.z);
		Vec3 rotationOffset = getRotationOffset(true);

		// Rotation & Mirror
		float fb = getScaleFB().getValue(pt);
		float lr = getScaleLR().getValue(pt);
		float rot = rotation.getValue(pt) + ((fb < 0 && lr < 0) ? 180 : 0);
		ms.translate(xOrigin, 0, zOrigin);
		ms.translate(rotationOffset.x, rotationOffset.y, rotationOffset.z);
		ms.rotateDegrees(Axis.YP, rot);
		ms.translate(-rotationOffset.x, -rotationOffset.y, -rotationOffset.z);
		ms.scale(abs(fb), 1, abs(lr));
		ms.translate(-xOrigin, 0, -zOrigin);
	}

	public Vec3 getRotationOffset(boolean ignoreMirrors) {
		Vec3 rotationOffset = Vec3.ZERO;
		if ((int) (zOrigin * 2) % 2 != (int) (xOrigin * 2) % 2) {
			boolean xGreaterZ = xOrigin > zOrigin;
			float xIn = (xGreaterZ ? 0 : .5f);
			float zIn = (!xGreaterZ ? 0 : .5f);
			if (!ignoreMirrors) {
				xIn *= getMirrorModifier(Direction.Axis.X);
				zIn *= getMirrorModifier(Direction.Axis.Z);
			}
			rotationOffset = new Vec3(xIn, 0, zIn);
		}
		return rotationOffset;
	}

	public Vec3 toLocalSpace(Vec3 vec, float pt) {
		Vec3 rotationOffset = getRotationOffset(true);
		// Inverse of applyTransformations: both mirrors are drawn as a 180 degree turn
		float fb = getScaleFB().getValue(pt);
		float lr = getScaleLR().getValue(pt);
		boolean bothMirrored = fb < 0 && lr < 0;
		float rot = rotation.getValue(pt) + (bothMirrored ? 180 : 0);

		vec = vec.subtract(prevChasingPos.lerp(chasingPos, pt));
		vec = vec.subtract(xOrigin + rotationOffset.x, 0, zOrigin + rotationOffset.z);
		vec = vec.yRot(-rot * Mth.DEG_TO_RAD);
		vec = vec.add(rotationOffset.x, 0, rotationOffset.z);
		vec = vec.multiply(bothMirrored ? -fb : fb, 1, bothMirrored ? -lr : lr);
		vec = vec.add(xOrigin, 0, zOrigin);

		return vec;
	}

	public StructurePlaceSettings toSettings() {
		StructurePlaceSettings settings = new StructurePlaceSettings();

		int i = getRotationTarget();

		boolean mirrorlr = getScaleLR().getChaseTarget() < 0;
		boolean mirrorfb = getScaleFB().getChaseTarget() < 0;
		if (mirrorlr && mirrorfb) {
			mirrorlr = mirrorfb = false;
			i += 180;
		}
		i = i % 360;
		if (i < 0)
			i += 360;

		Rotation rotation = switch (i) {
		case 90 -> Rotation.COUNTERCLOCKWISE_90;
		case 180 -> Rotation.CLOCKWISE_180;
		case 270 -> Rotation.CLOCKWISE_90;
		default -> Rotation.NONE;
		};

		settings.setRotation(rotation);
		if (mirrorfb)
			settings.setMirror(Mirror.FRONT_BACK);
		if (mirrorlr)
			settings.setMirror(Mirror.LEFT_RIGHT);

		return settings;
	}

	/**
	 * Rotates by whole quarter turns like Vec3#yRot, but exactly: Mth's sine table is slightly off at 90 and 180
	 * degrees, which would move positions that are later floored to block positions by a whole block.
	 */
	public static Vec3 rotateY(Vec3 vec, int degrees) {
		return switch (Math.floorMod(Math.round(degrees / 90f), 4)) {
		case 1 -> new Vec3(vec.z, vec.y, -vec.x);
		case 2 -> new Vec3(-vec.x, vec.y, -vec.z);
		case 3 -> new Vec3(-vec.z, vec.y, vec.x);
		default -> vec;
		};
	}

	/**
	 * Mirroring both axes is printed as a 180 degree rotation without mirror.
	 */
	private boolean isBothMirrored() {
		return getScaleFB().getChaseTarget() < 0 && getScaleLR().getChaseTarget() < 0;
	}

	public BlockPos getAnchor() {
		Vec3 vec = Vec3.ZERO.add(.5, 0, .5);
		Vec3 rotationOffset = getRotationOffset(isBothMirrored());
		vec = vec.subtract(xOrigin, 0, zOrigin);
		vec = vec.subtract(rotationOffset.x, 0, rotationOffset.z);
		vec = vec.multiply(getScaleFB().getChaseTarget(), 1, getScaleLR().getChaseTarget());
		vec = rotateY(vec, getRotationTarget());
		vec = vec.add(xOrigin, 0, zOrigin);
		vec = vec.add(target.getX(), target.getY(), target.getZ());
		return BlockPos.containing(vec.x, vec.y, vec.z);
	}

	/**
	 * The anchor the schematic would get if it was moved to the given target, see {@link #moveTo}.
	 */
	public BlockPos anchorFor(BlockPos target) {
		return getAnchor().offset(target.subtract(this.target));
	}

	public BlockPos fromAnchor(BlockPos pos) {
		Vec3 vec = Vec3.ZERO.add(.5, 0, .5);
		Vec3 rotationOffset = getRotationOffset(isBothMirrored());
		vec = vec.subtract(xOrigin, 0, zOrigin);
		vec = vec.subtract(rotationOffset.x, 0, rotationOffset.z);
		vec = vec.multiply(getScaleFB().getChaseTarget(), 1, getScaleLR().getChaseTarget());
		vec = rotateY(vec, getRotationTarget());
		vec = vec.add(xOrigin, 0, zOrigin);
		return pos.subtract(BlockPos.containing(vec.x, vec.y, vec.z));
	}

	/**
	 * The rotation the schematic is turning to, snapped to a multiple of 90 degrees.
	 */
	public int getRotationTarget() {
		return Math.round(rotation.getChaseTarget() / 90) * 90;
	}

	public int getMirrorModifier(Direction.Axis axis) {
		if (axis == Direction.Axis.Z)
			return (int) getScaleLR().getChaseTarget();
		return (int) getScaleFB().getChaseTarget();
	}

	public float getCurrentRotation(float pt) {
		return rotation.getValue(pt);
	}

	public void tick() {
		prevChasingPos = chasingPos;
		chasingPos = chasingPos.lerp(Vec3.atLowerCornerOf(target), 0.45f);
		getScaleLR().tickChaser();
		getScaleFB().tickChaser();
		rotation.tickChaser();
		snapIfNotSmooth();
	}

	/**
	 * Without smooth animations the schematic jumps to where it is moving, turning or flipping to, right away.
	 */
	private void snapIfNotSmooth() {
		if (ModConfig.get().smoothAnimations)
			return;
		chasingPos = Vec3.atLowerCornerOf(target);
		prevChasingPos = chasingPos;
		getScaleLR().snapToTarget();
		getScaleFB().snapToTarget();
		rotation.snapToTarget();
	}

	public void flip(Direction.Axis axis) {
		if (axis == Direction.Axis.X)
			getScaleLR().updateChaseTarget(getScaleLR().getChaseTarget() * -1);
		if (axis == Direction.Axis.Z)
			getScaleFB().updateChaseTarget(getScaleFB().getChaseTarget() * -1);
		snapIfNotSmooth();
	}

	public void rotate90(boolean clockwise) {
		rotation.updateChaseTarget(getRotationTarget() + (clockwise ? -90 : 90));
		snapIfNotSmooth();
	}

	public void move(int xIn, int yIn, int zIn) {
		moveTo(target.offset(xIn, yIn, zIn));
	}

	public void startAt(BlockPos pos) {
		chasingPos = Vec3.atLowerCornerOf(pos);
		prevChasingPos = chasingPos;
		moveTo(pos);
	}

	public void moveTo(BlockPos pos) {
		target = pos;
		snapIfNotSmooth();
	}

	public LerpedFloat getScaleFB() {
		return scaleFrontBack;
	}

	public LerpedFloat getScaleLR() {
		return scaleLeftRight;
	}

}
