package net.harou.schematiccannon.schematic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.harou.schematiccannon.util.BlockHelper;
import net.harou.schematiccannon.util.NBTProcessors;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.PathBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SegmentableBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The items the Schematicannon needs to place a block or entity.
 */
public class ItemRequirement {
	public static final ItemRequirement NONE = new ItemRequirement(Collections.emptyList());
	public static final ItemRequirement INVALID = new ItemRequirement(Collections.emptyList());

	protected List<StackRequirement> requiredItems;

	public ItemRequirement(List<StackRequirement> requiredItems) {
		this.requiredItems = requiredItems;
	}

	public ItemRequirement(StackRequirement stackRequirement) {
		this(List.of(stackRequirement));
	}

	public ItemRequirement(ItemStack stack) {
		this(new StackRequirement(stack));
	}

	public ItemRequirement(Item item) {
		this(new ItemStack(item));
	}

	public static ItemRequirement of(BlockState state, @Nullable BlockEntity be) {
		Block block = state.getBlock();
		if (state.isAir())
			return NONE;

		Item item = block.asItem();
		if (item == Items.AIR)
			return INVALID;

		// double slab needs two items
		if (state.hasProperty(BlockStateProperties.SLAB_TYPE)
			&& state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE)
			return new ItemRequirement(new ItemStack(item, 2));
		if (block instanceof TurtleEggBlock)
			return new ItemRequirement(new ItemStack(item, state.getValue(TurtleEggBlock.EGGS)));
		if (block instanceof SeaPickleBlock)
			return new ItemRequirement(new ItemStack(item, state.getValue(SeaPickleBlock.PICKLES)));
		if (block instanceof SnowLayerBlock)
			return new ItemRequirement(new ItemStack(item, state.getValue(SnowLayerBlock.LAYERS)));
		if (block instanceof CandleBlock)
			return new ItemRequirement(new ItemStack(item, state.getValue(CandleBlock.CANDLES)));
		// One item per face, like the drops (resin clumps, glow lichen, sculk veins)
		if (block instanceof MultifaceBlock)
			return new ItemRequirement(new ItemStack(item, MultifaceBlock.availableFaces(state)
				.size()));
		// Flower beds and leaf litter
		if (block instanceof SegmentableBlock segmentable)
			return new ItemRequirement(new ItemStack(item, state.getValue(segmentable.getSegmentAmountProperty())));
		if (block instanceof FarmlandBlock || block instanceof PathBlock)
			return new ItemRequirement(Items.DIRT);
		if (block instanceof AbstractBannerBlock && be instanceof BannerBlockEntity bannerBE)
			return new ItemRequirement(new StrictNbtStackRequirement(bannerBE.getItem()));
		// Tall grass doesn't exist as an item, so use 2 grass blades
		if (block == Blocks.TALL_GRASS)
			return new ItemRequirement(new ItemStack(Items.SHORT_GRASS, 2));
		// Large ferns don't exist as items, so use 2 ferns instead
		if (block == Blocks.LARGE_FERN)
			return new ItemRequirement(new ItemStack(Items.FERN, 2));

		return new ItemRequirement(item);
	}

	/**
	 * The bucket for the fluid of a schematic block, placed after all blocks: a water or lava source, or the water of a
	 * waterlogged block (see {@link BlockHelper#isFluidSource} and {@link BlockHelper#isWaterlogged}).
	 */
	public static ItemRequirement ofFluid(BlockState state) {
		Item bucket = state.getFluidState()
			.getType()
			.getBucket();
		return bucket == Items.AIR ? INVALID : new ItemRequirement(bucket);
	}

	public static ItemRequirement of(Entity entity) {
		EntityPlacement placement = EntityPlacements.find(entity);
		if (placement != null)
			return new ItemRequirement(placement.requirement(entity));

		if (entity instanceof ItemFrame itemFrame) {
			ItemStack frame = itemFrame.getFrameItemStack();
			ItemStack displayedItem = NBTProcessors.withUnsafeComponentsDiscarded(itemFrame.getItem());
			if (displayedItem.isEmpty())
				return new ItemRequirement(frame);
			return new ItemRequirement(List.of(new StackRequirement(frame),
				new StrictNbtStackRequirement(displayedItem)));
		}

		if (entity instanceof ArmorStand armorStand) {
			List<StackRequirement> requirements = new ArrayList<>();
			requirements.add(new StackRequirement(new ItemStack(Items.ARMOR_STAND)));
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				ItemStack stack = armorStand.getItemBySlot(slot);
				if (!stack.isEmpty())
					requirements.add(new StrictNbtStackRequirement(NBTProcessors.withUnsafeComponentsDiscarded(stack)));
			}
			return new ItemRequirement(requirements);
		}

		if (entity instanceof Painting)
			return new ItemRequirement(Items.PAINTING);

		return INVALID;
	}

	public boolean isEmpty() {
		return NONE == this;
	}

	public boolean isInvalid() {
		return INVALID == this;
	}

	public List<StackRequirement> getRequiredItems() {
		return requiredItems;
	}

	public static class StackRequirement {
		public final ItemStack stack;

		public StackRequirement(ItemStack stack) {
			this.stack = stack;
		}

		public boolean matches(ItemStack other) {
			return ItemStack.isSameItem(stack, other);
		}
	}

	public static class StrictNbtStackRequirement extends StackRequirement {
		public StrictNbtStackRequirement(ItemStack stack) {
			super(stack);
		}

		@Override
		public boolean matches(ItemStack other) {
			return ItemStack.isSameItemSameComponents(stack, other);
		}
	}
}
