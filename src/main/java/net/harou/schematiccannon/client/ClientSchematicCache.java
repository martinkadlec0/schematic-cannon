package net.harou.schematiccannon.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.network.SchematicDownloadPacket;
import net.harou.schematiccannon.network.SchematicDownloadRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Templates downloaded from the server for the schematic preview, by the cache key of their source. Downloads never
 * block: callers ask again until the template is there.
 */
public class ClientSchematicCache {

	/**
	 * Ticks without a part of the download until it is requested again, and then given up. Long enough for a 256 KiB
	 * part on a slow connection.
	 */
	private static final int TIMEOUT = 600;

	/**
	 * Templates kept at most; the least recently used goes first. A preview already built from one keeps working, and it
	 * is downloaded again when needed.
	 */
	private static final int MAX_TEMPLATES = 32;

	private final Map<String, StructureTemplate> templates = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, StructureTemplate> eldest) {
			return size() > MAX_TEMPLATES;
		}
	};
	private final Map<String, Download> downloads = new HashMap<>();
	private final Set<String> failed = new HashSet<>();

	/** The slot of downloads the server sent without being asked, which are not requested again */
	private static final int PUSHED = -2;

	private static class Download {
		final int slot;
		/** Since the download started, for the progress indicator */
		int ticks;
		/** Since the last part arrived, for the timeout */
		int idleTicks;
		boolean retried;
		byte[][] parts;
		int received;

		Download(int slot) {
			this.slot = slot;
		}
	}

	public enum Status {
		READY, DOWNLOADING, FAILED
	}

	/**
	 * The template for the key, or null while it is downloaded. Starts the download from the schematic in the given
	 * inventory slot if needed.
	 */
	@Nullable
	public StructureTemplate get(String key, int slot) {
		StructureTemplate template = templates.get(key);
		if (template != null || failed.contains(key))
			return template;
		if (!downloads.containsKey(key)) {
			downloads.put(key, new Download(slot));
			ClientPlayNetworking.send(new SchematicDownloadRequestPacket(slot));
		}
		return null;
	}

	/**
	 * Whether the template for the key is here, without downloading it.
	 */
	public boolean has(String key) {
		return templates.containsKey(key);
	}

	/**
	 * Whether the template for the key is here or on its way.
	 */
	public boolean hasOrDownloads(String key) {
		return templates.containsKey(key) || downloads.containsKey(key);
	}

	public Status getStatus(String key) {
		if (templates.containsKey(key))
			return Status.READY;
		return failed.contains(key) ? Status.FAILED : Status.DOWNLOADING;
	}

	/**
	 * How long the download of the key is running, in ticks.
	 */
	public int getDownloadTicks(String key) {
		Download download = downloads.get(key);
		return download == null ? 0 : download.ticks;
	}

	/**
	 * Received parts out of the total, or 0 if nothing arrived yet.
	 */
	public float getProgress(String key) {
		Download download = downloads.get(key);
		return download == null || download.parts == null ? 0 : (float) download.received / download.parts.length;
	}

	/**
	 * Allows a failed download to be tried again, e.g. when the schematic is selected again.
	 */
	public void clearFailure(String key) {
		failed.remove(key);
	}

	public void tick() {
		downloads.entrySet()
			.removeIf(entry -> {
				Download download = entry.getValue();
				download.ticks++;
				if (++download.idleTicks < TIMEOUT * (download.retried ? 2 : 1))
					return false;
				// Not asked for, so nothing to ask again; it is downloaded normally once needed
				if (download.slot == PUSHED)
					return true;
				if (download.retried) {
					failed.add(entry.getKey());
					return true;
				}
				download.retried = true;
				download.parts = null;
				download.received = 0;
				ClientPlayNetworking.send(new SchematicDownloadRequestPacket(download.slot));
				return false;
			});
	}

	public void onPart(SchematicDownloadPacket packet) {
		Download download = downloads.get(packet.key());
		// Sent without being asked (see SchematicDownloads#send(ServerPlayer, String, StructureTemplate)), or a download
		// dropped in the meantime: kept, as it is likely needed soon
		if (download == null) {
			if (templates.containsKey(packet.key()) || packet.total() <= 0)
				return;
			download = new Download(PUSHED);
			downloads.put(packet.key(), download);
		}

		if (packet.total() <= 0) {
			downloads.remove(packet.key());
			failed.add(packet.key());
			return;
		}

		if (download.parts == null || download.parts.length != packet.total()) {
			download.parts = new byte[packet.total()][];
			download.received = 0;
		}
		if (packet.part() < 0 || packet.part() >= download.parts.length || download.parts[packet.part()] != null)
			return;
		download.parts[packet.part()] = packet.data();
		download.received++;
		// Still arriving: the timeout counts from the last part, so a large download on a slow connection goes on
		download.idleTicks = 0;
		if (download.received < download.parts.length)
			return;

		downloads.remove(packet.key());
		try {
			ByteArrayOutputStream joined = new ByteArrayOutputStream();
			for (byte[] part : download.parts)
				joined.write(part);
			CompoundTag nbt = NbtIo.readCompressed(new ByteArrayInputStream(joined.toByteArray()),
				NbtAccounter.create(0x20000000L));
			StructureTemplate template = new StructureTemplate();
			template.load(Minecraft.getInstance().level.holderLookup(Registries.BLOCK), nbt);
			templates.put(packet.key(), template);
		} catch (Exception e) {
			SchematicCannon.LOGGER.error("Failed to read downloaded schematic {}", packet.key(), e);
			failed.add(packet.key());
		}
	}

	/**
	 * Drops one template, e.g. because its file was uploaded again; it is downloaded again when needed.
	 */
	public void invalidate(String key) {
		invalidate(key::equals);
	}

	/**
	 * Drops the templates whose keys match, e.g. those a data pack reload changed.
	 */
	public void invalidate(Predicate<String> keys) {
		templates.keySet()
			.removeIf(keys);
		downloads.keySet()
			.removeIf(keys);
		failed.removeIf(keys);
	}

	public void clear() {
		templates.clear();
		downloads.clear();
		failed.clear();
	}

}
