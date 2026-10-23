package net.harou.schematiccannon.cannon;

import net.harou.schematiccannon.foundation.BlockEntityMenu;
import net.harou.schematiccannon.foundation.FilteredSlot;
import net.harou.schematiccannon.foundation.MenuOpenData;
import net.harou.schematiccannon.registry.ModMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SchematicannonMenu extends BlockEntityMenu<SchematicannonBlockEntity> {

	public SchematicannonMenu(int id, Inventory inv, MenuOpenData data) {
		this(id, inv, BlockEntityMenu.<SchematicannonBlockEntity>readClient(inv, data));
	}

	public SchematicannonMenu(int id, Inventory inv, SchematicannonBlockEntity be) {
		super(ModMenuTypes.SCHEMATICANNON, id, inv, be);
	}

	@Override
	protected void addSlots() {
		SchematicannonInventory inventory = contentHolder.inventory;
		addSlot(new FilteredSlot(inventory, SchematicannonInventory.SCHEMATIC_INPUT, 15, 65));
		addSlot(new FilteredSlot(inventory, SchematicannonInventory.SCHEMATIC_OUTPUT, 171, 65));
		addSlot(new FilteredSlot(inventory, SchematicannonInventory.BOOK_INPUT, 134, 19));
		addSlot(new FilteredSlot(inventory, SchematicannonInventory.BOOK_OUTPUT, 174, 19));
		addSlot(new FilteredSlot(inventory, SchematicannonInventory.GUNPOWDER, 15, 19));

		addStandardInventorySlots(playerInventory, 37, 161);
	}

	@Override
	public ItemStack quickMoveStack(Player playerIn, int index) {
		Slot clickedSlot = getSlot(index);
		if (!clickedSlot.hasItem())
			return ItemStack.EMPTY;
		ItemStack stack = clickedSlot.getItem();

		if (index < 5)
			moveItemStackTo(stack, 5, slots.size(), true);
		else if (!moveItemStackTo(stack, 0, 1, false) && !moveItemStackTo(stack, 2, 3, false))
			moveItemStackTo(stack, 4, 5, false);

		clickedSlot.setChanged();
		return ItemStack.EMPTY;
	}

}
