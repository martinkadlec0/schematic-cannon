package net.harou.schematiccannon.network;

import io.netty.buffer.ByteBuf;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.cannon.SchematicannonBlockEntity;
import net.harou.schematiccannon.cannon.SchematicannonBlockEntity.State;
import net.harou.schematiccannon.cannon.SchematicannonMenu;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.server.level.ServerPlayer;

public record ConfigureSchematicannonPacket(Option option, boolean set) implements ServerboundPacket {

	public static final Type<ConfigureSchematicannonPacket> TYPE =
		new Type<>(SchematicCannon.asResource("configure_schematicannon"));
	public static final StreamCodec<ByteBuf, ConfigureSchematicannonPacket> STREAM_CODEC = StreamCodec.composite(
		Option.STREAM_CODEC, ConfigureSchematicannonPacket::option,
		ByteBufCodecs.BOOL, ConfigureSchematicannonPacket::set,
		ConfigureSchematicannonPacket::new
	);

	@Override
	public void handle(ServerPlayer player) {
		// Spectators can open the menu, but not use it
		if (player.isSpectator() || !(player.containerMenu instanceof SchematicannonMenu menu))
			return;

		SchematicannonBlockEntity be = menu.contentHolder;
		switch (option) {
		case DONT_REPLACE, REPLACE_ANY, REPLACE_EMPTY, REPLACE_SOLID -> be.replaceMode = option.ordinal();
		case SKIP_MISSING -> be.skipMissing = set;
		case SKIP_BLOCK_ENTITIES -> be.replaceBlockEntities = set;
		case PLAY -> {
			be.autoPaused = false;
			be.state = State.RUNNING;
			be.statusMsg = "running";
		}
		case PAUSE -> {
			be.autoPaused = false;
			be.state = State.PAUSED;
			be.statusMsg = "paused";
		}
		case STOP -> {
			be.autoPaused = false;
			be.state = State.STOPPED;
			be.statusMsg = "stopped";
		}
		}

		be.sendUpdate = true;
	}

	@Override
	public Type<ConfigureSchematicannonPacket> type() {
		return TYPE;
	}

	public enum Option {
		DONT_REPLACE, REPLACE_SOLID, REPLACE_ANY, REPLACE_EMPTY, SKIP_MISSING, SKIP_BLOCK_ENTITIES, PLAY, PAUSE, STOP;

		public static final StreamCodec<ByteBuf, Option> STREAM_CODEC =
			ByteBufCodecs.idMapper(ByIdMap.continuous(Option::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO),
				Option::ordinal);
	}
}
