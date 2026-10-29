package net.harou.schematiccannon.network;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.harou.schematiccannon.ModConfig;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.schematic.SchematicItem;
import net.harou.schematiccannon.schematic.SchematicPrinter;
import net.harou.schematiccannon.util.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.Level;

/**
 * Creative mode: print the whole schematic instantly.
 */
public record SchematicPlacePacket(ItemStack stack) implements ServerboundPacket {

	public static final Type<SchematicPlacePacket> TYPE = new Type<>(SchematicCannon.asResource("place_schematic"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SchematicPlacePacket> STREAM_CODEC =
		ItemStack.validatedStreamCodec(ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC)
			.map(SchematicPlacePacket::new, SchematicPlacePacket::stack);

	@Override
	public void handle(ServerPlayer player) {
		if (!player.isCreative())
			return;

		Level level = player.level();
		SchematicPrinter printer = new SchematicPrinter();
		printer.loadSchematic(stack, level, !player.canUseGameMasterBlocks());
		if (!printer.isLoaded() || printer.isErrored())
			return;

		boolean includeAir = ModConfig.get().creativePrintIncludesAir;
		boolean frozen = SchematicItem.isFrozenPrint(stack);
		// What was placed frozen, settled once all of it is there; not blocks that were already in place
		LongLinkedOpenHashSet placed = new LongLinkedOpenHashSet();

		while (printer.advanceCurrentPos()) {
			if (!printer.shouldPlaceCurrent(level))
				continue;

			printer.handleCurrentTarget((pos, state, blockEntity) -> {
				boolean placingAir = state.isAir();
				if (placingAir && !includeAir || state.getBlock() instanceof PistonHeadBlock)
					return;

				CompoundTag data = BlockHelper.prepareBlockEntityData(level, state, blockEntity);
				BlockHelper.placeSchematicBlock(level, state, pos, ItemStack.EMPTY, data, frozen);
				placed.add(pos.asLong());
			}, (pos, state, blockEntity) -> {
				BlockHelper.placeSchematicFluid(level, state, pos, frozen);
				placed.add(pos.asLong());
			}, (pos, entity) -> level.addFreshEntity(entity));
		}

		if (frozen)
			for (long pos : placed)
				BlockHelper.settleSchematicBlock(level, BlockPos.of(pos));
	}

	@Override
	public Type<SchematicPlacePacket> type() {
		return TYPE;
	}
}
