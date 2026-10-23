package net.harou.schematiccannon.client.gui.widget;

import net.harou.schematiccannon.client.gui.GuiTextures;
import net.harou.schematiccannon.client.gui.Icons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public class IconButton extends BaseWidget {

	protected Icons icon;

	public boolean green;

	public IconButton(int x, int y, Icons icon) {
		super(x, y, 18, 18);
		this.icon = icon;
	}

	@Override
	protected void doRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		GuiTextures button = !active ? GuiTextures.BUTTON_DISABLED
			: isHovered && pressed ? GuiTextures.BUTTON_DOWN
				: isHoveredOrFocused() ? GuiTextures.BUTTON_HOVER : green ? GuiTextures.BUTTON_GREEN : GuiTextures.BUTTON;

		button.render(graphics, getX(), getY());
		icon.render(graphics, getX() + 1, getY() + 1);
		handleCursor(graphics);
	}

	@Override
	public boolean shouldTakeFocusAfterInteraction() {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!isActive() || !event.isSelection())
			return false;
		playDownSound(Minecraft.getInstance()
			.getSoundManager());
		onClick.run();
		return true;
	}

	public void setToolTip(Component text) {
		toolTip.clear();
		toolTip.add(text);
	}
}
