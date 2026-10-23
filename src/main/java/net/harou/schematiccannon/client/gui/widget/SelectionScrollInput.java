package net.harou.schematiccannon.client.gui.widget;

import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.List;

import net.harou.schematiccannon.client.ModKeys;
import net.harou.schematiccannon.util.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.ScrollWheelHandler;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

/**
 * Scrolling over the widget cycles through a list of options.
 */
public class SelectionScrollInput extends BaseWidget {

	private final ScrollWheelHandler scrollWheel = new ScrollWheelHandler();

	private final MutableComponent scrollToSelect = Lang.translate("gui.scrollInput.scrollToSelect");

	protected List<? extends Component> options = new ArrayList<>();
	protected int state;
	protected int max = 1;
	protected Component title = Lang.translate("gui.scrollInput.defaultTitle");
	protected Label displayLabel;
	protected boolean soundPlayed;

	public SelectionScrollInput(int x, int y, int width, int height) {
		super(x, y, width, height);
	}

	public SelectionScrollInput forOptions(List<? extends Component> options) {
		this.options = options;
		this.max = options.size();
		updateTooltip();
		return this;
	}

	public SelectionScrollInput titled(MutableComponent title) {
		this.title = title;
		updateTooltip();
		return this;
	}

	public SelectionScrollInput writingTo(Label label) {
		this.displayLabel = label;
		if (label != null)
			writeToLabel();
		return this;
	}

	@Override
	public void tick() {
		soundPlayed = false;
	}

	public int getState() {
		return state;
	}

	public SelectionScrollInput setState(int state) {
		this.state = state;
		clampState();
		updateTooltip();
		if (displayLabel != null)
			writeToLabel();
		return this;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		// Scrolling down moves forward through the list
		Vector2i wheel = scrollWheel.onMouseScroll(scrollX, scrollY);
		// Horizontal scrolling too, e.g. Shift+wheel on macOS
		int steps = wheel.y == 0 ? -wheel.x : wheel.y;
		if (steps == 0)
			return true;
		int priorState = state;
		state -= steps * (ModKeys.sneakKeyDown() ? 5 : 1);
		clampState();

		if (priorState != state) {
			if (!soundPlayed)
				Minecraft.getInstance()
					.getSoundManager()
					.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(),
						1.5f + 0.1f * state / Math.max(1, max), 0.125f));
			soundPlayed = true;
			onChanged();
		}

		return priorState != state;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return false;
	}

	/**
	 * Only controlled with the mouse wheel, so keyboard navigation skips it.
	 */
	@Override
	public ComponentPath nextFocusPath(FocusNavigationEvent event) {
		return null;
	}

	protected void clampState() {
		if (state >= max)
			state = max - 1;
		if (state < 0)
			state = 0;
	}

	public void onChanged() {
		if (displayLabel != null)
			writeToLabel();
		updateTooltip();
	}

	protected void writeToLabel() {
		displayLabel.text = options.isEmpty() ? Component.empty() : options.get(state);
	}

	protected void updateTooltip() {
		toolTip.clear();
		if (title == null)
			return;
		toolTip.add(title.plainCopy()
			.withStyle(s -> s.withColor(HEADER_RGB)));
		int min = Math.min(this.max - 16, state - 7);
		int max = Math.max(16, state + 8);
		min = Math.max(min, 0);
		max = Math.min(max, this.max);
		if (min == 1)
			min--;
		if (min > 0)
			toolTip.add(Component.literal("> ...")
				.withStyle(ChatFormatting.GRAY));
		if (this.max - 1 == max)
			max++;
		for (int i = min; i < max; i++) {
			if (i == state)
				toolTip.add(Component.empty()
					.append("-> ")
					.append(options.get(i))
					.withStyle(ChatFormatting.WHITE));
			else
				toolTip.add(Component.empty()
					.append("> ")
					.append(options.get(i))
					.withStyle(ChatFormatting.GRAY));
		}
		if (max < this.max)
			toolTip.add(Component.literal("> ...")
				.withStyle(ChatFormatting.GRAY));

		toolTip.add(scrollToSelect.plainCopy()
			.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

}
