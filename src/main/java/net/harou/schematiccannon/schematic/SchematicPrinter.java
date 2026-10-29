package net.harou.schematiccannon.schematic;

import java.util.LinkedList;
import java.util.List;

import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.cannon.MaterialChecklist;
import net.harou.schematiccannon.foundation.SyncedBlockEntity;
import net.harou.schematiccannon.registry.ModDataComponents;
import net.harou.schematiccannon.schematic.level.SchematicLevel;
import net.harou.schematiccannon.util.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseCoralPlantTypeBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class SchematicPrinter {

	public enum PrintStage {
		BLOCKS, DEFERRED_BLOCKS,
		/** Fluid sources and the water of waterlogged blocks, once all blocks are there to hold them */
		FLUIDS, ENTITIES
	}

	private boolean schematicLoaded;
	private boolean isErrored;
	private SchematicLevel blockReader;
	private BlockPos schematicAnchor;
	/** Of the loaded schematic, see {@link #getSchematicSize} */
	private Vec3i schematicSize = Vec3i.ZERO;

	private BlockPos currentPos;
	private int printingEntityIndex;
	private PrintStage printStage;
	private List<BlockPos> deferredBlocks;
	private List<BlockPos> fluidBlocks;

	public SchematicPrinter() {
		printingEntityIndex = -1;
		printStage = PrintStage.BLOCKS;
		deferredBlocks = new LinkedList<>();
		fluidBlocks = new LinkedList<>();
	}

	/**
	 * Reads what clients need for rendering the cannon.
	 */
	public void read(ValueInput input) {
		currentPos = input.read("CurrentPos", BlockPos.CODEC)
			.orElse(null);
		schematicAnchor = input.read("Anchor", BlockPos.CODEC)
			.orElse(null);
		schematicLoaded = schematicAnchor != null;
	}

	/**
	 * Writes what clients need for rendering the cannon.
	 */
	public void write(ValueOutput output) {
		output.storeNullable("CurrentPos", BlockPos.CODEC, currentPos);
		output.storeNullable("Anchor", BlockPos.CODEC, schematicAnchor);
	}

	public void loadSchematic(ItemStack blueprint, Level originalWorld, boolean processNBT) {
		if (!blueprint.has(ModDataComponents.SCHEMATIC_ANCHOR) || !blueprint.has(ModDataComponents.SCHEMATIC_DEPLOYED))
			return;

		StructureTemplate activeTemplate = SchematicItem.loadSchematic(originalWorld, blueprint);
		schematicSize = activeTemplate.getSize();
		StructurePlaceSettings settings = SchematicItem.getSettings(blueprint, processNBT);

		schematicAnchor = blueprint.get(ModDataComponents.SCHEMATIC_ANCHOR);
		blockReader = new SchematicLevel(schematicAnchor, originalWorld);
		blockReader.filterOperatorEntities = processNBT;

		try {
			activeTemplate.placeInWorld(blockReader, schematicAnchor, schematicAnchor, settings,
				blockReader.getRandom(), Block.UPDATE_CLIENTS);
		} catch (Exception e) {
			SchematicCannon.LOGGER.error("Failed to load Schematic for Printing", e);
			schematicLoaded = true;
			isErrored = true;
			return;
		}

		BlockPos extraBounds = StructureTemplate.calculateRelativePosition(settings, BlockPos.ZERO.offset(activeTemplate.getSize())
			.offset(-1, -1, -1));
		blockReader.setBounds(SchematicLevel.encapsulate(blockReader.getBounds(), extraBounds));

		printingEntityIndex = -1;
		printStage = PrintStage.BLOCKS;
		deferredBlocks.clear();
		fluidBlocks.clear();
		BoundingBox bounds = blockReader.getBounds();
		currentPos = new BlockPos(bounds.minX() - 1, bounds.minY(), bounds.minZ());
		schematicLoaded = true;
	}

	public void resetSchematic() {
		schematicLoaded = false;
		schematicAnchor = null;
		isErrored = false;
		currentPos = null;
		blockReader = null;
		printingEntityIndex = -1;
		printStage = PrintStage.BLOCKS;
		deferredBlocks.clear();
		fluidBlocks.clear();
	}

	public boolean isLoaded() {
		return schematicLoaded;
	}

	/**
	 * The size of the loaded schematic. The size stored on the stack may be outdated, e.g. after its file was uploaded
	 * again.
	 */
	public Vec3i getSchematicSize() {
		return schematicSize;
	}

	/** The schematic's block at the current target */
	public BlockState getCurrentBlockState() {
		return blockReader.getBlockState(getCurrentTarget());
	}

	public boolean isPrintingFluids() {
		return printStage == PrintStage.FLUIDS;
	}

	public boolean isErrored() {
		return isErrored;
	}

	public BlockPos getCurrentTarget() {
		if (!isLoaded() || isErrored())
			return null;
		return schematicAnchor.offset(currentPos);
	}

	public boolean isWorldEmpty() {
		return blockReader.getAllPositions()
			.isEmpty();
	}

	@FunctionalInterface
	public interface BlockTargetHandler {
		void handle(BlockPos target, BlockState blockState, BlockEntity blockEntity);
	}

	@FunctionalInterface
	public interface EntityTargetHandler {
		void handle(BlockPos target, Entity entity);
	}

	/**
	 * @param fluidHandler for the fluid stage, with the schematic's block: a fluid source or a waterlogged block, see
	 *                     {@link BlockHelper#placeSchematicFluid}
	 */
	public void handleCurrentTarget(BlockTargetHandler blockHandler, BlockTargetHandler fluidHandler,
		EntityTargetHandler entityHandler) {
		BlockPos target = getCurrentTarget();

		if (printStage == PrintStage.ENTITIES) {
			Entity entity = blockReader.getEntityList()
				.get(printingEntityIndex);
			entityHandler.handle(target, entity);
		} else if (printStage == PrintStage.FLUIDS) {
			fluidHandler.handle(target, blockReader.getBlockState(target), null);
		} else {
			BlockState blockState = BlockHelper.setZeroAge(blockReader.getBlockState(target));
			BlockEntity blockEntity = blockReader.getBlockEntity(target);
			blockHandler.handle(target, blockState, blockEntity);
		}
	}

	@FunctionalInterface
	public interface PlacementPredicate {
		boolean shouldPlace(BlockPos target, BlockState blockState, BlockEntity blockEntity, BlockState toReplace,
			BlockState toReplaceOther, boolean isNormalCube);
	}

	/**
	 * For the creative instant print: everything is placed, fluids replacing whatever is in their way like blocks.
	 */
	public boolean shouldPlaceCurrent(Level world) {
		return shouldPlaceCurrent(world, (a, b, c, d, e, f) -> true, true);
	}

	public boolean shouldPlaceCurrent(Level world, PlacementPredicate predicate) {
		return shouldPlaceCurrent(world, predicate, false);
	}

	private boolean shouldPlaceCurrent(Level world, PlacementPredicate predicate, boolean fluidsReplaceBlocks) {
		if (world == null)
			return false;

		if (printStage == PrintStage.ENTITIES)
			return true;
		if (printStage == PrintStage.FLUIDS)
			return shouldPlaceFluid(world, getCurrentTarget(), fluidsReplaceBlocks);

		return shouldPlaceBlock(world, predicate, getCurrentTarget());
	}

	/**
	 * Whether the fluid of the schematic block is still missing where it fits, see {@link BlockHelper#fluidFits}.
	 */
	public boolean shouldPlaceFluid(Level world, BlockPos pos, boolean replaceBlocks) {
		return world.isLoaded(pos) && world.getWorldBorder()
			.isWithinBounds(pos) && BlockHelper.fluidFits(world, pos, blockReader.getBlockState(pos), replaceBlocks);
	}

	public boolean shouldPlaceBlock(Level world, PlacementPredicate predicate, BlockPos pos) {
		// Check this first, reading blocks of an unloaded chunk would load it
		if (!world.isLoaded(pos))
			return false;
		if (!world.getWorldBorder()
			.isWithinBounds(pos))
			return false;

		BlockState state = BlockHelper.setZeroAge(blockReader.getBlockState(pos));
		BlockEntity blockEntity = blockReader.getBlockEntity(pos);
		BlockState toReplace = world.getBlockState(pos);
		if (BlockHelper.getPlacedState(toReplace) == BlockHelper.getPlacedState(state))
			return false;

		BlockPos otherPos = null;
		if (state.hasProperty(BlockStateProperties.BED_PART) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
			&& state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT)
			otherPos = pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
			&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER)
			otherPos = pos.above();
		if (otherPos != null && !world.isLoaded(otherPos))
			return false;
		BlockState toReplaceOther = otherPos == null ? null : world.getBlockState(otherPos);

		if (toReplace.getDestroySpeed(world, pos) == -1
			|| (toReplaceOther != null && toReplaceOther.getDestroySpeed(world, otherPos) == -1))
			return false;

		boolean isNormalCube = state.isRedstoneConductor(blockReader, pos);
		return predicate.shouldPlace(pos, state, blockEntity, toReplace, toReplaceOther, isNormalCube);
	}

	public ItemRequirement getCurrentRequirement() {
		if (printStage == PrintStage.ENTITIES)
			return ItemRequirement.of(blockReader.getEntityList()
				.get(printingEntityIndex));

		BlockPos target = getCurrentTarget();
		if (printStage == PrintStage.FLUIDS)
			return ItemRequirement.ofFluid(blockReader.getBlockState(target));
		BlockState blockState = BlockHelper.setZeroAge(blockReader.getBlockState(target));
		BlockEntity blockEntity = null;
		if (blockState.hasBlockEntity()) {
			blockEntity = ((EntityBlock) blockState.getBlock()).newBlockEntity(target, blockState);
			CompoundTag data = BlockHelper.prepareBlockEntityData(blockReader, blockState, blockReader.getBlockEntity(target));
			if (blockEntity != null && data != null)
				SyncedBlockEntity.loadFromTag(blockEntity, data, blockReader.registryAccess());
		}
		return ItemRequirement.of(blockState, blockEntity);
	}

	public int markAllBlockRequirements(MaterialChecklist checklist, Level world, PlacementPredicate predicate) {
		int blocksToPlace = 0;
		for (BlockPos localPos : blockReader.getAllPositions()) {
			BlockPos pos = localPos.offset(schematicAnchor);
			if (world.isOutsideBuildHeight(pos))
				continue;
			if (!world.isLoaded(pos)) {
				checklist.warnBlockNotLoaded();
				continue;
			}
			BlockState required = BlockHelper.setZeroAge(blockReader.getBlockState(pos));
			// The bucket for the fluid, in addition to a waterlogged block itself. The cannon's rules see the fluid going
			// into an empty spot, as it never replaces anything, which leaves the ones that also stop fluids (e.g. next to
			// the cannon).
			ItemRequirement bucket = ItemRequirement.ofFluid(required);
			if ((BlockHelper.isFluidSource(required) || BlockHelper.isWaterlogged(required)) && !bucket.isInvalid()
				&& needsFluid(world, predicate, pos, required) && predicate.shouldPlace(pos, required.getFluidState()
					.createLegacyBlock(), null, Blocks.AIR.defaultBlockState(), null, false)) {
				checklist.require(bucket);
				blocksToPlace++;
			}
			if (!shouldPlaceBlock(world, predicate, pos))
				continue;
			BlockEntity requiredBE = blockReader.getBlockEntity(pos);
			ItemRequirement requirement = ItemRequirement.of(required, requiredBE);
			if (requirement.isEmpty())
				continue;
			if (requirement.isInvalid())
				continue;
			checklist.require(requirement);
			blocksToPlace++;
		}
		return blocksToPlace;
	}

	/**
	 * Whether the cannon will place the fluid. Like {@link #shouldPlaceFluid}, but for a waterlogged block that isn't there
	 * yet, also whether the cannon will place the block first (not when another block stays in its way).
	 */
	private boolean needsFluid(Level world, PlacementPredicate predicate, BlockPos pos, BlockState state) {
		if (!BlockHelper.isWaterlogged(state))
			return shouldPlaceFluid(world, pos, false);
		BlockState current = world.getBlockState(pos);
		if (BlockHelper.getPlacedState(current) == BlockHelper.getPlacedState(state))
			return !BlockHelper.isWaterlogged(current);
		return BlockHelper.isPlacedByOtherHalf(state) || shouldPlaceBlock(world, predicate, pos);
	}

	public void markAllEntityRequirements(MaterialChecklist checklist) {
		for (Entity entity : blockReader.getEntityList()) {
			ItemRequirement requirement = ItemRequirement.of(entity);
			if (requirement.isEmpty() || requirement.isInvalid())
				continue;
			checklist.require(requirement);
		}
	}

	public boolean advanceCurrentPos() {
		List<Entity> entities = blockReader.getEntityList();

		do {
			if (printStage == PrintStage.BLOCKS) {
				while (tryAdvanceCurrentPos()) {
					// Left for a later stage
				}
			}

			if (printStage == PrintStage.DEFERRED_BLOCKS) {
				if (deferredBlocks.isEmpty()) {
					printStage = PrintStage.FLUIDS;
				} else {
					currentPos = deferredBlocks.remove(0);
				}
			}

			if (printStage == PrintStage.FLUIDS) {
				if (fluidBlocks.isEmpty()) {
					printStage = PrintStage.ENTITIES;
				} else {
					currentPos = fluidBlocks.remove(0);
				}
			}

			if (printStage == PrintStage.ENTITIES) {
				if (printingEntityIndex + 1 < entities.size()) {
					printingEntityIndex++;
					currentPos = entities.get(printingEntityIndex)
						.blockPosition()
						.subtract(schematicAnchor);
				} else {
					// Reached end of printing
					return false;
				}
			}
		} while (!blockReader.getBounds()
			.isInside(currentPos));

		// More things available to print
		return true;
	}

	/**
	 * Moves to the next position of the block stage.
	 *
	 * @return whether the block there is left for a later stage: deferred blocks, fluids
	 */
	public boolean tryAdvanceCurrentPos() {
		currentPos = currentPos.relative(Direction.EAST);
		BoundingBox bounds = blockReader.getBounds();
		BlockPos posInBounds = currentPos.offset(-bounds.minX(), -bounds.minY(), -bounds.minZ());

		if (posInBounds.getX() > bounds.getXSpan())
			currentPos = new BlockPos(bounds.minX(), currentPos.getY(), currentPos.getZ() + 1).west();
		if (posInBounds.getZ() > bounds.getZSpan())
			currentPos = new BlockPos(currentPos.getX(), currentPos.getY() + 1, bounds.minZ()).west();

		// End of blocks reached
		if (currentPos.getY() > bounds.getYSpan()) {
			printStage = PrintStage.DEFERRED_BLOCKS;
			return false;
		}

		BlockState state = blockReader.getBlockState(getCurrentTarget());
		// Fluids go in once all blocks are there to hold them; a waterlogged block is printed dry first
		if (BlockHelper.isFluidSource(state) || BlockHelper.isWaterlogged(state))
			fluidBlocks.add(currentPos);
		if (BlockHelper.isFluidSource(state))
			return true;
		if (shouldDeferBlock(state)) {
			deferredBlocks.add(currentPos);
			return true;
		}
		return false;
	}

	/**
	 * Blocks that need a supporting block are placed after all other blocks. So are blocks that hold water without being
	 * waterlogged (kelp, seagrass): their water could flow out of the unfinished build otherwise.
	 */
	public static boolean shouldDeferBlock(BlockState state) {
		Block block = state.getBlock();
		if (state.hasProperty(BlockStateProperties.HANGING))
			return true;
		if (!state.getFluidState()
			.isEmpty() && !state.hasProperty(BlockStateProperties.WATERLOGGED) && !(block instanceof LiquidBlock)
			&& !BlockHelper.isFluidSource(state))
			return true;
		if (block instanceof LadderBlock || block instanceof BaseTorchBlock || block instanceof SignBlock
			|| block instanceof BasePressurePlateBlock || block instanceof BaseRailBlock || block instanceof DiodeBlock
			|| block instanceof RedstoneWireBlock || block instanceof WoolCarpetBlock)
			return true;
		if (block instanceof FaceAttachedHorizontalDirectionalBlock && !(block instanceof GrindstoneBlock))
			return true;
		return block == Blocks.BELL || block == Blocks.COCOA || block == Blocks.FLOWER_POT
			|| block == Blocks.MOSS_CARPET || block == Blocks.BAMBOO_SAPLING || block == Blocks.BIG_DRIPLEAF
			|| block == Blocks.VINE || block == Blocks.TWISTING_VINES_PLANT || block == Blocks.TWISTING_VINES
			|| block == Blocks.WEEPING_VINES_PLANT || block == Blocks.WEEPING_VINES || block == Blocks.CAKE
			|| block instanceof BaseCoralPlantTypeBlock || state.is(BlockTags.CAVE_VINES) || state.is(BlockTags.BANNERS)
			|| state.is(BlockTags.BEDS) || state.is(BlockTags.DOORS);
	}

	public void sendBlockUpdates(Level level) {
		BoundingBox bounds = blockReader.getBounds();
		BlockPos.betweenClosedStream(bounds.inflatedBy(1))
			.filter(pos -> !bounds.isInside(pos))
			.filter(pos -> level.isLoaded(pos.offset(schematicAnchor)) && level.getFluidState(pos.offset(schematicAnchor))
				.is(Fluids.WATER))
			.forEach(pos -> level.scheduleTick(pos.offset(schematicAnchor), Fluids.WATER, Fluids.WATER.getTickDelay(level)));
	}

}
