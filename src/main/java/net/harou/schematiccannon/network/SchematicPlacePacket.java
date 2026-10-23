package net.harou.schematiccannon.network;

import net.harou.schematiccannon.ModConfig;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.schematic.SchematicPrinter;
import net.harou.schematiccannon.util.BlockHelper;
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

		while (printer.advanceCurrentPos()) {
			if (!printer.shouldPlaceCurrent(level))
				continue;

			printer.handleCurrentTarget((pos, state, blockEntity) -> {
				boolean placingAir = state.isAir();
				if (placingAir && !includeAir || state.getBlock() instanceof PistonHeadBlock)
					return;

				CompoundTag data = BlockHelper.prepareBlockEntityData(level, state, blockEntity);
				BlockHelper.placeSchematicBlock(level, state, pos, ItemStack.EMPTY, data);
			}, (pos, entity) -> level.addFreshEntity(entity));
		}
	}

	@Override
	public Type<SchematicPlacePacket> type() {
		return TYPE;
	}
}
