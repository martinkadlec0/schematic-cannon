package net.harou.schematiccannon.client.screen;

import java.util.List;
import java.util.function.Consumer;

import org.joml.Matrix3x2fStack;

import net.harou.schematiccannon.client.ModKeys;
import net.harou.schematiccannon.client.gui.GuiTextures;
import net.harou.schematiccannon.client.tools.SchematicTool;
import net.harou.schematiccannon.util.Lang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/**
 * The tool bar shown above the hotbar while holding a schematic. Not an actual screen: it is drawn as part of the HUD.
 */
public class ToolSelectionScreen {

	private final String scrollToCycle = Lang.translate("gui.toolmenu.cycle")
		.getString();
	private static final String HOLD_TO_FOCUS = "gui.toolmenu.focusKey";

	protected List<SchematicTool> tools;
	protected Consumer<SchematicTool> callback;
	public boolean focused;
	private float yOffset;
	protected int selection;

	protected int w;
	protected int h;

	public ToolSelectionScreen(List<SchematicTool> tools, Consumer<SchematicTool> callback) {
		this(tools, tools.get(0), callback);
	}

	/**
	 * @param initial the tool selected first, if it is one of the tools
	 */
	public ToolSelectionScreen(List<SchematicTool> tools, SchematicTool initial, Consumer<SchematicTool> callback) {
		this.tools = tools;
		this.callback = callback;
		focused = false;
		yOffset = 0;
		selection = Math.max(0, tools.indexOf(initial));

		callback.accept(tools.get(selection));

		w = Math.max(tools.size() * 50 + 30, 220);
		h = 30;
	}

	public void cycle(int direction) {
		selection += (direction < 0) ? 1 : -1;
		selection = (selection + tools.size()) % tools.size();
	}

	public void renderPassive(GuiGraphicsExtractor graphics, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		Matrix3x2fStack matrixStack = graphics.pose();
		int screenWidth = graphics.guiWidth();
		int x = (screenWidth - w) / 2 + 15;
		int y = graphics.guiHeight() - h - 75;

		matrixStack.pushMatrix();
		matrixStack.translate(0, -yOffset);

		GuiTextures gray = GuiTextures.HUD_BACKGROUND;
		gray.renderStretched(graphics, x - 15, y, w, h, ARGB.colorFromFloat(focused ? 7 / 8f : 1 / 2f, 1, 1, 1));

		float toolTipAlpha = yOffset / 10;
		List<Component> toolTip = tools.get(selection)
			.getDescription();
		int stringAlphaComponent = ((int) (toolTipAlpha * 0xFF)) << 24;

		if (toolTipAlpha > 0.25f) {
			gray.renderStretched(graphics, x - 15, y + 33, w, h + 22, ARGB.colorFromFloat(toolTipAlpha, .7f, .7f, .8f));

			int[] lineY = { 38, 50, 60, 72 };
			int[] lineColor = { 0xEEEEEE, 0xCCDDFF, 0xCCDDFF, 0xCCCCDD };
			for (int i = 0; i < Math.min(toolTip.size(), 4); i++)
				graphics.text(font, toolTip.get(i), x - 10, y + lineY[i], lineColor[i] + stringAlphaComponent,
					false);
		}

		if (tools.size() > 1) {
			if (!focused)
				graphics.centeredText(font, Lang.translate(HOLD_TO_FOCUS, ModKeys.getToolMenuKeyName()),
					screenWidth / 2, y - 10, 0xFFCCDDFF);
			else
				graphics.centeredText(font, scrollToCycle, screenWidth / 2, y - 10, 0xFFCCDDFF);
		} else {
			x += 65;
		}

		for (int i = 0; i < tools.size(); i++) {
			matrixStack.pushMatrix();

			float alpha = focused ? 1 : .2f;
			if (i == selection) {
				matrixStack.translate(0, -10);
				graphics.centeredText(font, tools.get(i)
					.getDisplayName()
					.getString(), x + i * 50 + 24, y + 28, 0xFFCCDDFF);
				alpha = 1;
			}
			tools.get(i)
				.renderIcon(graphics, x + i * 50 + 16, y + 12, ARGB.colorFromFloat(alpha, 0, 0, 0));
			tools.get(i)
				.renderIcon(graphics, x + i * 50 + 16, y + 11, ARGB.colorFromFloat(alpha, 1, 1, 1));

			matrixStack.popMatrix();
		}

		matrixStack.popMatrix();
	}

	public void update() {
		if (focused)
			yOffset += (10 - yOffset) * .1f;
		else
			yOffset *= .9f;
	}

	public void onClose() {
		callback.accept(tools.get(selection));
	}

}
