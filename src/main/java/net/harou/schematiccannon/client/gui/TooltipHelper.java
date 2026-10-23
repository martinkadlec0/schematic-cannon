package net.harou.schematiccannon.client.gui;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import net.harou.schematiccannon.client.ModKeys;
import net.harou.schematiccannon.util.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public class TooltipHelper {

	public static final int MAX_WIDTH_PER_LINE = 200;

	public record Palette(Style primary, Style highlight) {
		public static final Palette STANDARD = new Palette(Style.EMPTY.withColor(0xC9974C), Style.EMPTY.withColor(0xF1DD79));
		public static final Palette ALL_GRAY = of(ChatFormatting.GRAY, ChatFormatting.GRAY);
		public static final Palette GRAY_AND_BLUE = of(ChatFormatting.GRAY, ChatFormatting.BLUE);

		private static Palette of(ChatFormatting primary, ChatFormatting highlight) {
			return new Palette(Style.EMPTY.applyFormat(primary), Style.EMPTY.applyFormat(highlight));
		}
	}

	public static MutableComponent holdShift(boolean shiftDown) {
		return Lang.translate("tooltip.holdForDescription", Component.literal(ModKeys.getSneakKeyName())
			.withStyle(shiftDown ? ChatFormatting.WHITE : ChatFormatting.GRAY))
			.withStyle(ChatFormatting.DARK_GRAY);
	}

	/**
	 * Wraps the text into lines; parts between underscores are highlighted.
	 */
	public static List<Component> cutTextComponent(Component c, Palette palette) {
		return cutTextComponent(c, palette, 0);
	}

	public static List<Component> cutTextComponent(Component c, Palette palette, int indent) {
		String s = c.getString();

		List<String> words = new LinkedList<>();
		BreakIterator iterator = BreakIterator.getLineInstance(Locale.getDefault());
		iterator.setText(s);
		int start = iterator.first();
		for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next())
			words.add(s.substring(start, end));

		Font font = Minecraft.getInstance().font;
		List<String> lines = new LinkedList<>();
		StringBuilder currentLine = new StringBuilder();
		int width = 0;
		for (String word : words) {
			int newWidth = font.width(word.replaceAll("_", ""));
			if (width + newWidth > MAX_WIDTH_PER_LINE) {
				if (width > 0) {
					lines.add(currentLine.toString());
					currentLine = new StringBuilder();
					width = 0;
				} else {
					lines.add(word);
					continue;
				}
			}
			currentLine.append(word);
			width += newWidth;
		}
		if (width > 0)
			lines.add(currentLine.toString());

		List<Component> formattedLines = new ArrayList<>(lines.size());
		boolean currentlyHighlighted = false;
		for (String string : lines) {
			MutableComponent currentComponent = Component.literal(" ".repeat(indent));
			for (String part : string.split("_", -1)) {
				currentComponent.append(Component.literal(part)
					.withStyle(currentlyHighlighted ? palette.highlight() : palette.primary()));
				currentlyHighlighted = !currentlyHighlighted;
			}
			formattedLines.add(currentComponent);
			currentlyHighlighted = !currentlyHighlighted;
		}

		return formattedLines;
	}

}
