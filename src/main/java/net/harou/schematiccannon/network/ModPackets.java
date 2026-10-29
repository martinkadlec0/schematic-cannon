package net.harou.schematiccannon.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class ModPackets {

	public static void init() {
		serverbound(ConfigureSchematicannonPacket.TYPE, ConfigureSchematicannonPacket.STREAM_CODEC);
		serverbound(InstantSchematicPacket.TYPE, InstantSchematicPacket.STREAM_CODEC);
		serverbound(SchematicPlacePacket.TYPE, SchematicPlacePacket.STREAM_CODEC);
		serverbound(SchematicSyncPacket.TYPE, SchematicSyncPacket.STREAM_CODEC);
		serverbound(SchematicUploadPacket.TYPE, SchematicUploadPacket.STREAM_CODEC);
		serverbound(SchematicDownloadRequestPacket.TYPE, SchematicDownloadRequestPacket.STREAM_CODEC);

		PayloadTypeRegistry.clientboundPlay()
			.register(ConfigSyncPacket.TYPE, ConfigSyncPacket.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
			.register(SchematicDownloadPacket.TYPE, SchematicDownloadPacket.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
			.register(SchematicCacheResetPacket.TYPE, SchematicCacheResetPacket.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay()
			.register(SchematicPlacementPacket.TYPE, SchematicPlacementPacket.STREAM_CODEC);
	}

	private static <T extends ServerboundPacket> void serverbound(CustomPacketPayload.Type<T> type,
		StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		PayloadTypeRegistry.serverboundPlay()
			.register(type, codec);
		ServerPlayNetworking.registerGlobalReceiver(type, (packet, context) -> packet.handle(context.player()));
	}

}
