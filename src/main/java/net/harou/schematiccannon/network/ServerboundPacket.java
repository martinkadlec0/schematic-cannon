package net.harou.schematiccannon.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public interface ServerboundPacket extends CustomPacketPayload {

	/**
	 * Called on the server thread.
	 */
	void handle(ServerPlayer player);

}
