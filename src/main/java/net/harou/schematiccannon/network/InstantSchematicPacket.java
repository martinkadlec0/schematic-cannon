package net.harou.schematiccannon.network;

import io.netty.buffer.ByteBuf;
import net.harou.schematiccannon.SchematicCannon;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

/**
 * Schematic and Pen: save the selected area on the server and give the player the schematic right away.
 */
public record InstantSchematicPacket(String name, BlockPos origin, BlockPos bounds) implements ServerboundPacket {

	public static final Type<InstantSchematicPacket> TYPE = new Type<>(SchematicCannon.asResource("instant_schematic"));
	public static final StreamCodec<ByteBuf, InstantSchematicPacket> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, InstantSchematicPacket::name,
		BlockPos.STREAM_CODEC, InstantSchematicPacket::origin,
		BlockPos.STREAM_CODEC, InstantSchematicPacket::bounds,
		InstantSchematicPacket::new
	);

	@Override
	public void handle(ServerPlayer player) {
		SchematicCannon.SCHEMATIC_RECEIVER.handleInstantSchematic(player, name, player.level(), origin, bounds);
	}

	@Override
	public Type<InstantSchematicPacket> type() {
		return TYPE;
	}
}
