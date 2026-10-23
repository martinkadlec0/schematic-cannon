package net.harou.schematiccannon.table;

import net.harou.schematiccannon.foundation.BlockEntityMenu;
import net.harou.schematiccannon.foundation.FilteredSlot;
import net.harou.schematiccannon.foundation.MenuOpenData;
import net.harou.schematiccannon.registry.ModMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SchematicTableMenu extends BlockEntityMenu<SchematicTableBlockEntity> {

	private Slot inputSlot;
	private Slot outputSlot;

	public SchematicTableMenu(int id, Inventory inv, MenuOpenData data) {
		this(id, inv, BlockEntityMenu.<SchematicTableBlockEntity>readClient(inv, data));
	}

	public SchematicTableMenu(int id, Inventory inv, SchematicTableBlockEntity be) {
		super(ModMenuTypes.SCHEMATIC_TABLE, id, inv, be);
	}

	public boolean canWrite() {
		return inputSlot.hasItem() && !outputSlot.hasItem();
	}

	@Override
	public ItemStack quickMoveStack(Player playerIn, int index) {
		Slot clickedSlot = getSlot(index);
		if (!clickedSlot.hasItem())
			return ItemStack.EMPTY;

		ItemStack stack = clickedSlot.getItem();
		if (index < 2)
			moveItemStackTo(stack, 2, slots.size(), true);
		else
			moveItemStackTo(stack, 0, 1, false);

		clickedSlot.setChanged();
		return ItemStack.EMPTY;
	}

	@Override
	protected void addSlots() {
		inputSlot = addSlot(new FilteredSlot(contentHolder.inventory, 0, 21, 59));
		outputSlot = addSlot(new FilteredSlot(contentHolder.inventory, 1, 166, 59));

		addStandardInventorySlots(playerInventory, 38, 107);
	}

}
