package net.harou.schematiccannon.client.gui.widget;

import java.util.LinkedList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Widget with a tooltip and a click callback. Tooltips are drawn by the screen, on top of everything else.
 */
public abstract class BaseWidget extends AbstractWidget {

	public static final int HEADER_RGB = 0x5391e1;

	protected List<Component> toolTip = new LinkedList<>();
	protected Runnable onClick = () -> {
	};
	protected boolean pressed;

	protected BaseWidget(int x, int y, int width, int height) {
		super(x, y, width, height, CommonComponents.EMPTY);
	}

	@SuppressWarnings("unchecked")
	public <T extends BaseWidget> T withCallback(Runnable cb) {
		this.onClick = cb;
		return (T) this;
	}

	public List<Component> getToolTip() {
		return toolTip;
	}

	public void tick() {
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		doRender(graphics, mouseX, mouseY, partialTicks);
	}

	protected void doRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		pressed = true;
		onClick.run();
	}

	/**
	 * Called by the screens on every mouse release, because vanilla only forwards releases to a dragged widget.
	 */
	public void release() {
		pressed = false;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
