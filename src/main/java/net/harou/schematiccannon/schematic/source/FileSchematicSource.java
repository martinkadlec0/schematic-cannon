package net.harou.schematiccannon.schematic.source;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.registry.ModDataComponents;
import net.harou.schematiccannon.util.Lang;
import net.harou.schematiccannon.util.SchematicFiles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Schematics uploaded at the Schematic Table, read from the server's upload folder.
 */
public class FileSchematicSource implements SchematicSource {

	@Override
	public StructureTemplate load(ServerLevel level, ItemStack stack) {
		StructureTemplate t = new StructureTemplate();
		String owner = stack.get(ModDataComponents.SCHEMATIC_OWNER);
		String schematic = stack.get(ModDataComponents.SCHEMATIC_FILE);

		if (owner == null || schematic == null || !schematic.endsWith(".nbt"))
			return t;

		try {
			Path dir = SchematicFiles.UPLOADED_SCHEMATICS_DIR;
			Path path = dir.resolve(Paths.get(owner, schematic))
				.normalize();
			if (!path.startsWith(dir))
				return t;

			CompoundTag nbt = NbtIo.readCompressed(path, NbtAccounter.create(0x20000000L));
			// Like vanilla structure loading: upgrade files saved by older versions
			nbt = DataFixTypes.STRUCTURE.updateToCurrentVersion(DataFixers.getDataFixer(), nbt,
				NbtUtils.getDataVersion(nbt, 500));
			t.load(level.holderLookup(Registries.BLOCK), nbt);
		} catch (Exception e) {
			SchematicCannon.LOGGER.warn("Failed to read schematic", e);
		}

		return t;
	}

	@Override
	public String cacheKey(ItemStack stack) {
		return cacheKey(stack.get(ModDataComponents.SCHEMATIC_OWNER), stack.get(ModDataComponents.SCHEMATIC_FILE));
	}

	/**
	 * The cache key of an uploaded file, so the server can tell clients when the file changed.
	 */
	public static String cacheKey(String owner, String file) {
		return "file:" + owner + "/" + file;
	}

	@Override
	public Component displayName(DataComponentGetter stack) {
		String file = stack.get(ModDataComponents.SCHEMATIC_FILE);
		return file != null ? Component.literal(file) : Lang.translate("schematic.invalid");
	}

	/**
	 * The file name, or the warning of a schematic without a file (it needs the Schematic Table) in red.
	 */
	@Override
	public void addToTooltip(DataComponentGetter stack, Consumer<Component> tooltip) {
		if (stack.get(ModDataComponents.SCHEMATIC_FILE) == null)
			tooltip.accept(displayName(stack).copy()
				.withStyle(ChatFormatting.RED));
		else
			SchematicSource.super.addToTooltip(stack, tooltip);
	}

}
