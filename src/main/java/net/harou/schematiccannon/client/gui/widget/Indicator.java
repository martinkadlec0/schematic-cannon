package net.harou.schematiccannon.client.gui.widget;

import net.harou.schematiccannon.client.gui.GuiTextures;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class Indicator extends BaseWidget {

	public State state;

	public Indicator(int x, int y) {
		super(x, y, GuiTextures.INDICATOR.getWidth(), GuiTextures.INDICATOR.getHeight());
		this.state = State.OFF;
		active = false;
	}

	@Override
	protected void doRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		GuiTextures toDraw = switch (state) {
		case OFF -> GuiTextures.INDICATOR;
		case RED -> GuiTextures.INDICATOR_RED;
		case YELLOW -> GuiTextures.INDICATOR_YELLOW;
		case GREEN -> GuiTextures.INDICATOR_GREEN;
		};
		toDraw.render(graphics, getX(), getY());
	}

	public enum State {
		OFF, RED, YELLOW, GREEN
	}

}
