package net.harou.schematiccannon.schematic;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.harou.schematiccannon.registry.ModDataComponents;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * A filled schematic and an empty one make a copy, like book cloning: the original stays in the grid. Works for every
 * {@link SchematicItem} whose source allows it, see
 * {@link net.harou.schematiccannon.schematic.source.SchematicSource#canDuplicate}.
 */
public class SchematicCloningRecipe extends CustomRecipe {

	public static final MapCodec<SchematicCloningRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Ingredient.CODEC.fieldOf("material")
			.forGetter(recipe -> recipe.material)
	).apply(i, SchematicCloningRecipe::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, SchematicCloningRecipe> STREAM_CODEC =
		Ingredient.CONTENTS_STREAM_CODEC.map(SchematicCloningRecipe::new, recipe -> recipe.material);
	public static final RecipeSerializer<SchematicCloningRecipe> SERIALIZER =
		new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	/** What the copy is made from, the empty schematic */
	private final Ingredient material;

	public SchematicCloningRecipe(Ingredient material) {
		this.material = material;
	}

	private static boolean isSource(ItemStack stack) {
		return SchematicItem.isValid(stack) && SchematicItem.getSource(stack)
			.canDuplicate(stack);
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		// Filled schematics do not stack, so one copy at a time
		if (input.ingredientCount() != 2)
			return false;
		boolean hasSource = false;
		boolean hasMaterial = false;
		for (int slot = 0; slot < input.size(); slot++) {
			ItemStack stack = input.getItem(slot);
			if (stack.isEmpty())
				continue;
			if (isSource(stack) && !hasSource)
				hasSource = true;
			else if (material.test(stack) && !hasMaterial)
				hasMaterial = true;
			else
				return false;
		}
		return hasSource && hasMaterial;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		for (int slot = 0; slot < input.size(); slot++) {
			ItemStack stack = input.getItem(slot);
			if (isSource(stack)) {
				// The copy starts undeployed, the original may stay deployed where it is
				ItemStack copy = stack.copyWithCount(1);
				copy.set(ModDataComponents.SCHEMATIC_DEPLOYED, false);
				// Not moved by the client's syncs of the original
				copy.remove(ModDataComponents.SCHEMATIC_SYNC_ID);
				return copy;
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
		for (int slot = 0; slot < input.size(); slot++)
			if (isSource(input.getItem(slot))) {
				remaining.set(slot, input.getItem(slot)
					.copyWithCount(1));
				break;
			}
		return remaining;
	}

	@Override
	public RecipeSerializer<SchematicCloningRecipe> getSerializer() {
		return SERIALIZER;
	}

}
