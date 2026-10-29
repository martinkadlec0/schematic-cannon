package net.harou.schematiccannon.schematic.level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.util.NBTProcessors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.util.ProblemReporter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;

/**
 * A detached level a structure template gets placed into, used for previewing and printing schematics. Block positions
 * are stored relative to the anchor.
 */
public class SchematicLevel extends Level implements ServerLevelAccessor {

	protected final Level level;
	protected final ChunkSource chunkSource;
	protected final LevelEntityGetter<Entity> entityGetter = new EmptyLevelEntityGetter<>();

	protected Map<BlockPos, BlockState> blocks;
	protected Map<BlockPos, BlockEntity> blockEntities;
	protected List<BlockEntity> renderedBlockEntities;
	protected List<Entity> entities;
	protected BoundingBox bounds;

	public BlockPos anchor;
	public boolean renderMode;
	/** Reject entities only operators may create, used when printing for non-operators */
	public boolean filterOperatorEntities;

	public SchematicLevel(Level original) {
		this(BlockPos.ZERO, original);
	}

	public SchematicLevel(BlockPos anchor, Level original) {
		super((WritableLevelData) original.getLevelData(), original.dimension(), original.registryAccess(),
			original.dimensionTypeRegistration(), original.isClientSide(), original.isDebug(), 0, 0);
		this.level = original;
		this.chunkSource = new SchematicChunkSource(this);
		this.blocks = new HashMap<>();
		this.blockEntities = new HashMap<>();
		this.bounds = new BoundingBox(BlockPos.ZERO);
		this.anchor = anchor;
		this.entities = new ArrayList<>();
		this.renderedBlockEntities = new ArrayList<>();
	}

	public Set<BlockPos> getAllPositions() {
		return blocks.keySet();
	}

	public List<Entity> getEntityList() {
		return entities;
	}

	public BoundingBox getBounds() {
		return bounds;
	}

	public void setBounds(BoundingBox bounds) {
		this.bounds = bounds;
	}

	public Iterable<BlockEntity> getRenderedBlockEntities() {
		return renderedBlockEntities;
	}

	private static final List<String> OPERATOR_ENTITY_FLAGS =
		List.of("Invulnerable", "Glowing", "Tags", "NoGravity", "Invisible", "Fixed", "Marker", "DisabledSlots");

