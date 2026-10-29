package net.harou.schematiccannon.cannon;

import java.util.Optional;

import net.harou.schematiccannon.util.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A block or entity flying from the Schematicannon to its target.
 */
public abstract class LaunchedItem {

	public int totalTicks;
	public int ticksRemaining;
	public BlockPos target;
	public ItemStack stack;
	/** Whether it was placed when it landed */
	public boolean placed;

	private LaunchedItem(BlockPos start, BlockPos target, ItemStack stack) {
		this(target, stack, ticksForDistance(start, target), ticksForDistance(start, target));
	}

	private static int ticksForDistance(BlockPos start, BlockPos target) {
		return (int) (Math.max(10, Math.sqrt(Math.sqrt(target.distSqr(start))) * 4f));
	}

	LaunchedItem() {
	}

	private LaunchedItem(BlockPos target, ItemStack stack, int ticksLeft, int total) {
		this.target = target;
		this.stack = stack;
		this.totalTicks = total;
		this.ticksRemaining = ticksLeft;
	}

	public boolean update(Level world) {
		if (ticksRemaining > 0) {
			ticksRemaining--;
			return false;
		}
		if (world.isClientSide())
			return false;

		placed = place(world);
		return true;
	}

	public void write(ValueOutput output) {
		output.putInt("TotalTicks", totalTicks);
		output.putInt("TicksLeft", ticksRemaining);
		output.store("Stack", ItemStack.OPTIONAL_CODEC, stack);
		output.store("Target", BlockPos.CODEC, target);
	}

	public static LaunchedItem read(ValueInput input) {
		LaunchedItem launched = input.read("BlockState", BlockState.CODEC)
			.isPresent() ? new ForBlockState() : new ForEntity();
		launched.readData(input);
		return launched;
	}

	/** @return whether anything was placed */
	abstract boolean place(Level world);

	void readData(ValueInput input) {
		target = input.read("Target", BlockPos.CODEC)
			.orElse(BlockPos.ZERO);
		ticksRemaining = input.getIntOr("TicksLeft", 0);
		totalTicks = input.getIntOr("TotalTicks", 0);
		stack = input.read("Stack", ItemStack.OPTIONAL_CODEC)
			.orElse(ItemStack.EMPTY);
	}

	public static class ForBlockState extends LaunchedItem {
		public BlockState state;
		public CompoundTag data;
		/** Placed without updates, see {@link BlockHelper#placeSchematicBlock(Level, BlockState, BlockPos, ItemStack, CompoundTag, boolean)} */
		public boolean frozen;
		/** The fluid of the block is placed, see {@link BlockHelper#placeSchematicFluid} */
		public boolean fluid;

		ForBlockState() {
		}

		public ForBlockState(BlockPos start, BlockPos target, ItemStack stack, BlockState state, CompoundTag data,
			boolean frozen) {
			super(start, target, stack);
			this.state = state;
			this.data = data;
			this.frozen = frozen;
		}

		@Override
		public void write(ValueOutput output) {
			super.write(output);
			output.store("BlockState", BlockState.CODEC, state);
			if (frozen)
				output.putBoolean("Frozen", true);
			if (fluid)
				output.putBoolean("Fluid", true);
			if (data != null) {
				data.remove("x");
				data.remove("y");
				data.remove("z");
				data.remove("id");
				output.store("Data", CompoundTag.CODEC, data);
			}
		}

		@Override
		void readData(ValueInput input) {
			super.readData(input);
			state = input.read("BlockState", BlockState.CODEC)
				.orElse(Blocks.AIR.defaultBlockState());
			data = input.read("Data", CompoundTag.CODEC)
				.orElse(null);
			frozen = input.getBooleanOr("Frozen", false);
			fluid = input.getBooleanOr("Fluid", false);
		}

		@Override
		boolean place(Level world) {
			if (!fluid)
				return BlockHelper.placeSchematicBlock(world, state, target, stack, data, frozen);
			// The spot may have changed while it flew, e.g. a chest placed there, which a fluid never replaces
			return BlockHelper.fluidFits(world, target, state, false) && BlockHelper.placeSchematicFluid(world, state,
				target, frozen);
		}

	}

	public static class ForEntity extends LaunchedItem {
		public Entity entity;
		private CompoundTag deferredTag;

		ForEntity() {
		}

		public ForEntity(BlockPos start, BlockPos target, ItemStack stack, Entity entity) {
			super(start, target, stack);
			this.entity = entity;
		}

		@Override
		public boolean update(Level world) {
			if (deferredTag != null && entity == null) {
				try {
					Optional<Entity> loadEntityUnchecked = EntityType.create(
						TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), deferredTag), world,
						new EntitySpawnRequest(EntitySpawnReason.LOAD, false));
					if (loadEntityUnchecked.isEmpty())
						return true;
					entity = loadEntityUnchecked.get();
				} catch (Exception e) {
					return true;
				}
				deferredTag = null;
			}
			return super.update(world);
		}

		@Override
		public void write(ValueOutput output) {
			super.write(output);
			if (entity != null) {
				TagValueOutput entityOutput =
					TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
				entity.save(entityOutput);
				output.store("Entity", CompoundTag.CODEC, entityOutput.buildResult());
			}
		}

		@Override
		void readData(ValueInput input) {
			super.readData(input);
			deferredTag = input.read("Entity", CompoundTag.CODEC)
				.orElse(null);
		}

		@Override
		boolean place(Level world) {
			// Schematic entities are created with the real level (SchematicLevel#getLevel)
			return entity != null && world.addFreshEntity(entity);
		}

	}

}
