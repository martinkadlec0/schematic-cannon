package net.harou.schematiccannon.schematic;

import java.util.stream.LongStream;

import com.mojang.serialization.Codec;

import net.harou.schematiccannon.SchematicCannon;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Settling left over by Schematicannons that were broken before they finished it. The cannon is gone, so its level
 * keeps the positions (saved with the world, like the cannon's own queue) and the server settles them, still a limited
 * number of blocks per tick.
 */
public class OrphanedSettles extends SavedData {

	private static final Codec<OrphanedSettles> CODEC = Codec.LONG_STREAM.xmap(OrphanedSettles::new,
		settles -> settles.queue.positions());
	// Without a data fixer, which Fabric allows for mods' saved data
	private static final SavedDataType<OrphanedSettles> TYPE = new SavedDataType<>(SchematicCannon.asResource(
		"orphaned_settles"), OrphanedSettles::new, CODEC, null);

	private final SettleQueue queue = new SettleQueue();

	private OrphanedSettles() {
	}

	private OrphanedSettles(LongStream positions) {
		queue.addAll(positions);
	}

	public static void add(ServerLevel level, LongStream positions) {
		OrphanedSettles settles = level.getDataStorage()
			.computeIfAbsent(TYPE);
		settles.queue.addAll(positions);
		settles.setDirty();
	}

	public static void tick(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			OrphanedSettles settles = level.getDataStorage()
				.get(TYPE);
			if (settles == null || settles.queue.isEmpty())
				continue;
			settles.queue.tick(level);
			settles.setDirty();
		}
	}

}
