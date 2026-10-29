package net.harou.schematiccannon.schematic.source;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.registry.ModDataComponents;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.resources.Identifier;

/**
 * The registered {@link SchematicSource}s. Stacks without a source component use the {@link #FILE} source.
 */
public class SchematicSources {

	public static final Identifier FILE_ID = SchematicCannon.asResource("file");
	public static final SchematicSource FILE = new FileSchematicSource();

	private static final Map<Identifier, SchematicSource> SOURCES = new HashMap<>();

	static {
		register(FILE_ID, FILE);
	}

	public static void register(Identifier id, SchematicSource source) {
		if (SOURCES.putIfAbsent(id, source) != null)
			throw new IllegalArgumentException("Schematic source " + id + " is already registered");
	}

	public static SchematicSource get(DataComponentGetter stack) {
		return SOURCES.getOrDefault(getId(stack), FILE);
	}

	/**
	 * The starts of the cache keys a data pack reload can change, see {@link SchematicSource#reloadedCacheKeys()}.
	 */
	public static List<String> reloadedCacheKeys() {
		return SOURCES.values()
			.stream()
			.map(SchematicSource::reloadedCacheKeys)
			.flatMap(Optional::stream)
			.toList();
	}

	/**
	 * The id of the stack's source, the file source if it has none.
	 */
	public static Identifier getId(DataComponentGetter stack) {
		return stack.getOrDefault(ModDataComponents.SCHEMATIC_SOURCE, FILE_ID);
	}

}
