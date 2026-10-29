package net.harou.schematiccannon.client;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.harou.schematiccannon.schematic.source.SchematicSources;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Client side checks of where a schematic would be placed, by schematic source. They run every tick while the schematic
 * is held, also before it is deployed, so they must be cheap and must not ask the server. The server still has the final
 * say, see {@link net.harou.schematiccannon.schematic.source.SchematicSource#validatePlacement}.
 */
public class ClientPlacementChecks {

	@FunctionalInterface
	public interface ClientPlacementCheck {
		/**
		 * @param box where the schematic would be in the world
		 * @return why it may not be placed there, or null if it may (or it cannot be told on the client)
		 */
		@Nullable
		Component check(ItemStack stack, BoundingBox box);
	}

	private static final Map<Identifier, ClientPlacementCheck> CHECKS = new HashMap<>();

	public static void register(Identifier sourceId, ClientPlacementCheck check) {
		CHECKS.put(sourceId, check);
	}

	@Nullable
	public static Component check(ItemStack stack, BoundingBox box) {
		ClientPlacementCheck check = CHECKS.get(SchematicSources.getId(stack));
		return check == null ? null : check.check(stack, box);
	}

}
