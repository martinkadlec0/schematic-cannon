package net.harou.schematiccannon.table;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;


import net.harou.schematiccannon.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SchematicTableBlock extends HorizontalDirectionalBlock implements EntityBlock {

	// Trestle legs at the sides and the tilted drawing board above them
	private static final VoxelShape SHAPE_NORTH_SOUTH =
		Shapes.or(box(1, 0, 3, 4, 10, 13), box(12, 0, 3, 15, 10, 13), box(0, 7, 0, 16, 14, 16));
	private static final VoxelShape SHAPE_EAST_WEST =
		Shapes.or(box(3, 0, 1, 13, 10, 4), box(3, 0, 12, 13, 10, 15), box(0, 7, 0, 16, 14, 16));
	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static {
		for (Direction direction : Direction.Plane.HORIZONTAL)
			SHAPES.put(direction, direction.getAxis() == Direction.Axis.Z ? SHAPE_NORTH_SOUTH : SHAPE_EAST_WEST);
	}

	public SchematicTableBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
		super.createBlockStateDefinition(builder);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection()
			.getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SchematicTableBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
		BlockEntityType<T> type) {
		if (level.isClientSide() || type != ModBlockEntityTypes.SCHEMATIC_TABLE)
			return null;
		return (l, p, s, be) -> ((SchematicTableBlockEntity) be).tick();
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
		BlockHitResult hitResult) {
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		if (level.getBlockEntity(pos) instanceof SchematicTableBlockEntity be)
			player.openMenu(be);
		return InteractionResult.SUCCESS;
	}


	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

}
