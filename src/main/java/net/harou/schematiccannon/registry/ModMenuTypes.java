package net.harou.schematiccannon.registry;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.cannon.SchematicannonMenu;
import net.harou.schematiccannon.foundation.MenuOpenData;
import net.harou.schematiccannon.table.SchematicTableMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;

public class ModMenuTypes {

	public static final ExtendedMenuType<SchematicannonMenu, MenuOpenData> SCHEMATICANNON = register("schematicannon",
		new ExtendedMenuType<>(SchematicannonMenu::new, MenuOpenData.STREAM_CODEC));

	public static final ExtendedMenuType<SchematicTableMenu, MenuOpenData> SCHEMATIC_TABLE = register("schematic_table",
		new ExtendedMenuType<>(SchematicTableMenu::new, MenuOpenData.STREAM_CODEC));

	private static <T extends MenuType<?>> T register(String name, T type) {
		return Registry.register(BuiltInRegistries.MENU, SchematicCannon.asResource(name), type);
	}

	public static void init() {
	}

}
