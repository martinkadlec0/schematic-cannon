package net.harou.schematiccannon.client.gui.widget;

import java.util.function.Predicate;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Edit box that rejects typed or pasted text when the result doesn't match the filter.
 */
public class FilteredEditBox extends EditBox {

	private Predicate<String> filter = s -> true;

	public FilteredEditBox(Font font, int x, int y, int width, int height, Component narration) {
		super(font, x, y, width, height, narration);
	}

	public void setFilter(Predicate<String> filter) {
		this.filter = filter;
	}

	@Override
	public void insertText(String input) {
		String previous = getValue();
		int cursor = getCursorPosition();
		super.insertText(input);
		if (filter.test(getValue()))
			return;
		setValue(previous);
		setCursorPosition(cursor);
		setHighlightPos(cursor);
	}
}
