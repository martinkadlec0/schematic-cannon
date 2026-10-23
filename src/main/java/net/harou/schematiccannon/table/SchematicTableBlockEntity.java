package net.harou.schematiccannon.table;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.harou.schematiccannon.foundation.MenuOpenData;
import net.harou.schematiccannon.foundation.SyncedBlockEntity;
import net.harou.schematiccannon.registry.ModBlockEntityTypes;
import net.harou.schematiccannon.registry.ModItems;
import net.harou.schematiccannon.util.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class SchematicTableBlockEntity extends SyncedBlockEntity
	implements ExtendedMenuProvider<MenuOpenData>, Clearable {

	public SchematicTableInventory inventory;
	public boolean isUploading;
	public String uploadingSchematic;
	public float uploadingProgress;
	public boolean sendUpdate;

	public class SchematicTableInventory extends SimpleContainer {
		public SchematicTableInventory() {
			super(2);
		}

		@Override
		public void setChanged() {
			super.setChanged();
			SchematicTableBlockEntity.this.setChanged();
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return slot == 0 && (stack.is(ModItems.EMPTY_SCHEMATIC) || stack.is(ModItems.SCHEMATIC_AND_PEN)
				|| stack.is(ModItems.SCHEMATIC));
		}
	}

	public SchematicTableBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntityTypes.SCHEMATIC_TABLE, pos, state);
		inventory = new SchematicTableInventory();
	}

	@Override
	protected void read(ValueInput input, boolean clientPacket) {
		NonNullList<ItemStack> items = NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input.childOrEmpty("Inventory"), items);
		for (int i = 0; i < items.size(); i++)
			inventory.setItem(i, items.get(i));

		if (!clientPacket)
			return;
		if (input.getBooleanOr("Uploading", false)) {
			isUploading = true;
			uploadingSchematic = input.getStringOr("Schematic", "");
			uploadingProgress = input.getFloatOr("Progress", 0);
		} else {
			isUploading = false;
			uploadingSchematic = null;
			uploadingProgress = 0;
		}
	}

	@Override
	protected void write(ValueOutput output, boolean clientPacket) {
		NonNullList<ItemStack> items = NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
		for (int i = 0; i < items.size(); i++)
			items.set(i, inventory.getItem(i));
		ContainerHelper.saveAllItems(output.child("Inventory"), items);

		if (clientPacket && isUploading) {
			output.putBoolean("Uploading", true);
			output.putString("Schematic", uploadingSchematic);
			output.putFloat("Progress", uploadingProgress);
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null)
			Containers.dropContents(level, pos, inventory);
	}

	@Override
	public void clearContent() {
		inventory.clearContent();
	}

	public void tick() {
		// Update Client block entity
		if (sendUpdate) {
			sendUpdate = false;
			sendData();
		}
	}

	public void startUpload(String schematic) {
		isUploading = true;
		uploadingProgress = 0;
		uploadingSchematic = schematic;
		sendUpdate = true;
		// Empty schematics stack, the others are single items
		inventory.removeItem(0, 1);
	}

	public void finishUpload() {
		isUploading = false;
		uploadingProgress = 0;
		uploadingSchematic = null;
		sendUpdate = true;
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
		return new SchematicTableMenu(id, inv, this);
	}

	@Override
	public MenuOpenData getScreenOpeningData(ServerPlayer player) {
		return MenuOpenData.of(this);
	}

	@Override
	public Component getDisplayName() {
		return Lang.translate("gui.schematicTable.title");
	}
}
