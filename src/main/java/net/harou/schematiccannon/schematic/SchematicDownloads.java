package net.harou.schematiccannon.schematic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.network.SchematicCacheResetPacket;
import net.harou.schematiccannon.network.SchematicDownloadPacket;
import net.harou.schematiccannon.registry.ModDataComponents;
import net.harou.schematiccannon.schematic.source.SchematicSource;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Sends schematic templates to clients, which only have the stack and download the structure for the preview.
 */
public class SchematicDownloads {

	/** Stays well below the 1 MiB limit of clientbound custom payloads */
	public static final int PART_SIZE = 256 * 1024;

	/**
	 * Tells all clients that the schematic changed, so they download it again.
	 *
	 * @param key the cache key of the schematic, see {@link SchematicSource#cacheKey}
	 */
	public static void invalidate(MinecraftServer server, String key) {
		for (ServerPlayer player : PlayerLookup.all(server))
			ServerPlayNetworking.send(player, new SchematicCacheResetPacket(key, false));
	}

	/**
	 * Sends the template of the schematic in the player's inventory slot, -1 for the main hand.
	 */
	public static void send(ServerPlayer player, int slot) {
		ItemStack stack = SchematicItem.getInSlot(player, slot);
		if (!SchematicItem.isValid(stack))
			return;

		SchematicSource source = SchematicItem.getSource(stack);
		StructureTemplate template = source.load(player.level(), stack);
		// An outdated stored size (e.g. the file was uploaded again under the same name) is corrected, so the preview's
		// outline and placement checks match the schematic
		if (!template.getSize()
			.equals(Vec3i.ZERO) && !template.getSize()
				.equals(stack.get(ModDataComponents.SCHEMATIC_BOUNDS)))
			stack.set(ModDataComponents.SCHEMATIC_BOUNDS, template.getSize());
		send(player, source.cacheKey(stack), template);
	}

	/**
	 * Sends a template under its cache key without the client asking for it, e.g. one the player is likely to need
	 * next. The client keeps it until its cache is cleared.
	 */
	public static void send(ServerPlayer player, String key, StructureTemplate template) {
		if (template.getSize()
			.equals(Vec3i.ZERO)) {
			ServerPlayNetworking.send(player, new SchematicDownloadPacket(key, 0, 0, new byte[0]));
			return;
		}

		byte[] data;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(template.save(new CompoundTag()), out);
			data = out.toByteArray();
		} catch (IOException e) {
			SchematicCannon.LOGGER.error("Failed to send schematic {}", key, e);
			ServerPlayNetworking.send(player, new SchematicDownloadPacket(key, 0, 0, new byte[0]));
			return;
		}

		int total = Math.max(1, (data.length + PART_SIZE - 1) / PART_SIZE);
		for (int part = 0; part < total; part++)
			ServerPlayNetworking.send(player, new SchematicDownloadPacket(key, part, total,
				Arrays.copyOfRange(data, part * PART_SIZE, Math.min(data.length, (part + 1) * PART_SIZE))));
	}

}
