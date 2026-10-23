package net.harou.schematiccannon.network;

import io.netty.buffer.ByteBuf;
import net.harou.schematiccannon.ModConfig;
import net.harou.schematiccannon.SchematicCannon;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Tells the client the server settings it needs for uploading schematics and displaying the cannon fuel.
 */
public record ConfigSyncPacket(int maxTotalSchematicSize, int maxSchematicPacketSize, int shotsPerGunpowder)
	implements CustomPacketPayload {

	public static final Type<ConfigSyncPacket> TYPE = new Type<>(SchematicCannon.asResource("config_sync"));
	public static final StreamCodec<ByteBuf, ConfigSyncPacket> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, ConfigSyncPacket::maxTotalSchematicSize,
		ByteBufCodecs.VAR_INT, ConfigSyncPacket::maxSchematicPacketSize,
		ByteBufCodecs.VAR_INT, ConfigSyncPacket::shotsPerGunpowder,
		ConfigSyncPacket::new
	);

	public static ConfigSyncPacket fromConfig() {
		ModConfig config = ModConfig.get();
		return new ConfigSyncPacket(config.maxTotalSchematicSize, config.maxSchematicPacketSize,
			config.schematicannonShotsPerGunpowder);
	}

	public void apply() {
		ModConfig config = ModConfig.get();
		config.maxTotalSchematicSize = maxTotalSchematicSize;
		config.maxSchematicPacketSize = maxSchematicPacketSize;
		config.schematicannonShotsPerGunpowder = shotsPerGunpowder;
	}

	@Override
	public Type<ConfigSyncPacket> type() {
		return TYPE;
	}
}
