package net.harou.schematiccannon.client.tools;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * An entry of the schematic tool bar: the built-in {@link ToolType}s, and tools added for a kind of schematic with
 * {@link net.harou.schematiccannon.client.ClientSchematicTools}.
 */
public interface SchematicTool {

	SchematicToolBase getTool();

	Component getDisplayName();

	/** Up to four lines shown while the tool menu is open: what it does, then how to use it */
	List<Component> getDescription();

	/** Draws the 16x16 icon, tinted with {@code color} (the bar draws it twice: a dark shadow, then the icon) */
	void renderIcon(GuiGraphicsExtractor graphics, int x, int y, int color);

}
