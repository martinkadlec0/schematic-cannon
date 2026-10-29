package net.harou.schematiccannon.schematic;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.Entity;

/**
 * The registered {@link EntityPlacement}s. Entities none of them applies to keep the built-in handling (item frames,
 * armor stands, paintings), everything else is skipped by the cannon.
 */
public class EntityPlacements {

	private static final List<EntityPlacement> PLACEMENTS = new ArrayList<>();

	public static void register(EntityPlacement placement) {
		PLACEMENTS.add(placement);
	}

	@Nullable
	public static EntityPlacement find(Entity schematicEntity) {
		for (EntityPlacement placement : PLACEMENTS)
			if (placement.appliesTo(schematicEntity))
				return placement;
		return null;
	}

}
