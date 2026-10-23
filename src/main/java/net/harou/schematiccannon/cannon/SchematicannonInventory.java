package net.harou.schematiccannon.cannon;

import net.harou.schematiccannon.registry.ModItems;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SchematicannonInventory extends SimpleContainer {
	public static final int SCHEMATIC_INPUT = 0;
	public static final int SCHEMATIC_OUTPUT = 1;
	public static final int BOOK_INPUT = 2;
	public static final int BOOK_OUTPUT = 3;
	public static final int GUNPOWDER = 4;

	private final SchematicannonBlockEntity blockEntity;

	public SchematicannonInventory(SchematicannonBlockEntity blockEntity) {
		super(5);
		this.blockEntity = blockEntity;
	}

	@Override
	public void setChanged() {
		super.setChanged();
		blockEntity.setChanged();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return switch (slot) {
		case SCHEMATIC_INPUT -> stack.is(ModItems.SCHEMATIC);
		case BOOK_INPUT -> stack.is(Items.BOOK) || stack.is(Items.WRITTEN_BOOK);
		case GUNPOWDER -> stack.is(Items.GUNPOWDER);
		default -> false;
		};
	}
}
