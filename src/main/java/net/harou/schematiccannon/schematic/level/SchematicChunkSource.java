package net.harou.schematiccannon.schematic.level;

import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;

/**
 * The schematic level stores its blocks itself, so every chunk lookup gets the same empty chunk.
 */
public class SchematicChunkSource extends ChunkSource {
	private final Level level;
	@Nullable
	private EmptyLevelChunk emptyChunk;

	public SchematicChunkSource(Level level) {
		this.level = level;
	}

	@Override
	public LightChunk getChunkForLighting(int x, int z) {
		return getChunk(x, z);
	}

	@Override
	public Level getLevel() {
		return level;
	}

	@Override
	public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean create) {
		return getChunk(x, z);
	}

	public ChunkAccess getChunk(int x, int z) {
		if (emptyChunk == null)
			emptyChunk = new EmptyLevelChunk(level, ChunkPos.ZERO, level.getBiome(BlockPos.ZERO));
		return emptyChunk;
	}

	@Override
	public String gatherStats() {
		return "SchematicChunkSource";
	}

	@Override
	public LevelLightEngine getLightEngine() {
		return level.getLightEngine();
	}

	@Override
	public void tick(BooleanSupplier hasTimeLeft, boolean tickChunks) {
	}

	@Override
	public int getLoadedChunksCount() {
		return 0;
	}
}
