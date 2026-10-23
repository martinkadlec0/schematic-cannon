package net.harou.schematiccannon.util;

import org.jetbrains.annotations.Nullable;

import net.harou.schematiccannon.foundation.SyncedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public class BlockHelper {

	public static BlockState setZeroAge(BlockState blockState) {
		if (blockState.hasProperty(BlockStateProperties.AGE_1))
			return blockState.setValue(BlockStateProperties.AGE_1, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_2))
			return blockState.setValue(BlockStateProperties.AGE_2, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_3))
			return blockState.setValue(BlockStateProperties.AGE_3, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_4))
			return blockState.setValue(BlockStateProperties.AGE_4, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_5))
			return blockState.setValue(BlockStateProperties.AGE_5, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_7))
			return blockState.setValue(BlockStateProperties.AGE_7, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_15))
			return blockState.setValue(BlockStateProperties.AGE_15, 0);
		if (blockState.hasProperty(BlockStateProperties.AGE_25))
			return blockState.setValue(BlockStateProperties.AGE_25, 0);
		if (blockState.hasProperty(BlockStateProperties.LEVEL_HONEY))
			return blockState.setValue(BlockStateProperties.LEVEL_HONEY, 0);
		if (blockState.hasProperty(BlockStateProperties.HATCH))
			return blockState.setValue(BlockStateProperties.HATCH, 0);
		if (blockState.hasProperty(BlockStateProperties.STAGE))
			return blockState.setValue(BlockStateProperties.STAGE, 0);
		if (blockState.is(BlockTags.CAULDRONS))
			return Blocks.CAULDRON.defaultBlockState();
		if (blockState.hasProperty(BlockStateProperties.LEVEL_COMPOSTER))
			return blockState.setValue(BlockStateProperties.LEVEL_COMPOSTER, 0);
		if (blockState.hasProperty(BlockStateProperties.EXTENDED))
			return blockState.setValue(BlockStateProperties.EXTENDED, false);
		if (blockState.hasProperty(BlockStateProperties.RESPAWN_ANCHOR_CHARGES))
			return blockState.setValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES, 0);
		return blockState;
	}

	/**
	 * The state the printer actually places for a schematic block. Comparing these tells whether a block in the world
	 * already matches, e.g. a grown crop or a waterlogged block.
	 */
	public static BlockState getPlacedState(BlockState blockState) {
		blockState = setZeroAge(blockState);
		if (blockState.hasProperty(BlockStateProperties.WATERLOGGED))
			blockState = blockState.setValue(BlockStateProperties.WATERLOGGED, false);
		// Contents of jukeboxes, lecterns and bookshelves are not printed
		if (blockState.hasProperty(BlockStateProperties.HAS_RECORD))
			blockState = blockState.setValue(BlockStateProperties.HAS_RECORD, false);
		if (blockState.hasProperty(BlockStateProperties.HAS_BOOK))
			blockState = blockState.setValue(BlockStateProperties.HAS_BOOK, false);
		if (blockState.getBlock() instanceof ChiseledBookShelfBlock)
			for (BooleanProperty slot : ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES)
				blockState = blockState.setValue(slot, false);
		if (blockState.getBlock() instanceof BrewingStandBlock)
			for (BooleanProperty bottle : BrewingStandBlock.HAS_BOTTLE)
				blockState = blockState.setValue(bottle, false);
		// Burning furnaces and opened barrels
		if (blockState.getBlock() instanceof AbstractFurnaceBlock)
			blockState = blockState.setValue(AbstractFurnaceBlock.LIT, false);
		if (blockState.getBlock() instanceof BarrelBlock)
			blockState = blockState.setValue(BarrelBlock.OPEN, false);
		return blockState;
	}

	/**
	 * Block entity data that is safe to copy in survival: only signs and banners keep their data.
	 */
	@Nullable
	public static CompoundTag prepareBlockEntityData(Level level, BlockState blockState, BlockEntity blockEntity) {
		if (blockEntity == null)
			return null;
		CompoundTag data = null;
		if (blockState.is(BlockTags.BANNERS) || blockState.is(BlockTags.ALL_SIGNS))
			data = blockEntity.saveWithFullMetadata(level.registryAccess());
		return NBTProcessors.process(blockState, blockEntity, data, level.registryAccess());
	}

	public static void placeSchematicBlock(Level world, BlockState state, BlockPos target, ItemStack stack,
										   @Nullable CompoundTag data) {
		state = getPlacedState(state);

		if (world.environmentAttributes()
			.getValue(EnvironmentAttributes.WATER_EVAPORATES, target) && state.getFluidState()
			.is(FluidTags.WATER)) {
			world.playSound(null, target, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
				2.6F + (world.getRandom().nextFloat() - world.getRandom().nextFloat()) * 0.8F);
			if (world instanceof ServerLevel serverLevel)
				serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, target.getX() + .5, target.getY() + .5,
					target.getZ() + .5, 8, .25, .25, .25, 0);
			Block.dropResources(state, world, target);
			return;
		}

		if (state.getBlock() instanceof BaseRailBlock)
			placeRailWithoutUpdate(world, state, target);
		else
			world.setBlock(target, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);

		if (data != null) {
			BlockEntity blockEntity = world.getBlockEntity(target);
			if (blockEntity != null) {
				data.putInt("x", target.getX());
				data.putInt("y", target.getY());
				data.putInt("z", target.getZ());
				SyncedBlockEntity.loadFromTag(blockEntity, data, world.registryAccess());
			}
		}

		try {
			state.getBlock()
				.setPlacedBy(world, target, state, null, stack);
		} catch (Exception ignored) {
		}
	}

	private static void placeRailWithoutUpdate(Level world, BlockState state, BlockPos target) {
		LevelChunk chunk = world.getChunkAt(target);
		int idx = chunk.getSectionIndex(target.getY());
		LevelChunkSection chunksection = chunk.getSection(idx);
		// Rails have no block entity, so remove the one of the replaced block
		world.removeBlockEntity(target);
		BlockState old = chunksection.setBlockState(SectionPos.sectionRelative(target.getX()),
			SectionPos.sectionRelative(target.getY()), SectionPos.sectionRelative(target.getZ()), state);
		chunk.markUnsaved();
		// Written into the section directly so the rail does not reshape itself on placement
		world.sendBlockUpdated(target, old, state, Block.UPDATE_CLIENTS);
		world.getChunkSource()
			.getLightEngine()
			.checkBlock(target);
		world.neighborChanged(target, world.getBlockState(target.below())
			.getBlock(), null);
	}

}
