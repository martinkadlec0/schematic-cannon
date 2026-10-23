package net.harou.schematiccannon.client.render;

import net.harou.schematiccannon.SchematicCannon;
import net.minecraft.resources.Identifier;

/**
 * Textures for outlines, drawn with vanilla entity render types.
 */
public class ModRenderTypes {

	public static final Identifier BLANK = SchematicCannon.asResource("textures/special/blank.png");
	public static final Identifier CHECKERED = SchematicCannon.asResource("textures/special/checkerboard.png");

	private ModRenderTypes() {
	}
}
