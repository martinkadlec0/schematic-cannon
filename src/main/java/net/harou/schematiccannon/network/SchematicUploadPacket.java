package net.harou.schematiccannon.network;

import io.netty.buffer.ByteBuf;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.table.SchematicTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

public record SchematicUploadPacket(int code, long size, String schematic, byte[] data) implements ServerboundPacket {
	public static final int BEGIN = 0;
	public static final int WRITE = 1;
	public static final int FINISH = 2;

	public static final Type<SchematicUploadPacket> TYPE = new Type<>(SchematicCannon.asResource("upload_schematic"));
	public static final StreamCodec<ByteBuf, SchematicUploadPacket> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, SchematicUploadPacket::code,
		ByteBufCodecs.VAR_LONG, SchematicUploadPacket::size,
		ByteBufCodecs.stringUtf8(256), SchematicUploadPacket::schematic,
		ByteBufCodecs.byteArray(32767), SchematicUploadPacket::data,
		SchematicUploadPacket::new
	);

	public static SchematicUploadPacket begin(String schematic, long size) {
		return new SchematicUploadPacket(BEGIN, size, schematic, new byte[0]);
	}

	public static SchematicUploadPacket write(String schematic, byte[] data) {
		return new SchematicUploadPacket(WRITE, 0, schematic, data);
	}

	public static SchematicUploadPacket finish(String schematic) {
		return new SchematicUploadPacket(FINISH, 0, schematic, new byte[0]);
	}

	@Override
	public void handle(ServerPlayer player) {
		if (code == BEGIN) {
			if (!(player.containerMenu instanceof SchematicTableMenu menu))
				return;
			BlockPos pos = menu.contentHolder.getBlockPos();
			SchematicCannon.SCHEMATIC_RECEIVER.handleNewUpload(player, schematic, size, pos);
		}
		if (code == WRITE)
			SchematicCannon.SCHEMATIC_RECEIVER.handleWriteRequest(player, schematic, data);
		if (code == FINISH)
			SchematicCannon.SCHEMATIC_RECEIVER.handleFinishedUpload(player, schematic);
	}

	@Override
	public Type<SchematicUploadPacket> type() {
		return TYPE;
	}
}
