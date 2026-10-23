package net.harou.schematiccannon.client.gui;

import java.util.List;

import net.harou.schematiccannon.client.gui.widget.BaseWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Screen with a fixed size window in the middle.
 */
public abstract class BaseScreen extends Screen {

	protected int windowWidth, windowHeight;
	protected int windowXOffset, windowYOffset;
	protected int guiLeft, guiTop;

	protected BaseScreen(Component title) {
		super(title);
	}

	protected BaseScreen() {
		this(CommonComponents.EMPTY);
	}

	/**
	 * This method must be called before {@code super.init()}!
	 */
	protected void setWindowSize(int width, int height) {
		windowWidth = width;
		windowHeight = height;
	}

	/**
	 * This method must be called before {@code super.init()}!
	 */
	protected void setWindowOffset(int xOffset, int yOffset) {
		windowXOffset = xOffset;
		windowYOffset = yOffset;
	}

	@Override
	protected void init() {
		guiLeft = (width - windowWidth) / 2 + windowXOffset;
		guiTop = (height - windowHeight) / 2 + windowYOffset;
	}

	@Override
	public void tick() {
		for (GuiEventListener listener : children())
			if (listener instanceof BaseWidget widget)
				widget.tick();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	@SafeVarargs
	protected final <W extends GuiEventListener & Renderable & NarratableEntry> void addRenderableWidgets(W... widgets) {
		for (W widget : widgets)
			addRenderableWidget(widget);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		super.extractBackground(graphics, mouseX, mouseY, partialTicks);
		renderWindow(graphics, mouseX, mouseY, partialTicks);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
		renderWidgetTooltips(graphics, children(), mouseX, mouseY);
	}

	protected abstract void renderWindow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks);

	@Override
	public boolean keyPressed(KeyEvent event) {
		boolean keyPressed = super.keyPressed(event);
		if (keyPressed || getFocused() instanceof EditBox)
			return keyPressed;

		if (minecraft.options.keyInventory.matches(event)) {
			onClose();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		releaseWidgets(children());
		return super.mouseReleased(event);
	}

	public static void releaseWidgets(List<? extends GuiEventListener> children) {
		for (GuiEventListener child : children)
			if (child instanceof BaseWidget widget)
				widget.release();
	}

	public static void renderWidgetTooltips(GuiGraphicsExtractor graphics, List<? extends GuiEventListener> children, int mouseX,
		int mouseY) {
		for (GuiEventListener child : children)
			if (child instanceof BaseWidget widget && widget.visible && widget.isMouseOver(mouseX, mouseY)
				&& !widget.getToolTip()
					.isEmpty())
				graphics.setComponentTooltipForNextFrame(Minecraft.getInstance().font, widget.getToolTip(), mouseX, mouseY);
	}

}
