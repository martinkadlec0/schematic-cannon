package net.harou.schematiccannon.util;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

public class RaycastHelper {
	public static BlockHitResult rayTraceRange(Level level, Player player, double range) {
		Vec3 origin = player.getEyePosition();
		Vec3 target = origin.add(player.getLookAngle()
			.scale(range));
		ClipContext context = new ClipContext(origin, target, Block.COLLIDER, Fluid.NONE, player);
		return level.clip(context);
	}

	public static BlockHitResult rayTraceUntil(Player player, double range, Predicate<BlockPos> predicate) {
		Vec3 origin = player.getEyePosition();
		Vec3 target = origin.add(player.getLookAngle()
			.scale(range));
		return rayTraceUntil(origin, target, predicate);
	}

	/**
	 * Finds the first block along the ray that matches the predicate.
	 */
	public static BlockHitResult rayTraceUntil(Vec3 start, Vec3 end, Predicate<BlockPos> predicate) {
		BlockPos startPos = BlockPos.containing(start);
		if (predicate.test(startPos))
			return new BlockHitResult(start, Direction.getApproximateNearest(end.subtract(start)), startPos, true);
		return BlockGetter.traverseBlocks(start, end, predicate,
			(test, pos) -> test.test(pos) ? Shapes.block()
				.clip(start, end, pos) : null,
			test -> BlockHitResult.miss(end, Direction.getApproximateNearest(end.subtract(start)), BlockPos.containing(end)));
	}

}
