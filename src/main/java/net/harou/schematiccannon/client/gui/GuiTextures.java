package net.harou.schematiccannon.client.gui;

import net.harou.schematiccannon.SchematicCannon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public enum GuiTextures {

	PLAYER_INVENTORY("player_inventory", 176, 108),

	SCHEMATIC("schematics", 10, 8, 192, 123),
	SCHEMATIC_TITLE("schematics_2", 205, 15),
	SCHEMATIC_SLOT("widgets", 54, 0, 16, 16),
	SCHEMATIC_PROMPT("schematics_2", 213, 79),
	HUD_BACKGROUND("overlay", 0, 0, 16, 16, 16),

	SCHEMATIC_TABLE("schematics", 10, 139, 214, 85),
	SCHEMATIC_TABLE_PROGRESS("schematics", 10, 224, 84, 16),

	SCHEMATICANNON_TOP("schematics_2", 0, 77, 213, 42),
	SCHEMATICANNON_BOTTOM("schematics_2", 0, 119, 213, 99),
	SCHEMATICANNON_PROGRESS("schematics_2", 76, 239, 114, 16),
	SCHEMATICANNON_CHECKLIST_PROGRESS("schematics_2", 191, 240, 16, 14),
	SCHEMATICANNON_HIGHLIGHT("schematics_2", 1, 229, 26, 26),
	SCHEMATICANNON_FUEL("schematics_2", 28, 222, 47, 16),

	BUTTON("widgets", 18, 18),
	BUTTON_HOVER("widgets", 18, 0, 18, 18),
	BUTTON_DOWN("widgets", 36, 0, 18, 18),
	BUTTON_GREEN("widgets", 72, 0, 18, 18),
	BUTTON_DISABLED("widgets", 90, 0, 18, 18),
	INDICATOR("widgets", 0, 18, 18, 6),
	INDICATOR_GREEN("widgets", 36, 18, 18, 6),
	INDICATOR_YELLOW("widgets", 54, 18, 18, 6),
	INDICATOR_RED("widgets", 72, 18, 18, 6);

	private final Identifier location;
	private final int width;
	private final int height;
	private final int startX;
	private final int startY;
	private final int textureSize;

	GuiTextures(String location, int width, int height) {
		this(location, 0, 0, width, height);
	}

	GuiTextures(String location, int startX, int startY, int width, int height) {
		this(location, startX, startY, width, height, 256);
	}

	GuiTextures(String location, int startX, int startY, int width, int height, int textureSize) {
		this.location = SchematicCannon.asResource("textures/gui/" + location + ".png");
		this.width = width;
		this.height = height;
		this.startX = startX;
		this.startY = startY;
		this.textureSize = textureSize;
	}

	public void render(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, location, x, y, startX, startY, width, height, textureSize, textureSize);
	}

	/**
	 * Renders the left part of the texture, used for progress bars.
	 */
	public void renderPartial(GuiGraphicsExtractor graphics, int x, int y, float progress) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, location, x, y, startX, startY, (int) (width * progress), height,
			textureSize, textureSize);
	}

	/**
	 * Stretches the texture region over the given size and tints it (ARGB).
	 */
	public void renderStretched(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, location, x, y, startX, startY, w, h, width, height, textureSize,
			textureSize, color);
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

}
