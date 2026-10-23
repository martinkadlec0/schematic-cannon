package net.harou.schematiccannon.registry;

import java.util.function.Function;

import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.cannon.SchematicannonBlock;
import net.harou.schematiccannon.table.SchematicTableBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public class ModBlocks {

	public static final SchematicannonBlock SCHEMATICANNON = register("schematicannon", SchematicannonBlock::new,
		Block.Properties.ofFullCopy(Blocks.DISPENSER)
			.mapColor(MapColor.COLOR_GRAY));

	public static final SchematicTableBlock SCHEMATIC_TABLE = register("schematic_table", SchematicTableBlock::new,
		Block.Properties.ofFullCopy(Blocks.LECTERN)
			.mapColor(MapColor.PODZOL)
			.forceSolidOn()
			.pushReaction(PushReaction.IMMOVEABLE));

	private static <T extends Block> T register(String name, Function<Block.Properties, T> factory,
		Block.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, SchematicCannon.asResource(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
	}

}
