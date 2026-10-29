package net.harou.schematiccannon.schematic;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Lets the cannon print entities of a schematic from an item, e.g. a mob stored in an item. Register in
 * {@link EntityPlacements}.
 */
public interface EntityPlacement {

	/**
	 * Whether this placement prints the given schematic entity.
	 */
	boolean appliesTo(Entity schematicEntity);

	/**
	 * The item the cannon takes from its inventories for the entity. Its stack is what the material checklist shows.
	 */
	ItemRequirement.StackRequirement requirement(Entity schematicEntity);

	/**
	 * Creates the entity to place from the item the cannon took. It must have the position and rotation of the schematic
	 * entity. Returns null if the item cannot be used, the cannon then puts it back.
	 *
	 * @param consumed everything the cannon took for the {@link #requirement}, as one stack (with the components of the
	 *                 first stack it took from, if it took from several)
	 */
	@Nullable
	Entity create(ServerLevel level, ItemStack consumed, Entity schematicEntity);

	/**
	 * What is left of the item after use. The cannon puts it into its inventories.
	 */
	default ItemStack remainder(ItemStack consumed) {
		return ItemStack.EMPTY;
	}

}
