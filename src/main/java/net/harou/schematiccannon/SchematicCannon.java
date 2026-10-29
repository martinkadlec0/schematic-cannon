package net.harou.schematiccannon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.harou.schematiccannon.network.ConfigSyncPacket;
import net.harou.schematiccannon.network.ModPackets;
import net.harou.schematiccannon.network.SchematicCacheResetPacket;
import net.harou.schematiccannon.registry.ModBlockEntityTypes;
import net.harou.schematiccannon.registry.ModBlocks;
import net.harou.schematiccannon.registry.ModDataComponents;
import net.harou.schematiccannon.registry.ModItems;
import net.harou.schematiccannon.registry.ModMenuTypes;
import net.harou.schematiccannon.schematic.OrphanedSettles;
import net.harou.schematiccannon.schematic.SchematicCloningRecipe;
import net.harou.schematiccannon.schematic.SchematicProcessor;
import net.harou.schematiccannon.schematic.ServerSchematicLoader;
import net.harou.schematiccannon.schematic.source.SchematicSources;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public class SchematicCannon implements ModInitializer {
	public static final String ID = "schematic_cannon";
	public static final Logger LOGGER = LoggerFactory.getLogger(ID);

	public static final ServerSchematicLoader SCHEMATIC_RECEIVER = new ServerSchematicLoader();

	public static Identifier asResource(String path) {
		return Identifier.fromNamespaceAndPath(ID, path);
	}

	@Override
	public void onInitialize() {
		ModConfig.load();

		ModDataComponents.init();
		ModBlocks.init();
		ModItems.init();
		ModBlockEntityTypes.init();
		ModMenuTypes.init();
		SchematicProcessor.init();
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, asResource("crafting_special_schematic_cloning"),
			SchematicCloningRecipe.SERIALIZER);
		ModPackets.init();

		ServerLifecycleEvents.SERVER_STARTING.register(server -> ModConfig.load());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SCHEMATIC_RECEIVER.shutdown());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			SCHEMATIC_RECEIVER.tick();
			OrphanedSettles.tick(server);
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sender.sendPacket(ConfigSyncPacket.fromConfig()));
		// Sources may load from data packs, so their downloaded templates can be outdated after a reload
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			// A failed reload changed nothing, so the downloaded schematics are still right
			if (!success)
				return;
			for (String prefix : SchematicSources.reloadedCacheKeys())
				for (ServerPlayer player : PlayerLookup.all(server))
					ServerPlayNetworking.send(player, new SchematicCacheResetPacket(prefix, true));
		});
	}
}
