package net.harou.schematiccannon.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.harou.schematiccannon.client.tools.SchematicTool;
import net.harou.schematiccannon.schematic.source.SchematicSources;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Extra tool bar tools for the schematics of a {@link net.harou.schematiccannon.schematic.source.SchematicSource},
 * offered after the built-in ones while such a schematic is deployed. Register the same tool instance once, so the
 * selection survives the schematic changing.
 */
public class ClientSchematicTools {

	private static final Map<Identifier, List<SchematicTool>> TOOLS = new HashMap<>();

	public static void register(Identifier sourceId, SchematicTool tool) {
		TOOLS.computeIfAbsent(sourceId, id -> new ArrayList<>())
			.add(tool);
	}

	public static List<SchematicTool> get(ItemStack stack) {
		return TOOLS.getOrDefault(SchematicSources.getId(stack), List.of());
	}

}
