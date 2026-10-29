package net.harou.schematiccannon.util;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.harou.schematiccannon.SchematicCannon;
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
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

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
		placeSchematicBlock(world, state, target, stack, data, false);
	}

	/**
	 * @param frozen skip {@code onPlace} and {@code setPlacedBy}, so nothing reacts before the whole schematic is placed.
	 *               {@link #settleSchematicBlock} runs them afterwards.
	 * @return whether the block was placed, not e.g. evaporated
	 */
	public static boolean placeSchematicBlock(Level world, BlockState state, BlockPos target, ItemStack stack,
										   @Nullable CompoundTag data, boolean frozen) {
		state = getPlacedState(state);

		if (evaporates(world, state, target)) {
			Block.dropResources(state, world, target);
			return false;
		}

		if (state.getBlock() instanceof BaseRailBlock)
			placeRailWithoutUpdate(world, state, target, frozen);
		else
			world.setBlock(target, state,
				Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | (frozen ? Block.UPDATE_SKIP_ON_PLACE : 0));

		if (data != null) {
			BlockEntity blockEntity = world.getBlockEntity(target);
			if (blockEntity != null) {
				data.putInt("x", target.getX());
				data.putInt("y", target.getY());
				data.putInt("z", target.getZ());
				SyncedBlockEntity.loadFromTag(blockEntity, data, world.registryAccess());
			}
		}

		if (!frozen)
			setPlacedBy(world, target, state, stack);
		return true;
	}

	/**
	 * Runs what a frozen placement skipped, once the whole schematic is there: {@code onPlace} (scheduled ticks, e.g.
	 * of fluids and falling blocks), {@code setPlacedBy} (e.g. pistons checking their power) and neighbor updates.
	 */
	public static void settleSchematicBlock(Level world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (state.isAir())
			return;
		// The other half is placed by the lower half or foot, like when printing without freezing
		if (isPlacedByOtherHalf(state))
			return;
		settleSchematicBlock(world, pos, state);
	}

	private static void settleSchematicBlock(Level world, BlockPos pos, BlockState state) {
		// Rails would reshape themselves, they keep the schematic's shape and only get what placeRailWithoutUpdate
		// skipped in a frozen print (powered and detector rails react to it)
		if (state.getBlock() instanceof BaseRailBlock)
			world.neighborChanged(pos, world.getBlockState(pos.below())
				.getBlock(), null);
		else
			state.onPlace(world, pos, Blocks.AIR.defaultBlockState(), false);
		setPlacedBy(world, pos, state, ItemStack.EMPTY);
		// Waterlogged blocks don't start their water flowing themselves
		scheduleFluidTick(world, pos, state);
		world.updateNeighborsAt(pos, state.getBlock(), null);
	}

	/**
	 * A water or lava source, or a bubble column (water without an item of its own), placed from a bucket once all
	 * blocks are there. Flowing fluid is never placed: it comes from the sources.
	 */
	public static boolean isFluidSource(BlockState state) {
		return (state.getBlock() instanceof LiquidBlock || state.getBlock() instanceof BubbleColumnBlock)
			&& state.getFluidState()
				.isSource();
	}

	/**
	 * The upper half of a door or two-block plant, or the head of a bed: the cannon places the lower half or foot, which
	 * places this half with it.
	 */
	public static boolean isPlacedByOtherHalf(BlockState state) {
		return isUpperHalf(state) || state.hasProperty(BlockStateProperties.BED_PART)
			&& state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
	}

	/**
	 * Whether the spot of the upper half is still empty with its lower half below, which adds it when it settles (a
	 * frozen print). It then takes its water from the spot, so a waterlogged one gets water there first.
	 */
	public static boolean awaitsUpperHalf(Level world, BlockPos pos, BlockState upperHalf) {
		BlockState below = world.getBlockState(pos.below());
		return isUpperHalf(upperHalf) && world.getBlockState(pos)
			.isAir() && below.is(upperHalf.getBlock()) && !isUpperHalf(below);
	}

	/** The upper half of a door or two-block plant, which its lower half places */
	public static boolean isUpperHalf(BlockState state) {
		return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
			&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
	}

	/**
	 * A block holding water: printed dry like any block, and filled from a bucket once all blocks are there.
	 */
	public static boolean isWaterlogged(BlockState state) {
		return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
	}

	/**
	 * Whether the fluid of the schematic block is missing where it fits: a source where the world has none (into air,
	 * flowing fluid, or replaceable blocks like grass, never other blocks unless replaceBlocks), or the water of a
	 * waterlogged block printed dry. Checked when the cannon fires, and again when the fluid lands, as the spot may have
	 * changed in between.
	 */
	public static boolean fluidFits(Level world, BlockPos pos, BlockState state, boolean replaceBlocks) {
		BlockState current = world.getBlockState(pos);
		if (isWaterlogged(state))
			return awaitsUpperHalf(world, pos, state) || current.hasProperty(BlockStateProperties.WATERLOGGED)
				&& !current.getValue(BlockStateProperties.WATERLOGGED) && getPlacedState(current) == getPlacedState(state);
		Fluid fluid = state.getFluidState()
			.getType();
		return isFluidSource(state) && !current.getFluidState()
			.isSourceOfType(fluid) && (replaceBlocks || current.isAir() || current.getBlock() instanceof LiquidBlock
				// Not vanilla's canBeReplaced(fluid): it also lets fluids replace signs, torches, rails and redstone
				|| current.canBeReplaced());
	}

	/**
	 * Places the fluid of a schematic block: the source itself, or the water of a waterlogged block into the block
	 * printed dry before.
	 *
	 * @param frozen don't let it flow yet; {@link #settleSchematicBlock} starts it
	 * @return whether the fluid was placed, not e.g. evaporated
	 */
	public static boolean placeSchematicFluid(Level world, BlockState state, BlockPos target, boolean frozen) {
		if (evaporates(world, state, target))
			return false;

		BlockState placed = state;
		if (isWaterlogged(state)) {
			BlockState current = world.getBlockState(target);
			if (awaitsUpperHalf(world, target, state))
				placed = Blocks.WATER.defaultBlockState();
			else if (current.hasProperty(BlockStateProperties.WATERLOGGED))
				placed = current.setValue(BlockStateProperties.WATERLOGGED, true);
			else
				return false;
		}
		world.setBlock(target, placed,
			Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | (frozen ? Block.UPDATE_SKIP_ON_PLACE : 0));
		if (!frozen)
			scheduleFluidTick(world, target, placed);
		return true;
	}

	/**
	 * Whether every chunk the box covers is loaded, e.g. before a check that could otherwise load or generate chunks.
	 */
	public static boolean isLoaded(Level world, BoundingBox box) {
		int y = Mth.clamp(box.minY(), world.getMinY(), world.getMaxY());
		for (int x = SectionPos.blockToSectionCoord(box.minX()); x <= SectionPos.blockToSectionCoord(box.maxX()); x++)
			for (int z = SectionPos.blockToSectionCoord(box.minZ()); z <= SectionPos.blockToSectionCoord(box.maxZ()); z++)
				if (!world.isLoaded(new BlockPos(SectionPos.sectionToBlockCoord(x), y, SectionPos.sectionToBlockCoord(z))))
					return false;
		return true;
	}

	/** Blocks whose setPlacedBy failed, logged once each */
	private static final Set<Block> FAILED_SET_PLACED_BY = new HashSet<>();

	/**
	 * Runs what placing the block by hand does, without a player. Blocks may expect one, so a failure is logged (once per
	 * block, a print may have thousands of it) and the print goes on.
	 */
	private static void setPlacedBy(Level world, BlockPos pos, BlockState state, ItemStack stack) {
		try {
			state.getBlock()
				.setPlacedBy(world, pos, state, null, stack);
		} catch (Exception e) {
			if (FAILED_SET_PLACED_BY.add(state.getBlock()))
				SchematicCannon.LOGGER.warn("Failed to set up the printed block {} at {}, not logged again for this block",
					state, pos.toShortString(), e);
		}
	}

	/**
	 * Whether the water of the block would evaporate there (e.g. in the Nether), with the hiss and smoke of a water
	 * bucket emptied there.
	 */
	private static boolean evaporates(Level world, BlockState state, BlockPos target) {
		if (!world.environmentAttributes()
			.getValue(EnvironmentAttributes.WATER_EVAPORATES, target) || !state.getFluidState()
				.is(FluidTags.WATER))
			return false;
		world.playSound(null, target, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
			2.6F + (world.getRandom().nextFloat() - world.getRandom().nextFloat()) * 0.8F);
		if (world instanceof ServerLevel serverLevel)
			serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, target.getX() + .5, target.getY() + .5,
				target.getZ() + .5, 8, .25, .25, .25, 0);
		return true;
	}

	private static void scheduleFluidTick(Level world, BlockPos pos, BlockState state) {
		FluidState fluid = state.getFluidState();
		if (!fluid.isEmpty())
			world.scheduleTick(pos, fluid.getType(), fluid.getType()
				.getTickDelay(world));
	}

	private static void placeRailWithoutUpdate(Level world, BlockState state, BlockPos target, boolean frozen) {
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
		// Powered and detector rails react to it; a frozen print leaves that to settling
		if (!frozen)
			world.neighborChanged(target, world.getBlockState(target.below())
				.getBlock(), null);
	}

}
