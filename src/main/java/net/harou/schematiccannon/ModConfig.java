package net.harou.schematiccannon;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

/**
 * Settings stored in config/schematic_cannon.json. Server values are the defaults Create uses.
 */
public class ModConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
		.create();
	private static final Path FILE = FabricLoader.getInstance()
		.getConfigDir()
		.resolve(SchematicCannon.ID + ".json");

	private static ModConfig instance = new ModConfig();

	// Server

	/** Whether placing a Schematic directly in Creative Mode should replace world blocks with Air */
	public boolean creativePrintIncludesAir = false;
	/**
	 * Place blocks without updates and settle them once the print is complete, so half-built redstone does not fire.
	 * Schematics can also ask for this themselves.
	 */
	public boolean alwaysFrozenPrint = false;
	/** The amount of Schematics a player can upload until previous ones are overwritten. */
	public int maxSchematics = 10;
	/** The maximum allowed file size of uploaded Schematics. [in KiloBytes] */
	public int maxTotalSchematicSize = 256;
	/** The maximum packet size uploaded Schematics are split into. [in Bytes] */
	public int maxSchematicPacketSize = 1024;
	/** Amount of game ticks without new packets arriving until an active schematic upload process is discarded. */
	public int schematicIdleTimeout = 600;
	/** Amount of game ticks between shots of the cannon. Higher => Slower */
	public int schematicannonDelay = 10;
	/** Amount of blocks a Schematicannon can print per Gunpowder item provided. */
	public int schematicannonShotsPerGunpowder = 400;

	// Client

	/** Show the summary and usage hints in item tooltips (hold Shift) */
	public boolean enableTooltips = true;
	/** Animate moving, rotating and flipping schematics and selections; otherwise they jump block by block */
	public boolean smoothAnimations = true;

	/**
	 * The settings in use. On a client connected to a remote server, the server values come from that server.
	 */
	public static ModConfig get() {
		return instance;
	}

	public static void set(ModConfig config) {
		instance = config;
	}

	public static void load() {
		instance = readFromFile();
		save(instance);
	}

	/**
	 * Reads the settings from the config file, or returns defaults if it is missing or broken.
	 */
	public static ModConfig readFromFile() {
		ModConfig config = null;
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				config = GSON.fromJson(reader, ModConfig.class);
			} catch (Exception e) {
				SchematicCannon.LOGGER.error("Failed to read config, using defaults", e);
			}
		}
		if (config == null)
			config = new ModConfig();
		config.clamp();
		return config;
	}

	public static void save(ModConfig config) {
		try (Writer writer = Files.newBufferedWriter(FILE)) {
			GSON.toJson(config, writer);
		} catch (IOException e) {
			SchematicCannon.LOGGER.error("Failed to write config", e);
		}
	}

	public void clamp() {
		maxSchematics = Math.max(1, maxSchematics);
		maxTotalSchematicSize = Math.max(16, maxTotalSchematicSize);
		maxSchematicPacketSize = Mth.clamp(maxSchematicPacketSize, 256, 31000);
		schematicIdleTimeout = Math.max(100, schematicIdleTimeout);
		schematicannonDelay = Math.max(1, schematicannonDelay);
		schematicannonShotsPerGunpowder = Math.max(1, schematicannonShotsPerGunpowder);
	}

}
