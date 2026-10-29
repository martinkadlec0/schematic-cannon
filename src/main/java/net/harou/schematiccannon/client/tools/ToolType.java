package net.harou.schematiccannon.client.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.harou.schematiccannon.client.ClientSchematicTools;
import net.harou.schematiccannon.client.ModKeys;
import net.harou.schematiccannon.client.gui.Icons;
import net.harou.schematiccannon.util.Lang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

public enum ToolType implements SchematicTool {

	DEPLOY(new DeployTool(), Icons.I_TOOL_DEPLOY),
	MOVE(new MoveTool(), Icons.I_TOOL_MOVE_XZ),
	MOVE_Y(new MoveVerticalTool(), Icons.I_TOOL_MOVE_Y),
	ROTATE(new RotateTool(), Icons.I_TOOL_ROTATE),
	FLIP(new FlipTool(), Icons.I_TOOL_MIRROR),
	PRINT(new PlaceTool(), Icons.I_CONFIRM);

	private final SchematicToolBase tool;
	private final Icons icon;

	ToolType(SchematicToolBase tool, Icons icon) {
		this.tool = tool;
		this.icon = icon;
	}

	@Override
	public SchematicToolBase getTool() {
		return tool;
	}

	private String langId() {
		return "schematic.tool." + name().toLowerCase(Locale.ROOT);
	}

	@Override
	public MutableComponent getDisplayName() {
		return Lang.translate(langId());
	}

	@Override
	public void renderIcon(GuiGraphicsExtractor graphics, int x, int y, int color) {
		icon.render(graphics, x, y, color);
	}

	/**
	 * The tools for a deployed schematic: the ones registered for its source (see {@link ClientSchematicTools}) first,
	 * then the built-in ones. {@link #MOVE} is the one selected after deploying.
	 */
	public static List<SchematicTool> getTools(ItemStack stack, boolean creative) {
		List<SchematicTool> tools = new ArrayList<>(ClientSchematicTools.get(stack));
		Collections.addAll(tools, MOVE, MOVE_Y, DEPLOY, ROTATE, FLIP);
		if (creative)
			tools.add(PRINT);
		return tools;
	}

	@Override
	public List<Component> getDescription() {
		List<Component> lines = new ArrayList<>();
		for (int i = 0; i < 4; i++)
			lines.add(Lang.translate(langId() + ".description." + i, ModKeys.getSprintKeyName(), ModKeys.getUseKeyName()));
		return lines;
	}

}
