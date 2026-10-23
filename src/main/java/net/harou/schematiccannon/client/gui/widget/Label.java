package net.harou.schematiccannon.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class Label extends BaseWidget {

	public Component text;
	protected boolean hasShadow;
	protected int color;

	public Label(int x, int y, Component text) {
		super(x, y, Minecraft.getInstance().font.width(text), 10);
		this.text = text;
		color = -1;
		active = false;
		hasShadow = false;
	}

	/**
	 * @param color ARGB
	 */
	public Label colored(int color) {
		this.color = color;
		return this;
	}

	public Label withShadow() {
		this.hasShadow = true;
		return this;
	}

	@Override
	protected void doRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		if (text == null || text.getString()
			.isEmpty())
			return;
		graphics.text(Minecraft.getInstance().font, text, getX(), getY(), color, hasShadow);
	}

}
