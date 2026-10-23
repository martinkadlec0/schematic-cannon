package net.harou.schematiccannon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.harou.schematiccannon.network.ConfigSyncPacket;
import net.harou.schematiccannon.network.ModPackets;
import net.harou.schematiccannon.registry.ModBlockEntityTypes;
import net.harou.schematiccannon.registry.ModBlocks;
import net.harou.schematiccannon.registry.ModDataComponents;
import net.harou.schematiccannon.registry.ModItems;
import net.harou.schematiccannon.registry.ModMenuTypes;
import net.harou.schematiccannon.schematic.SchematicProcessor;
import net.harou.schematiccannon.schematic.ServerSchematicLoader;
import net.minecraft.resources.Identifier;

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
		ModPackets.init();

		ServerLifecycleEvents.SERVER_STARTING.register(server -> ModConfig.load());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SCHEMATIC_RECEIVER.shutdown());
		ServerTickEvents.END_SERVER_TICK.register(server -> SCHEMATIC_RECEIVER.tick());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sender.sendPacket(ConfigSyncPacket.fromConfig()));
	}
}