	/**
	 * E.g. fixed item frames or invisible armor stands could not be removed by players.
	 */
	private void removeOperatorFlags(Entity entity) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registryAccess());
		entity.saveWithoutId(output);
		CompoundTag tag = output.buildResult();
		OPERATOR_ENTITY_FLAGS.forEach(tag::remove);
		boolean silent = entity.isSilent();
		tag.putBoolean("Silent", true);
		entity.load(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), tag));
		entity.setSilent(silent);
	}

	@Override
	public boolean addFreshEntity(Entity entityIn) {
		if (filterOperatorEntities && entityIn.getType().onlyOpCanSetNbt())
			return false;
		if (filterOperatorEntities)
			removeOperatorFlags(entityIn);
		if (entityIn instanceof ItemFrame itemFrame) {
			ItemStack item = itemFrame.getItem();
			ItemStack safeItem = NBTProcessors.withUnsafeComponentsDiscarded(item);
			if (safeItem != item) {
				boolean silent = itemFrame.isSilent();
				itemFrame.setSilent(true);
				itemFrame.setItem(safeItem, false);
				itemFrame.setSilent(silent);
			}
		}
		if (entityIn instanceof ArmorStand armorStand)
			for (EquipmentSlot equipmentSlot : EquipmentSlot.values())
				armorStand.setItemSlot(equipmentSlot,
					NBTProcessors.withUnsafeComponentsDiscarded(armorStand.getItemBySlot(equipmentSlot)));

		return entities.add(entityIn);
	}

	@Override
	public BlockEntity getBlockEntity(BlockPos pos) {
		if (isOutsideBuildHeight(pos))
			return null;
		if (blockEntities.containsKey(pos))
			return blockEntities.get(pos);
		if (!blocks.containsKey(pos.subtract(anchor)))
			return null;

		BlockState blockState = getBlockState(pos);
		if (blockState.hasBlockEntity()) {
			try {
				BlockEntity blockEntity = ((EntityBlock) blockState.getBlock()).newBlockEntity(pos, blockState);
				if (blockEntity != null) {
					blockEntity.setLevel(this);
					blockEntities.put(pos, blockEntity);
					renderedBlockEntities.add(blockEntity);
				}
				return blockEntity;
			} catch (Exception e) {
				SchematicCannon.LOGGER.debug("Could not create BlockEntity of block " + blockState, e);
			}
		}
		return null;
	}

	@Override
	public BlockState getBlockState(@Nullable BlockPos globalPos) {
		BlockPos pos = globalPos.subtract(anchor);

		if (pos.getY() - bounds.minY() == -1 && !renderMode)
			return Blocks.DIRT.defaultBlockState();
		if (getBounds().isInside(pos) && blocks.containsKey(pos))
			return blocks.get(pos);
		return Blocks.AIR.defaultBlockState();
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return getBlockState(pos).getFluidState();
	}

	@Override
	public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
		return predicate.test(getBlockState(pos));
	}

	@Override
	public boolean destroyBlock(BlockPos pos, boolean dropBlock) {
		return setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
	}

	@Override
	public boolean removeBlock(BlockPos pos, boolean isMoving) {
		return setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
	}

	// BlockEntity#setBlockState has no replacement; vanilla calls it the same way when a block changes its state
	@SuppressWarnings("deprecation")
	@Override
	public boolean setBlock(BlockPos pos, BlockState state, int flags) {
		// Blocks are stored relative to the anchor, block entities by their world position
		BlockPos globalPos = pos.immutable();
		BlockPos localPos = globalPos.subtract(anchor);
		bounds = encapsulate(bounds, localPos);
		blocks.put(localPos, state);

		BlockEntity blockEntity = blockEntities.get(globalPos);
		if (blockEntity != null && !blockEntity.getType()
			.isValid(state)) {
			blockEntities.remove(globalPos);
			renderedBlockEntities.remove(blockEntity);
		} else if (blockEntity != null)
			blockEntity.setBlockState(state);

		// Creates the block entity for the new state if needed
		getBlockEntity(globalPos);
		return true;
	}

	public static BoundingBox encapsulate(BoundingBox bb, BlockPos pos) {
		return new BoundingBox(Math.min(bb.minX(), pos.getX()), Math.min(bb.minY(), pos.getY()),
			Math.min(bb.minZ(), pos.getZ()), Math.max(bb.maxX(), pos.getX()), Math.max(bb.maxY(), pos.getY()),
			Math.max(bb.maxZ(), pos.getZ()));
	}

	@Override
	public Holder<Biome> getBiome(BlockPos pos) {
		return level.registryAccess()
			.lookupOrThrow(Registries.BIOME)
			.getOrThrow(Biomes.PLAINS);
	}

	@Override
	public int getBrightness(LightLayer lightLayer, BlockPos pos) {
		return 15;
	}

	@Override
	public int getMaxLocalRawBrightness(BlockPos pos) {
		return 15;
	}

	@Override
	public ClockManager clockManager() {
		return level.clockManager();
	}

	@Override
	public int getSkyDarken() {
		return 0;
	}

	@Override
	public LevelTickAccess<Block> getBlockTicks() {
		return BlackholeTickAccess.emptyLevelList();
	}

	@Override
	public LevelTickAccess<Fluid> getFluidTicks() {
		return BlackholeTickAccess.emptyLevelList();
	}

	@Override
	public List<? extends Player> players() {
		return Collections.emptyList();
	}

	@Override
	public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
	}

	@Override
	public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
		if (level instanceof ServerLevel serverLevel)
			return serverLevel.getCurrentDifficultyAt(pos);
		return new DifficultyInstance(level.getDifficulty(), level.getOverworldClockTime(), 0, 0);
	}

	@Override
	public ServerLevel getLevel() {
		if (this.level instanceof ServerLevel serverLevel)
			return serverLevel;
		throw new IllegalStateException("Cannot use ServerLevelAccessor#getLevel in a client environment");
	}

	// Delegation to the wrapped level

	@Override
	public LevelLightEngine getLightEngine() {
		return level.getLightEngine();
	}

	@Override
	public ChunkSource getChunkSource() {
		return chunkSource;
	}

	@Override
	public TickRateManager tickRateManager() {
		return level.tickRateManager();
	}

	@Override
	public Scoreboard getScoreboard() {
		return level.getScoreboard();
	}

	@Override
	public RecipeAccess recipeAccess() {
		return level.recipeAccess();
	}

	@Override
	public Collection<EnderDragonPart> dragonParts() {
		return Collections.emptyList();
	}

	@Override
	public LevelData.RespawnData getRespawnData() {
		return level.getRespawnData();
	}

	@Override
	public void setRespawnData(LevelData.RespawnData respawnData) {
	}

	@Override
	public WorldBorder getWorldBorder() {
		return level.getWorldBorder();
	}

	@Override
	public EnvironmentAttributeSystem environmentAttributes() {
		return level.environmentAttributes();
	}

	@Override
	public int getSeaLevel() {
		return level.getSeaLevel();
	}

	@Override
	public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
		return level.getUncachedNoiseBiome(x, y, z);
	}

	@Override
	public RegistryAccess registryAccess() {
		return level.registryAccess();
	}

	@Override
	public FeatureFlagSet enabledFeatures() {
		return level.enabledFeatures();
	}

	@Override
	public String gatherChunkSourceStats() {
		return level.gatherChunkSourceStats();
	}

	@Override
	protected LevelEntityGetter<Entity> getEntities() {
		return entityGetter;
	}

	// No-ops

	@Override
	public void levelEvent(@Nullable Entity entity, int type, BlockPos pos, int data) {
	}

	@Override
	public void playSeededSound(@Nullable Entity entity, double x, double y, double z, Holder<SoundEvent> sound,
		SoundSource source, float volume, float pitch, long seed) {
	}

	@Override
	public void playSeededSound(@Nullable Entity entity, Entity source, Holder<SoundEvent> sound, SoundSource soundSource,
		float volume, float pitch, long seed) {
	}

	@Override
	public Entity getEntity(int id) {
		return null;
	}

	@Nullable
	@Override
	public MapItemSavedData getMapData(MapId mapId) {
		return null;
	}

	@Override
	public void explode(@Nullable Entity entity, @Nullable DamageSource damageSource,
		@Nullable ExplosionDamageCalculator damageCalculator, double x, double y, double z, float radius, boolean fire,
		ExplosionInteraction interaction, ParticleOptions smallParticle, ParticleOptions largeParticle,
		WeightedList<ExplosionParticleInfo> blockParticles, Holder<SoundEvent> sound) {
	}

	@Override
	public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {
	}

	@Override
	public void updateNeighbourForOutputSignal(BlockPos pos, Block block) {
	}

	@Override
	public void gameEvent(@Nullable Entity entity, Holder<GameEvent> gameEvent, Vec3 pos) {
	}

	@Override
	public void gameEvent(Holder<GameEvent> gameEvent, Vec3 pos, GameEvent.Context context) {
	}


}
