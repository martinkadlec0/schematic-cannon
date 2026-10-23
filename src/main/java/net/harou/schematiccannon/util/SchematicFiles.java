package net.harou.schematiccannon.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import net.fabricmc.loader.api.FabricLoader;
import net.harou.schematiccannon.SchematicCannon;

public class SchematicFiles {
	// Absolute, so anything resolved against these paths is absolute as well. Same folders as Create uses.
	public static final Path SCHEMATICS_DIR = FabricLoader.getInstance()
		.getGameDir()
		.toAbsolutePath()
		.normalize()
		.resolve("schematics");
	public static final Path UPLOADED_SCHEMATICS_DIR = SCHEMATICS_DIR.resolve("uploaded");

	public static void createFolderIfMissing(Path path) {
		try {
			Files.createDirectories(path);
		} catch (IOException e) {
			SchematicCannon.LOGGER.warn("Could not create Folder: {}", path);
		}
	}

	public static String findFirstValidFilename(String name, Path folderPath, String extension) {
		int index = 0;
		String filename;
		do {
			filename = slug(name) + ((index == 0) ? "" : "_" + index) + "." + extension;
			index++;
		} while (Files.exists(folderPath.resolve(filename)));
		return filename;
	}

	public static String slug(String name) {
		return name.toLowerCase(Locale.ROOT)
			.replaceAll("\\W+", "_");
	}

	private SchematicFiles() {
	}
}
