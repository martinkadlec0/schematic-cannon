package net.harou.schematiccannon.cannon;

import org.jetbrains.annotations.Nullable;


import net.harou.schematiccannon.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SchematicannonBlock extends BaseEntityBlock {

	private static final VoxelShape SHAPE =
		Shapes.or(box(1, 0, 1, 15, 8, 15), box(0.5, 8, 0.5, 15.5, 11, 15.5));

	public SchematicannonBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SchematicannonBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
		BlockEntityType<T> type) {
		return createTickerHelper(type, ModBlockEntityTypes.SCHEMATICANNON, (l, p, s, be) -> be.tick());
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity entity, ItemStack stack) {
		if (entity != null && level.getBlockEntity(pos) instanceof SchematicannonBlockEntity be)
			be.defaultYaw = (-Mth.floor((entity.getYRot() + (entity.isShiftKeyDown() ? 180.0F : 0.0F)) * 16.0F / 360.0F + 0.5F) & 15) * 360.0F / 16.0F;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
		BlockHitResult hitResult) {
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		if (level.getBlockEntity(pos) instanceof SchematicannonBlockEntity be)
			player.openMenu(be);
		return InteractionResult.SUCCESS;
	}


}
