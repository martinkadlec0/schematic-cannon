package net.harou.schematiccannon.network;

import io.netty.buffer.ByteBuf;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.schematic.SchematicDownloads;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

/**
 * Asks for the structure of the schematic in the given inventory slot, so the client can show the preview. Only
 * schematics the player holds are answered.
 */
public record SchematicDownloadRequestPacket(int slot) implements ServerboundPacket {

	public static final Type<SchematicDownloadRequestPacket> TYPE =
		new Type<>(SchematicCannon.asResource("download_schematic_request"));
	public static final StreamCodec<ByteBuf, SchematicDownloadRequestPacket> STREAM_CODEC =
		ByteBufCodecs.VAR_INT.map(SchematicDownloadRequestPacket::new, SchematicDownloadRequestPacket::slot);

	@Override
	public void handle(ServerPlayer player) {
		SchematicDownloads.send(player, slot);
	}

	@Override
	public Type<SchematicDownloadRequestPacket> type() {
		return TYPE;
	}
}
