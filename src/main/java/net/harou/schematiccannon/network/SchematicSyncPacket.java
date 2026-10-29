package net.harou.schematiccannon.network;

import java.util.Optional;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.registry.ModDataComponents;
import net.harou.schematiccannon.schematic.SchematicItem;
import net.harou.schematiccannon.schematic.source.PlacementResult;
import net.harou.schematiccannon.schematic.source.SchematicSource;
import net.harou.schematiccannon.util.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * @param syncId stored with the placement, see {@link ModDataComponents#SCHEMATIC_SYNC_ID}
 */
public record SchematicSyncPacket(int slot, boolean deployed, BlockPos anchor, Rotation rotation, Mirror mirror, int syncId)
	implements ServerboundPacket {

	public static final Type<SchematicSyncPacket> TYPE = new Type<>(SchematicCannon.asResource("sync_schematic"));
	public static final StreamCodec<ByteBuf, SchematicSyncPacket> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, SchematicSyncPacket::slot,
		ByteBufCodecs.BOOL, SchematicSyncPacket::deployed,
		BlockPos.STREAM_CODEC, SchematicSyncPacket::anchor,
		Rotation.STREAM_CODEC, SchematicSyncPacket::rotation,
		ModDataComponents.MIRROR_STREAM_CODEC, SchematicSyncPacket::mirror,
		ByteBufCodecs.INT, SchematicSyncPacket::syncId,
		SchematicSyncPacket::new
	);

	public SchematicSyncPacket(int slot, StructurePlaceSettings settings, BlockPos anchor, boolean deployed, int syncId) {
		this(slot, deployed, anchor, settings.getRotation(), settings.getMirror(), syncId);
	}

	@Override
	public void handle(ServerPlayer player) {
		ItemStack stack = SchematicItem.getInSlot(player, slot);
		if (!(stack.getItem() instanceof SchematicItem))
			return;
		stack.set(ModDataComponents.SCHEMATIC_DEPLOYED, deployed);
		stack.set(ModDataComponents.SCHEMATIC_ANCHOR, anchor);
		stack.set(ModDataComponents.SCHEMATIC_ROTATION, rotation);
		stack.set(ModDataComponents.SCHEMATIC_MIRROR, mirror);
		stack.set(ModDataComponents.SCHEMATIC_SYNC_ID, syncId);

		// Tell the player right away if the cannon would refuse this placement
		if (deployed && SchematicItem.isValid(stack)) {
			SchematicSource source = SchematicItem.getSource(stack);
			BoundingBox box = SchematicItem.getWorldBounds(anchor, rotation, mirror,
				stack.get(ModDataComponents.SCHEMATIC_BOUNDS));
			// A check that looks at the world only where it is loaded: the anchor comes from the client, and it could load
			// or generate chunks anywhere. Unchecked, there is no answer (not "valid"); the cannon checks before printing.
			if (source.placementCheckReadsWorld(stack) && !BlockHelper.isLoaded(player.level(), box))
				return;
			PlacementResult result = source.validatePlacement(player.level(), stack, box);
			ServerPlayNetworking.send(player, new SchematicPlacementPacket(source.cacheKey(stack), anchor, rotation, mirror,
				Optional.ofNullable(result.reason())));
		}
	}

	@Override
	public Type<SchematicSyncPacket> type() {
		return TYPE;
	}
}
