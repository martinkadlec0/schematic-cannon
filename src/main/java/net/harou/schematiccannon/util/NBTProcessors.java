package net.harou.schematiccannon.util;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Strips block entity / item data that players should not be able to smuggle into the world through schematics.
 */
public final class NBTProcessors {

	private NBTProcessors() {
	}

	@Nullable
	public static CompoundTag process(BlockState state, BlockEntity blockEntity, @Nullable CompoundTag compound,
		HolderLookup.Provider registries) {
		if (compound == null)
			return null;
		if (blockEntity instanceof SpawnerBlockEntity)
			return compound;
		if (state.is(BlockTags.ALL_SIGNS))
			return processSign(compound, registries);
		if (blockEntity.getType().onlyOpCanSetNbt())
			return null;
		return compound;
	}

	/**
	 * Keeps sign text without click events. Text that can't be decoded is treated as unsafe.
	 */
	@Nullable
	private static CompoundTag processSign(CompoundTag data, HolderLookup.Provider registries) {
		for (String key : List.of("front_text", "back_text")) {
			if (!data.contains(key))
				continue;
			SignText text = data.read(key, SignText.CODEC, registries.createSerializationContext(NbtOps.INSTANCE))
				.orElse(null);
			if (text == null)
				return null;
			for (boolean filtered : new boolean[] { false, true })
				for (Component component : text.getMessages(filtered))
					if (textComponentHasClickEvent(component))
						return null;
		}
		CompoundTag copy = data.copy();
		// Lets signs run commands and resolve selectors with operator permissions
		copy.remove("allow_op_features");
		return copy;
	}

	public static boolean textComponentHasClickEvent(Component component) {
		for (Component sibling : component.getSiblings())
			if (textComponentHasClickEvent(sibling))
				return true;
		return component.getStyle()
			.getClickEvent() != null;
	}

	/**
	 * Drops the unsafe components the stack adds or removes. Default components of the item stay untouched.
	 */
	public static ItemStack withUnsafeComponentsDiscarded(ItemStack stack) {
		DataComponentPatch patch = stack.getComponentsPatch();
		if (patch.isEmpty())
			return stack;
		return new ItemStack(stack.typeHolder(), stack.getCount(), patch.forget(NBTProcessors::isUnsafeItemComponent));
	}

	public static boolean isUnsafeItemComponent(DataComponentType<?> component) {
		return !component.equals(DataComponents.ENCHANTMENTS) && !component.equals(DataComponents.POTION_CONTENTS)
			&& !component.equals(DataComponents.DAMAGE) && !component.equals(DataComponents.CUSTOM_NAME);
	}

}
