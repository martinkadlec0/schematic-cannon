package net.harou.schematiccannon.foundation;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public abstract class BlockEntityMenu<T extends SyncedBlockEntity> extends AbstractContainerMenu {
	public final Inventory playerInventory;
	public final T contentHolder;

	protected BlockEntityMenu(MenuType<?> type, int id, Inventory inv, T contentHolder) {
		super(type, id);
		this.playerInventory = inv;
		this.contentHolder = contentHolder;
		addSlots();
	}

	/**
	 * Finds the block entity on the client and applies the data the server sent along with the menu.
	 */
	@SuppressWarnings("unchecked")
	protected static <T extends SyncedBlockEntity> T readClient(Inventory inv, MenuOpenData data) {
		T be = (T) inv.player.level()
			.getBlockEntity(data.pos());
		SyncedBlockEntity.loadFromTag(be, data.data(), inv.player.registryAccess());
		return be;
	}

	protected abstract void addSlots();

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(contentHolder, player);
	}

}
