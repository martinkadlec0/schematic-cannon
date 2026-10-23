package net.harou.schematiccannon.schematic;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.util.Lang;
import net.harou.schematiccannon.util.SchematicFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class SchematicExport {
	/**
	 * Save a schematic to a file from a world.
	 *
	 * @param dir       the directory the schematic will be created in
	 * @param fileName  the ideal name of the schematic, may not be the name of the created file
	 * @param overwrite whether overwriting an existing schematic is allowed
	 * @param level     the level where the schematic structure is placed
	 * @param first     the first corner of the schematic area
	 * @param second    the second corner of the schematic area
	 * @return a SchematicExportResult, or null if an error occurred.
	 */
	@Nullable
	public static SchematicExportResult saveSchematic(Path dir, String fileName, boolean overwrite, Level level,
		BlockPos first, BlockPos second) {
		BoundingBox bb = BoundingBox.fromCorners(first, second);
		BlockPos origin = new BlockPos(bb.minX(), bb.minY(), bb.minZ());
		BlockPos bounds = new BlockPos(bb.getXSpan(), bb.getYSpan(), bb.getZSpan());

		StructureTemplate structure = new StructureTemplate();
		structure.fillFromWorld(level, origin, bounds, true, List.of(Blocks.AIR));
		CompoundTag data = structure.save(new CompoundTag());
		replaceStructureVoidWithAir(data);

		if (fileName.isEmpty())
			fileName = Lang.translate("schematicAndPen.fallbackName")
				.getString();
		if (!overwrite)
			fileName = SchematicFiles.findFirstValidFilename(fileName, dir, "nbt");
		if (!fileName.endsWith(".nbt"))
			fileName += ".nbt";
		Path file = dir.resolve(fileName)
			.toAbsolutePath();

		try {
			Files.createDirectories(dir);
			try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file))) {
				NbtIo.writeCompressed(data, out);
			}
			return new SchematicExportResult(file, fileName, origin, bounds);
		} catch (IOException e) {
			SchematicCannon.LOGGER.error("An error occurred while saving schematic [" + fileName + "]", e);
			return null;
		}
	}

	private static void replaceStructureVoidWithAir(CompoundTag nbt) {
		String air = BuiltInRegistries.BLOCK.getKey(Blocks.AIR)
			.toString();
		String structureVoid = BuiltInRegistries.BLOCK.getKey(Blocks.STRUCTURE_VOID)
			.toString();

		ListTag palette = nbt.getListOrEmpty("palette");
		for (int i = 0; i < palette.size(); i++) {
			CompoundTag c = palette.getCompoundOrEmpty(i);
			if (c.getStringOr("Name", "")
				.equals(structureVoid))
				c.putString("Name", air);
		}
	}

	public record SchematicExportResult(Path file, String fileName, BlockPos origin, BlockPos bounds) {
	}
}
