package net.harou.schematiccannon.foundation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Block entity base that distinguishes between data saved to disk and data synced to the client.
 */
public abstract class SyncedBlockEntity extends BlockEntity {

	// Vanilla loads client sync data through the same path as saved data, so client packets are tagged
	private static final String CLIENT_PACKET_MARKER = "schematic_cannon:client_packet";

	public SyncedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	protected abstract void write(ValueOutput output, boolean clientPacket);

	protected abstract void read(ValueInput input, boolean clientPacket);

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		write(output, false);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		read(input, input.getBooleanOr(CLIENT_PACKET_MARKER, false));
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		write(output, true);
		output.putBoolean(CLIENT_PACKET_MARKER, true);
		return output.buildResult();
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	/**
	 * Syncs the block entity to clients and marks it to be saved, since the synced data changed.
	 */
	public void sendData() {
		if (level == null || level.isClientSide())
			return;
		setChanged();
		level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 6);
	}

	/**
	 * Loads block entity data stored in a compound tag, e.g. from a schematic or a packet.
	 */
	public static void loadFromTag(BlockEntity blockEntity, CompoundTag tag, HolderLookup.Provider registries) {
		blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
	}

}
