package net.harou.schematiccannon.schematic;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import java.util.stream.Stream;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.harou.schematiccannon.ModConfig;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.registry.ModBlocks;
import net.harou.schematiccannon.registry.ModItems;
import net.harou.schematiccannon.schematic.SchematicExport.SchematicExportResult;
import net.harou.schematiccannon.schematic.source.FileSchematicSource;
import net.harou.schematiccannon.table.SchematicTableBlockEntity;
import net.harou.schematiccannon.util.BlockHelper;
import net.harou.schematiccannon.util.Lang;
import net.harou.schematiccannon.util.SchematicFiles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class ServerSchematicLoader {

	private final Map<String, SchematicUploadEntry> activeUploads;

	public static class SchematicUploadEntry {
		public Level world;
		public BlockPos tablePos;
		public OutputStream stream;
		/** The file being written; it replaces the uploaded file once complete, see {@link #partFile} */
		public Path partFile;
		public long bytesUploaded;
		public long totalBytes;
		public int idleTime;

		public SchematicUploadEntry(OutputStream stream, long totalBytes, Level world, BlockPos tablePos) {
			this.stream = stream;
			this.totalBytes = totalBytes;
			this.tablePos = tablePos;
			this.world = world;
			this.bytesUploaded = 0;
			this.idleTime = 0;
		}
	}

	public ServerSchematicLoader() {
		activeUploads = new HashMap<>();
	}

	private final ObjectArrayList<String> deadEntries = ObjectArrayList.of();

	public void tick() {
		// Detect Timed out Uploads
		int timeout = ModConfig.get().schematicIdleTimeout;
		for (String upload : activeUploads.keySet()) {
			SchematicUploadEntry entry = activeUploads.get(upload);

			if (entry.idleTime++ > timeout) {
				SchematicCannon.LOGGER.warn("Schematic Upload timed out: " + upload);
				deadEntries.add(upload);
			}
		}

		// Remove Timed out Uploads
		for (String toRemove : deadEntries) {
			this.cancelUpload(toRemove);
		}
		deadEntries.clear();
	}

	public void shutdown() {
		// Close open streams
		new HashSet<>(activeUploads.keySet()).forEach(this::cancelUpload);
	}

	public void handleNewUpload(ServerPlayer player, String schematic, long size, BlockPos pos) {
		String playerSchematicId = ownerId(player) + "/" + schematic;
		Path uploadPath = resolveUploadPath(player, schematic);
		if (uploadPath == null)
			return;

		// Too big
		if (!validateSchematicSizeOnServer(player, size))
			return;

		// Skip existing Uploads
		if (activeUploads.containsKey(playerSchematicId))
			return;

		// The table needs an empty schematic and room for the result, like the client checks
		if (!player.isWithinBlockInteractionRange(pos, 4))
			return;
		SchematicTableBlockEntity table = getTable(player.level(), pos);
		if (table == null || table.isUploading || table.inventory.getItem(0)
			.isEmpty() || !table.inventory.getItem(1)
				.isEmpty())
			return;

		try {
			// Too many Schematics; uploading one with the same name again replaces it
			if (!Files.exists(uploadPath) && !tryDeleteOldestSchematic(player.level()
				.getServer(), uploadPath.getParent()))
				return;

			// Written next to it and moved in once complete: until then a schematic with this name (e.g. a copy put
			// into a cannon) still loads the previous file
			Path partFile = partFile(uploadPath);
			OutputStream writer = Files.newOutputStream(partFile);
			SchematicUploadEntry entry = new SchematicUploadEntry(writer, size, player.level(), pos);
			entry.partFile = partFile;
			activeUploads.put(playerSchematicId, entry);

			// Notify Block Entity
			table.startUpload(schematic);
		} catch (IOException e) {
			SchematicCannon.LOGGER.error("Exception Thrown when starting Upload: {}", playerSchematicId, e);
		}
	}

	protected boolean validateSchematicSizeOnServer(ServerPlayer player, long size) {
		long maxFileSize = ModConfig.get().maxTotalSchematicSize;
		if (size > maxFileSize * 1000) {
			player.sendSystemMessage(Lang.translate("schematics.uploadTooLarge")
				.append(Component.literal(" (" + size / 1000 + " KB).")));
			player.sendSystemMessage(Lang.translate("schematics.maxAllowedSize")
				.append(Component.literal(" " + maxFileSize + " KB")));
			return false;
		}
		return true;
	}

	public void handleWriteRequest(ServerPlayer player, String schematic, byte[] data) {
		String playerSchematicId = ownerId(player) + "/" + schematic;

		if (activeUploads.containsKey(playerSchematicId)) {
			SchematicUploadEntry entry = activeUploads.get(playerSchematicId);
			entry.bytesUploaded += data.length;

			// Size Validations
			if (data.length > ModConfig.get().maxSchematicPacketSize) {
				SchematicCannon.LOGGER.warn("Oversized Upload Packet received: {}", playerSchematicId);
				cancelUpload(playerSchematicId);
				return;
			}

			if (entry.bytesUploaded > entry.totalBytes) {
				SchematicCannon.LOGGER.warn("Received more data than Expected: {}", playerSchematicId);
				cancelUpload(playerSchematicId);
				return;
			}

			try {
				entry.stream.write(data);
				entry.idleTime = 0;

				SchematicTableBlockEntity table = getTable(entry.world, entry.tablePos);
				if (table == null)
					return;
				table.uploadingProgress = (float) ((double) entry.bytesUploaded / entry.totalBytes);
				table.sendUpdate = true;

			} catch (IOException e) {
				SchematicCannon.LOGGER.error("Exception Thrown when uploading Schematic: {}", playerSchematicId, e);
				cancelUpload(playerSchematicId);
			}
		}
	}

	protected void cancelUpload(String playerSchematicId) {
		if (!activeUploads.containsKey(playerSchematicId))
			return;

		SchematicUploadEntry entry = activeUploads.remove(playerSchematicId);
		try {
			entry.stream.close();
			// The uploaded file itself was never touched
			Files.deleteIfExists(entry.partFile);
			SchematicCannon.LOGGER.warn("Cancelled Schematic Upload: {}", playerSchematicId);
		} catch (IOException e) {
			SchematicCannon.LOGGER.error("Exception Thrown when cancelling Upload: {}", playerSchematicId, e);
		}

		BlockPos pos = entry.tablePos;
		if (pos == null)
			return;

		SchematicTableBlockEntity table = getTable(entry.world, pos);
		if (table != null)
			table.finishUpload();
	}

	public SchematicTableBlockEntity getTable(Level world, BlockPos pos) {
		BlockEntity be = world.getBlockEntity(pos);
		if (!(be instanceof SchematicTableBlockEntity table))
			return null;
		return table;
	}

	public void handleFinishedUpload(ServerPlayer player, String schematic) {
		String playerSchematicId = ownerId(player) + "/" + schematic;

		if (activeUploads.containsKey(playerSchematicId)) {
			try {
				activeUploads.get(playerSchematicId).stream.close();
				SchematicUploadEntry removed = activeUploads.remove(playerSchematicId);
				Level world = removed.world;
				BlockPos pos = removed.tablePos;
				moveIn(removed.partFile, SchematicFiles.UPLOADED_SCHEMATICS_DIR.resolve(playerSchematicId));

				SchematicCannon.LOGGER.info("New Schematic Uploaded: " + playerSchematicId);
				// Schematics of an earlier upload with this name now load the new file
				SchematicDownloads.invalidate(player.level()
					.getServer(), FileSchematicSource.cacheKey(ownerId(player), schematic));
				if (pos == null)
					return;

				BlockState blockState = world.getBlockState(pos);
				if (!blockState.is(ModBlocks.SCHEMATIC_TABLE))
					return;

				SchematicTableBlockEntity table = getTable(world, pos);
				if (table == null)
					return;
				table.finishUpload();
				if (table.inventory.getItem(1)
					.isEmpty())
					table.inventory.setItem(1, SchematicItem.create(world, schematic, ownerId(player)));

			} catch (IOException e) {
				SchematicCannon.LOGGER.error("Exception Thrown when finishing Upload: {}", playerSchematicId, e);
			}
		}
	}

	public void handleInstantSchematic(ServerPlayer player, String schematic, Level world, BlockPos pos,
		BlockPos bounds) {
		String playerName = player.getGameProfile()
			.name();
		Path uploadPath = resolveUploadPath(player, schematic);
		if (uploadPath == null)
			return;

		// Not holding S&Q
		if (!player.getMainHandItem()
			.is(ModItems.SCHEMATIC_AND_PEN))
			return;

		// The area comes from the client: only save loaded chunks, never load or generate any
		BlockPos end = pos.offset(bounds)
			.offset(-1, -1, -1);
		// Loaded, and at least partly inside the world's height
		if (bounds.getX() <= 0 || bounds.getY() <= 0 || bounds.getZ() <= 0 || pos.getY() > world.getMaxY()
			|| end.getY() < world.getMinY() || !BlockHelper.isLoaded(world, BoundingBox.fromCorners(pos, end))) {
			SchematicCannon.LOGGER.warn("Rejected instant schematic with invalid bounds: {}/{}", playerName, schematic);
			return;
		}

		// if there's too many schematics, delete oldest
		if (!Files.exists(uploadPath) && !tryDeleteOldestSchematic(player.level()
			.getServer(), uploadPath.getParent()))
			return;

		SchematicExportResult result = SchematicExport.saveSchematic(uploadPath.getParent(), schematic, true, world,
			pos, end);
		if (result != null) {
			SchematicDownloads.invalidate(player.level()
				.getServer(), FileSchematicSource.cacheKey(ownerId(player), schematic));
			player.setItemInHand(InteractionHand.MAIN_HAND,
				SchematicItem.create(world, schematic, ownerId(player)));
		} else
			Lang.sendStatus(player, Lang.translate("schematicAndPen.instant_failed").withStyle(ChatFormatting.RED));
	}

	/**
	 * Uploads are stored per player by UUID, which stays the same when the player changes their name.
	 */
	private static String ownerId(ServerPlayer player) {
		return player.getUUID()
			.toString();
	}

	/**
	 * Returns the path for the player's upload, or null if the name is not an .nbt file inside their folder.
	 */
	@Nullable
	private Path resolveUploadPath(ServerPlayer player, String schematic) {
		String playerName = player.getGameProfile()
			.name();
		Path baseDir = SchematicFiles.UPLOADED_SCHEMATICS_DIR;
		Path playerPath;
		Path uploadPath;
		try {
			playerPath = baseDir.resolve(ownerId(player))
				.normalize();
			uploadPath = playerPath.resolve(schematic)
				.normalize();
		} catch (InvalidPathException e) {
			SchematicCannon.LOGGER.warn("Attempted Schematic Upload with an invalid name: {}/{}", playerName, schematic);
			return null;
		}

		if (!baseDir.equals(playerPath.getParent()) || !playerPath.equals(uploadPath.getParent())) {
			SchematicCannon.LOGGER.warn("Attempted Schematic Upload with path traversal: {}/{}", playerName, schematic);
			return null;
		}
		if (!schematic.endsWith(".nbt")) {
			SchematicCannon.LOGGER.warn("Attempted Schematic Upload with non-supported Format: {}/{}", playerName,
				schematic);
			return null;
		}
		SchematicFiles.createFolderIfMissing(playerPath);
		return uploadPath;
	}

	/**
	 * Tells clients that an uploaded file changed or is gone, so they drop what they downloaded of it.
	 */
	private static void invalidate(MinecraftServer server, Path file) {
		SchematicDownloads.invalidate(server, FileSchematicSource.cacheKey(file.getParent()
			.getFileName()
			.toString(), file.getFileName()
				.toString()));
	}

	/** Where an upload is written until it is complete */
	private static Path partFile(Path uploadPath) {
		return uploadPath.resolveSibling(uploadPath.getFileName() + ".part");
	}

	/** Replaces the uploaded file with the complete upload, at once where the file system allows it */
	private static void moveIn(Path partFile, Path uploadPath) throws IOException {
		try {
			Files.move(partFile, uploadPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(partFile, uploadPath, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private boolean tryDeleteOldestSchematic(MinecraftServer server, Path dir) {
		try (Stream<Path> stream = Files.list(dir)) {
			// Uploads in progress (.part) don't count
			List<Path> files = stream.filter(file -> !Files.isDirectory(file) && file.getFileName()
				.toString()
				.endsWith(".nbt"))
				.toList();
			if (files.size() < ModConfig.get().maxSchematics)
				return true;
			Path oldest = files.stream()
				.min(Comparator.comparingLong(this::getLastModifiedTime))
				.orElseThrow();
			Files.delete(oldest);
			invalidate(server, oldest);
			return true;
		} catch (IOException | IllegalStateException e) {
			SchematicCannon.LOGGER.error("Error deleting oldest schematic", e);
			return false;
		}
	}

	private long getLastModifiedTime(Path file) {
		try {
			return Files.getLastModifiedTime(file).toMillis();
		} catch (IOException e) {
			SchematicCannon.LOGGER.error("Error getting modification time of file {}", file.getFileName(), e);
			throw new IllegalStateException(e);
		}
	}

}
