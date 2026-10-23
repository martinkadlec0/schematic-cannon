package net.harou.schematiccannon.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.harou.schematiccannon.ModConfig;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.network.SchematicUploadPacket;
import net.harou.schematiccannon.util.Lang;
import net.harou.schematiccannon.util.SchematicFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public class ClientSchematicLoader {

	public static final int PACKET_DELAY = 10;

	private final List<Component> availableSchematics;
	private final Map<String, InputStream> activeUploads;
	private int packetCycle;

	public ClientSchematicLoader() {
		availableSchematics = new ArrayList<>();
		activeUploads = new HashMap<>();
	}

	public void tick() {
		if (activeUploads.isEmpty())
			return;
		if (packetCycle-- > 0)
			return;
		packetCycle = PACKET_DELAY;

		for (String schematic : new HashSet<>(activeUploads.keySet())) {
			continueUpload(schematic);
		}
	}

	/**
	 * @return whether the upload was started
	 */
	public boolean startNewUpload(String schematic) {
		Path path = SchematicFiles.SCHEMATICS_DIR.resolve(schematic);
		if (activeUploads.containsKey(schematic))
			return false;

		if (!Files.exists(path)) {
			SchematicCannon.LOGGER.error("Missing Schematic file: {}", path);
			return false;
		}

		InputStream in;
		try {
			long size = Files.size(path);

			// Too big
			if (!validateSizeLimitation(size))
				return false;

			// Validate if the file is encoded in a GZIP compatible format
			if (!isGZIPEncoded(path)) {
				LocalPlayer player = Minecraft.getInstance().player;
				if (player != null)
					player.sendSystemMessage(Lang.translate("schematics.wrongFormat"));
				return false;
			}

			in = Files.newInputStream(path, StandardOpenOption.READ);
			activeUploads.put(schematic, in);
			ClientPlayNetworking.send(SchematicUploadPacket.begin(schematic, size));
			return true;
		} catch (IOException e) {
			SchematicCannon.LOGGER.error("Encountered an error while starting schematic upload", e);
			return false;
		}
	}

	public static boolean validateSizeLimitation(long size) {
		if (Minecraft.getInstance().hasSingleplayerServer())
			return true;
		long maxSize = ModConfig.get().maxTotalSchematicSize;
		if (size > maxSize * 1000) {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player != null) {
				player.sendSystemMessage(Lang.translate("schematics.uploadTooLarge").append(" (" + size / 1000 + " KB)."));
				player.sendSystemMessage(Lang.translate("schematics.maxAllowedSize").append(" " + maxSize + " KB"));
			}
			return false;
		}
		return true;
	}

	/**
	 * Checks if a given file is GZIP-encoded by checking its header,
	 * of which the first two bytes should contain the magic number (0x1F, 0x8B)
	 */
	private static boolean isGZIPEncoded(Path path) {
		try (InputStream in = Files.newInputStream(path)) {
			byte[] bytes = in.readNBytes(2);
			if (bytes.length != 2)
				return false;

			int byte1 = bytes[0] & 0xFF;
			int byte2 = bytes[1] & 0xFF;

			return byte1 == 0x1F && byte2 == 0x8B;
		} catch (IOException exception) {
			return false;
		}
	}

	private void continueUpload(String schematic) {
		InputStream in = activeUploads.get(schematic);
		try {
			byte[] data = in.readNBytes(ModConfig.get().maxSchematicPacketSize);
			if (data.length > 0)
				ClientPlayNetworking.send(SchematicUploadPacket.write(schematic, data));
			if (data.length < ModConfig.get().maxSchematicPacketSize) {
				ClientPlayNetworking.send(SchematicUploadPacket.finish(schematic));
				closeQuietly(activeUploads.remove(schematic));
			}
		} catch (IOException e) {
			SchematicCannon.LOGGER.error("Encountered a error while uploading schematic", e);
			closeQuietly(activeUploads.remove(schematic));
		}
	}

	/**
	 * Drops all running uploads, e.g. when leaving a server that would never receive the rest.
	 */
	public void cancelUploads() {
		activeUploads.values()
			.forEach(ClientSchematicLoader::closeQuietly);
		activeUploads.clear();
	}

	private static void closeQuietly(InputStream in) {
		if (in == null)
			return;
		try {
			in.close();
		} catch (IOException e) {
			SchematicCannon.LOGGER.warn("Failed to close schematic file", e);
		}
	}

	public void refresh() {
		SchematicFiles.createFolderIfMissing(SchematicFiles.SCHEMATICS_DIR);
		availableSchematics.clear();

		try (Stream<Path> paths = Files.list(SchematicFiles.SCHEMATICS_DIR)) {
			paths.filter(f -> !Files.isDirectory(f) && f.getFileName().toString().endsWith(".nbt"))
				.forEach(path -> availableSchematics.add(Component.literal(path.getFileName().toString())));
		} catch (IOException e) {
			SchematicCannon.LOGGER.error("Failed to refresh schematics", e);
		}

		availableSchematics.sort((aT, bT) -> {
			String a = aT.getString();
			String b = bT.getString();
			if (a.endsWith(".nbt"))
				a = a.substring(0, a.length() - 4);
			if (b.endsWith(".nbt"))
				b = b.substring(0, b.length() - 4);
			int aLength = a.length();
			int bLength = b.length();
			int minSize = Math.min(aLength, bLength);
			char aChar, bChar;
			boolean aNumber, bNumber;
			boolean asNumeric = false;
			int lastNumericCompare = 0;
			for (int i = 0; i < minSize; i++) {
				aChar = a.charAt(i);
				bChar = b.charAt(i);
				aNumber = aChar >= '0' && aChar <= '9';
				bNumber = bChar >= '0' && bChar <= '9';
				if (asNumeric)
					if (aNumber && bNumber) {
						if (lastNumericCompare == 0)
							lastNumericCompare = aChar - bChar;
					} else if (aNumber)
						return 1;
					else if (bNumber)
						return -1;
					else if (lastNumericCompare == 0) {
						if (aChar != bChar)
							return aChar - bChar;
						asNumeric = false;
					} else
						return lastNumericCompare;
				else if (aNumber && bNumber) {
					asNumeric = true;
					if (lastNumericCompare == 0)
						lastNumericCompare = aChar - bChar;
				} else if (aChar != bChar)
					return aChar - bChar;
			}
			if (asNumeric)
				if (aLength > bLength && a.charAt(bLength) >= '0' && a.charAt(bLength) <= '9') // as number
					return 1; // a has bigger size, thus b is smaller
				else if (bLength > aLength && b.charAt(aLength) >= '0' && b.charAt(aLength) <= '9') // as number
					return -1; // b has bigger size, thus a is smaller
				else if (lastNumericCompare == 0)
					return aLength - bLength;
				else
					return lastNumericCompare;
			else
				return aLength - bLength;
		});
	}

	public List<Component> getAvailableSchematics() {
		return availableSchematics;
	}

}
