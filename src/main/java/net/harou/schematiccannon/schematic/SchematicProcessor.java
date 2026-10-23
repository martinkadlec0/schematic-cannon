package net.harou.schematiccannon.schematic;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.util.NBTProcessors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Removes unsafe block entity data. Operator-only entities are filtered by {@link net.harou.schematiccannon.schematic.level.SchematicLevel}.
 */
public class SchematicProcessor implements StructureProcessor {
	public static final SchematicProcessor INSTANCE = new SchematicProcessor();
	public static final MapCodec<SchematicProcessor> CODEC = MapCodec.unit(() -> INSTANCE);
	private SchematicProcessor() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR, SchematicCannon.asResource("schematic"), CODEC);
	}

	@Nullable
	@Override
	public StructureTemplate.StructureBlockInfo processBlock(LevelReader world, BlockPos pos, BlockPos referencePos,
		BlockPos templateRelativePos, StructureTemplate.StructureBlockInfo info,
		StructurePlaceSettings settings) {
		if (info.nbt() != null && info.state()
			.hasBlockEntity()) {
			BlockEntity be = ((EntityBlock) info.state()
				.getBlock()).newBlockEntity(info.pos(), info.state());
			if (be != null) {
				CompoundTag nbt = NBTProcessors.process(info.state(), be, info.nbt(), world.registryAccess());
				if (nbt != info.nbt())
					return new StructureTemplate.StructureBlockInfo(info.pos(), info.state(), nbt);
			}
		}
		return info;
	}

	@Override
	public MapCodec<SchematicProcessor> codec() {
		return CODEC;
	}
}
