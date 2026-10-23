package net.harou.schematiccannon.client.gui;

import net.harou.schematiccannon.SchematicCannon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * 16x16 icons from the icon atlas, addressed by column and row.
 */
public enum Icons {

	I_TRASH(1, 0),

	I_CONFIRM(0, 1),
	I_OPEN_FOLDER(2, 1),
	I_REFRESH(3, 1),

	I_DONT_REPLACE(0, 2),
	I_REPLACE_SOLID(1, 2),
	I_REPLACE_ANY(2, 2),
	I_REPLACE_EMPTY(3, 2),

	I_TOOL_DEPLOY(0, 3),
	I_SKIP_MISSING(1, 3),
	I_SKIP_BLOCK_ENTITIES(2, 3),

	I_TOOL_MOVE_XZ(0, 4),
	I_TOOL_MOVE_Y(1, 4),
	I_TOOL_ROTATE(2, 4),
	I_TOOL_MIRROR(3, 4),

	I_PLAY(0, 5),
	I_PAUSE(1, 5),
	I_STOP(2, 5),
	I_PLACEMENT_SETTINGS(3, 5),

	I_SCHEMATIC(0, 8);

	public static final Identifier ICON_ATLAS = SchematicCannon.asResource("textures/gui/icons.png");

	private final int iconX;
	private final int iconY;

	Icons(int column, int row) {
		iconX = column * 16;
		iconY = row * 16;
	}

	public void render(GuiGraphicsExtractor graphics, int x, int y) {
		render(graphics, x, y, -1);
	}

	/**
	 * Renders the icon tinted with an ARGB color.
	 */
	public void render(GuiGraphicsExtractor graphics, int x, int y, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, ICON_ATLAS, x, y, iconX, iconY, 16, 16, 16, 16, 256, 256, color);
	}

}
