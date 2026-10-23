package net.harou.schematiccannon.client.gui;

import java.util.Collection;

import org.joml.Matrix3x2fStack;

import net.harou.schematiccannon.client.gui.widget.BaseWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public abstract class BaseContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

	protected int windowXOffset, windowYOffset;

	public BaseContainerScreen(T container, Inventory inv, Component title, int width, int height) {
		super(container, inv, title, width, height);
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
		super.init();
		leftPos += windowXOffset;
		topPos += windowYOffset;
	}

	@Override
	protected void containerTick() {
		for (GuiEventListener listener : children())
			if (listener instanceof BaseWidget widget)
				widget.tick();
	}

	@SafeVarargs
	protected final <W extends GuiEventListener & Renderable & NarratableEntry> void addRenderableWidgets(W... widgets) {
		for (W widget : widgets)
			addRenderableWidget(widget);
	}

	protected <W extends GuiEventListener & Renderable & NarratableEntry> void addRenderableWidgets(Collection<W> widgets) {
		for (W widget : widgets)
			addRenderableWidget(widget);
	}

	protected void removeWidgets(Collection<? extends GuiEventListener> widgets) {
		for (GuiEventListener widget : widgets)
			removeWidget(widget);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
		renderForeground(graphics, mouseX, mouseY, partialTicks);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		// Titles are drawn by the screens themselves
	}

	protected void renderForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		BaseScreen.renderWidgetTooltips(graphics, children(), mouseX, mouseY);
	}

	public int getLeftOfCentered(int textureWidth) {
		return leftPos - windowXOffset + (imageWidth - textureWidth) / 2;
	}

	public void renderPlayerInventory(GuiGraphicsExtractor graphics, int x, int y) {
		GuiTextures.PLAYER_INVENTORY.render(graphics, x, y);
		graphics.text(font, playerInventoryTitle, x + 8, y + 6, 0xFF404040, false);
	}

	/**
	 * Draws an item scaled up, used for the decorative block next to the windows.
	 */
	public static void renderScaledItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, float scale) {
		Matrix3x2fStack ms = graphics.pose();
		ms.pushMatrix();
		ms.translate(x, y);
		ms.scale(scale, scale);
		graphics.item(stack, 0, 0);
		ms.popMatrix();
	}

	/**
	 * Container screens only scroll bundle slots, pass the rest on to the widgets.
	 */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
			|| getChildAt(mouseX, mouseY).filter(child -> child.mouseScrolled(mouseX, mouseY, scrollX, scrollY))
				.isPresent();
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		BaseScreen.releaseWidgets(children());
		return super.mouseReleased(event);
	}

}
