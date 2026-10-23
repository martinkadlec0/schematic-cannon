package net.harou.schematiccannon.foundation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Sent to the client when a block entity menu opens, so the client block entity is up to date.
 */
public record MenuOpenData(BlockPos pos, CompoundTag data) {
	public static final StreamCodec<RegistryFriendlyByteBuf, MenuOpenData> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, MenuOpenData::pos,
		ByteBufCodecs.COMPOUND_TAG, MenuOpenData::data,
		MenuOpenData::new
	);

	public static MenuOpenData of(SyncedBlockEntity be) {
		return new MenuOpenData(be.getBlockPos(), be.getUpdateTag(be.getLevel()
			.registryAccess()));
	}
}
