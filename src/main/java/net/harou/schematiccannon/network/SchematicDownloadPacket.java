package net.harou.schematiccannon.network;

import io.netty.buffer.ByteBuf;
import net.harou.schematiccannon.SchematicCannon;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * One part of a downloaded schematic: the compressed template NBT, split into parts that fit into a packet. A total of
 * 0 means the server could not load the schematic.
 */
public record SchematicDownloadPacket(String key, int part, int total, byte[] data) implements CustomPacketPayload {

	public static final Type<SchematicDownloadPacket> TYPE = new Type<>(SchematicCannon.asResource("download_schematic"));
	public static final StreamCodec<ByteBuf, SchematicDownloadPacket> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, SchematicDownloadPacket::key,
		ByteBufCodecs.VAR_INT, SchematicDownloadPacket::part,
		ByteBufCodecs.VAR_INT, SchematicDownloadPacket::total,
		ByteBufCodecs.BYTE_ARRAY, SchematicDownloadPacket::data,
		SchematicDownloadPacket::new
	);

	@Override
	public Type<SchematicDownloadPacket> type() {
		return TYPE;
	}
}
