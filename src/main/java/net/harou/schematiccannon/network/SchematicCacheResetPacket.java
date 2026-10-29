package net.harou.schematiccannon.network;

import io.netty.buffer.ByteBuf;
import net.harou.schematiccannon.SchematicCannon;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Tells clients to drop downloaded schematics: one that changed (e.g. a file uploaded again under the same name), or
 * all of a source that a data pack reload can change, see
 * {@link net.harou.schematiccannon.schematic.source.SchematicSource#reloadedCacheKeys()}.
 *
 * @param key    the cache key of the changed schematic, or the start of the keys to drop
 * @param prefix whether all keys starting with the key are dropped
 */
public record SchematicCacheResetPacket(String key, boolean prefix) implements CustomPacketPayload {

	public static final Type<SchematicCacheResetPacket> TYPE = new Type<>(SchematicCannon.asResource("reset_schematic_cache"));
	public static final StreamCodec<ByteBuf, SchematicCacheResetPacket> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, SchematicCacheResetPacket::key, ByteBufCodecs.BOOL, SchematicCacheResetPacket::prefix,
		SchematicCacheResetPacket::new);

	public boolean matches(String cacheKey) {
		return prefix ? cacheKey.startsWith(key) : cacheKey.equals(key);
	}

	@Override
	public Type<SchematicCacheResetPacket> type() {
		return TYPE;
	}
}
