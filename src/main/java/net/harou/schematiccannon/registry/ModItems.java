package net.harou.schematiccannon.registry;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.schematic.SchematicItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ModItems {

	public static final BlockItem SCHEMATICANNON = registerBlockItem("schematicannon", ModBlocks.SCHEMATICANNON);

	public static final BlockItem SCHEMATIC_TABLE = registerBlockItem("schematic_table", ModBlocks.SCHEMATIC_TABLE);

	public static final Item EMPTY_SCHEMATIC = register("empty_schematic", Item::new, new Item.Properties());

	public static final Item SCHEMATIC_AND_PEN =
		register("schematic_and_pen", Item::new, new Item.Properties().stacksTo(1));

	// Filled schematics do not stack: each one can have its own deployment (anchor, rotation, mirror). Letting only
	// undeployed ones stack, but not deployed ones, would be very confusing in game.
	public static final SchematicItem SCHEMATIC =
		register("schematic", SchematicItem::new, new Item.Properties().stacksTo(1));

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory,
		Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SchematicCannon.asResource(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static BlockItem registerBlockItem(String name, Block block) {
		return register(name, properties -> new BlockItem(block, properties), new Item.Properties()
			.useBlockDescriptionPrefix());
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
			.register(entries -> {
				entries.accept(SCHEMATIC_TABLE);
				entries.accept(SCHEMATICANNON);
			});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(entries -> {
				entries.accept(EMPTY_SCHEMATIC);
				entries.accept(SCHEMATIC_AND_PEN);
			});
	}

}
