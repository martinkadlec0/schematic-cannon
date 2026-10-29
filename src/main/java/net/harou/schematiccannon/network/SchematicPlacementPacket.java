package net.harou.schematiccannon.network;

import java.util.Optional;

import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * The server's check of a synced placement, see
 * {@link net.harou.schematiccannon.schematic.source.SchematicSource#validatePlacement}. The client shows an invalid
 * placement while the schematic is being placed.
 *
 * @param key    cache key of the schematic's source, so answers for another schematic are ignored
 * @param anchor where the placement was checked, with rotation and mirror, so answers for an earlier placement (or
 *               another copy of the schematic) are ignored
 * @param reason why the placement is invalid, empty if it is valid
 */
public record SchematicPlacementPacket(String key, BlockPos anchor, Rotation rotation, Mirror mirror,
	Optional<Component> reason) implements CustomPacketPayload {

	public static final Type<SchematicPlacementPacket> TYPE = new Type<>(SchematicCannon.asResource("schematic_placement"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SchematicPlacementPacket> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, SchematicPlacementPacket::key,
			BlockPos.STREAM_CODEC, SchematicPlacementPacket::anchor,
			Rotation.STREAM_CODEC, SchematicPlacementPacket::rotation,
			ModDataComponents.MIRROR_STREAM_CODEC, SchematicPlacementPacket::mirror,
			ComponentSerialization.OPTIONAL_STREAM_CODEC, SchematicPlacementPacket::reason,
			SchematicPlacementPacket::new
		);

	@Override
	public Type<SchematicPlacementPacket> type() {
		return TYPE;
	}
}
