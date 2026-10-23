package net.harou.schematiccannon.registry;

import java.util.Set;

import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.cannon.SchematicannonBlockEntity;
import net.harou.schematiccannon.table.SchematicTableBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModBlockEntityTypes {

	public static final BlockEntityType<SchematicannonBlockEntity> SCHEMATICANNON = register("schematicannon",
		new BlockEntityType<>(SchematicannonBlockEntity::new, Set.of(ModBlocks.SCHEMATICANNON)));

	public static final BlockEntityType<SchematicTableBlockEntity> SCHEMATIC_TABLE = register("schematic_table",
		new BlockEntityType<>(SchematicTableBlockEntity::new, Set.of(ModBlocks.SCHEMATIC_TABLE)));

	private static <T extends BlockEntityType<?>> T register(String name, T type) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, SchematicCannon.asResource(name), type);
	}

	public static void init() {
	}

}
