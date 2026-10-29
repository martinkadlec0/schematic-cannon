package net.harou.schematiccannon.schematic.source;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

/**
 * Whether a schematic may be printed where it was deployed. The reason is shown in the cannon screen.
 */
public record PlacementResult(@Nullable Component reason) {

	public static final PlacementResult OK = new PlacementResult(null);

	public static PlacementResult invalid(Component reason) {
		return new PlacementResult(reason);
	}

	public boolean isValid() {
		return reason == null;
	}

}
