package net.harou.schematiccannon.network;

import io.netty.buffer.ByteBuf;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.registry.ModDataComponents;
import net.harou.schematiccannon.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

public record SchematicSyncPacket(int slot, boolean deployed, BlockPos anchor, Rotation rotation, Mirror mirror)
	implements ServerboundPacket {

	public static final Type<SchematicSyncPacket> TYPE = new Type<>(SchematicCannon.asResource("sync_schematic"));
	public static final StreamCodec<ByteBuf, SchematicSyncPacket> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, SchematicSyncPacket::slot,
		ByteBufCodecs.BOOL, SchematicSyncPacket::deployed,
		BlockPos.STREAM_CODEC, SchematicSyncPacket::anchor,
		Rotation.STREAM_CODEC, SchematicSyncPacket::rotation,
		ModDataComponents.MIRROR_STREAM_CODEC, SchematicSyncPacket::mirror,
		SchematicSyncPacket::new
	);

	public SchematicSyncPacket(int slot, StructurePlaceSettings settings, BlockPos anchor, boolean deployed) {
		this(slot, deployed, anchor, settings.getRotation(), settings.getMirror());
	}

	@Override
	public void handle(ServerPlayer player) {
		if (slot < -1 || slot >= player.getInventory()
			.getContainerSize())
			return;
		ItemStack stack = slot == -1 ? player.getMainHandItem()
			: player.getInventory()
				.getItem(slot);
		if (!stack.is(ModItems.SCHEMATIC))
			return;
		stack.set(ModDataComponents.SCHEMATIC_DEPLOYED, deployed);
		stack.set(ModDataComponents.SCHEMATIC_ANCHOR, anchor);
		stack.set(ModDataComponents.SCHEMATIC_ROTATION, rotation);
		stack.set(ModDataComponents.SCHEMATIC_MIRROR, mirror);
	}

	@Override
	public Type<SchematicSyncPacket> type() {
		return TYPE;
	}
}
