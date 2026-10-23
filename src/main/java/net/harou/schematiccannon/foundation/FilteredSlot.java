package net.harou.schematiccannon.foundation;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Slot that only accepts what {@link Container#canPlaceItem} allows.
 */
public class FilteredSlot extends Slot {

	public FilteredSlot(Container container, int slot, int x, int y) {
		super(container, slot, x, y);
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return container.canPlaceItem(getContainerSlot(), stack);
	}

}
