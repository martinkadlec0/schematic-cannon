package net.harou.schematiccannon.schematic.source;

import java.util.Optional;
import java.util.function.Consumer;

import net.harou.schematiccannon.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Where the structure of a schematic item comes from. The item names its source with the
 * {@link net.harou.schematiccannon.registry.ModDataComponents#SCHEMATIC_SOURCE} component and keeps the source's data in
 * its own components. Register sources in {@link SchematicSources}.
 * <p>
 * Templates are only loaded on the server. Clients download them for the preview.
 */
public interface SchematicSource {

	/**
	 * Loads the structure of the stack. Returns an empty template if it cannot be loaded.
	 */
	StructureTemplate load(ServerLevel level, ItemStack stack);

	/**
	 * Identifies the structure the stack loads: stacks with the same key load the same template. Clients cache downloaded
	 * templates by it, and the preview is rebuilt when it changes.
	 */
	String cacheKey(ItemStack stack);

	/**
	 * The start of the cache keys of the schematics a data pack reload can change, e.g. of those loaded from data packs.
	 * Clients download them again after a reload; the others are kept.
	 */
	default Optional<String> reloadedCacheKeys() {
		return Optional.empty();
	}

	/**
	 * Shown in the tooltip and the screens.
	 *
	 * @param stack the schematic's components, e.g. the stack itself
	 */
	Component displayName(DataComponentGetter stack);

	/**
	 * Adds the schematic's lines to its tooltip, by default its name.
	 *
	 * @param stack the schematic's components
	 */
	default void addToTooltip(DataComponentGetter stack, Consumer<Component> tooltip) {
		tooltip.accept(displayName(stack).copy()
			.withStyle(ChatFormatting.GOLD));
	}

	/**
	 * Whether the cannon should place the blocks without updates and settle them once the print is complete.
	 */
	default boolean prefersFrozenPrint(ItemStack stack) {
		return false;
	}

	/**
	 * Checked when the cannon starts printing the deployed stack, and whenever the player moves it. Clients can check
	 * the same while the schematic is moved, see {@link net.harou.schematiccannon.client.ClientPlacementChecks}.
	 *
	 * @param box where the schematic is in the world, see
	 *            {@link net.harou.schematiccannon.schematic.SchematicItem#getWorldBounds}
	 */
	default PlacementResult validatePlacement(ServerLevel level, ItemStack stack, BoundingBox box) {
		return PlacementResult.OK;
	}

	/**
	 * Whether {@link #validatePlacement} looks at the world in the box, e.g. for structures. It then only runs once the
	 * whole box is loaded, as looking at unloaded chunks would load or even generate them; the cannon waits until then.
	 * Checks that only need the box itself (its height, its dimension) or the seed should return false, so large or far
	 * schematics can start printing in the loaded part.
	 */
	default boolean placementCheckReadsWorld(ItemStack stack) {
		return false;
	}

	/**
	 * Whether the stack can be copied in the crafting grid with an empty schematic, see
	 * {@link net.harou.schematiccannon.schematic.SchematicCloningRecipe}.
	 */
	default boolean canDuplicate(ItemStack stack) {
		return true;
	}

	/**
	 * What the cannon puts into its output slot once it is done with the stack: once it was printed, and also when it
	 * can't be printed at all (its schematic failed to load or is empty, e.g. its file is gone).
	 */
	default ItemStack afterPrinting(ItemStack stack) {
		return new ItemStack(ModItems.EMPTY_SCHEMATIC);
	}

}
