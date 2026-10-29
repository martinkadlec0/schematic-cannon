package net.harou.schematiccannon.cannon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.harou.schematiccannon.schematic.ItemRequirement;
import net.harou.schematiccannon.util.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

public class MaterialChecklist {

	public static final int MAX_ENTRIES_PER_PAGE = 5;

	/** Required items, in the order they were first required */
	public final List<Entry> entries = new ArrayList<>();
	public boolean blocksNotLoaded;

	/**
	 * One line of the checklist. Plain requirements are counted per item, others (e.g. items with specific components)
	 * per stack of their requirement.
	 */
	public static class Entry {
		public final ItemRequirement.StackRequirement requirement;
		public int required;
		public int gathered;

		Entry(ItemRequirement.StackRequirement requirement) {
			this.requirement = requirement;
		}

		boolean isFor(ItemRequirement.StackRequirement other) {
			if (requirement.getClass() != other.getClass())
				return false;
			return other.getClass() == ItemRequirement.StackRequirement.class
				? ItemStack.isSameItem(requirement.stack, other.stack)
				: ItemStack.isSameItemSameComponents(requirement.stack, other.stack);
		}

		public ItemStack displayStack() {
			return requirement.stack;
		}
	}

	public void clear() {
		entries.clear();
		blocksNotLoaded = false;
	}

	public void warnBlockNotLoaded() {
		blocksNotLoaded = true;
	}

	public void require(ItemRequirement requirement) {
		if (requirement.isEmpty())
			return;
		if (requirement.isInvalid())
			return;

		for (ItemRequirement.StackRequirement stack : requirement.getRequiredItems()) {
			if (stack.stack.is(Items.AIR))
				continue;
			Entry entry = entries.stream()
				.filter(e -> e.isFor(stack))
				.findFirst()
				.orElse(null);
			if (entry == null) {
				entry = new Entry(stack);
				entries.add(entry);
			}
			entry.required += stack.stack.getCount();
		}
	}

	public void collect(ItemStack stack) {
		for (Entry entry : entries)
			if (entry.requirement.matches(stack)) {
				entry.gathered += stack.getCount();
				return;
			}
	}

	public ItemStack createWrittenBook() {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);

		List<Filterable<Component>> pages = new ArrayList<>();

		int itemsWritten = 0;
		MutableComponent textComponent;

		if (blocksNotLoaded) {
			textComponent = Component.literal("\n" + ChatFormatting.RED);
			textComponent = textComponent.append(Lang.translate("materialChecklist.blocksNotLoaded"));
			pages.add(Filterable.passThrough(textComponent));
		}

		List<Entry> keys = new ArrayList<>(entries);
		keys.sort((entry1, entry2) -> {
			String name1 = entry1.displayStack()
				.getHoverName()
				.getString()
				.toLowerCase(Locale.ENGLISH);
			String name2 = entry2.displayStack()
				.getHoverName()
				.getString()
				.toLowerCase(Locale.ENGLISH);
			return name1.compareTo(name2);
		});

		textComponent = Component.empty();
		List<Entry> completed = new ArrayList<>();
		for (Entry item : keys) {
			int amount = item.required - item.gathered;

			if (amount <= 0) {
				completed.add(item);
				continue;
			}

			if (itemsWritten == MAX_ENTRIES_PER_PAGE) {
				itemsWritten = 0;
				textComponent.append(Component.literal("\n >>>")
					.withStyle(ChatFormatting.BLUE));
				pages.add(Filterable.passThrough(textComponent));
				textComponent = Component.empty();
			}

			itemsWritten++;
			textComponent.append(entry(item.displayStack(), amount, true));
		}

		for (Entry item : completed) {
			if (itemsWritten == MAX_ENTRIES_PER_PAGE) {
				itemsWritten = 0;
				textComponent.append(Component.literal("\n >>>")
					.withStyle(ChatFormatting.DARK_GREEN));
				pages.add(Filterable.passThrough(textComponent));
				textComponent = Component.empty();
			}

			itemsWritten++;
			textComponent.append(entry(item.displayStack(), item.required, false));
		}

		pages.add(Filterable.passThrough(textComponent));

		WrittenBookContent contents = new WrittenBookContent(
			Filterable.passThrough(ChatFormatting.BLUE + "Material Checklist"),
			"Schematicannon",
			0,
			pages,
			true
		);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, contents);
		book.set(DataComponents.CUSTOM_NAME, Lang.translate("materialChecklist")
			.setStyle(Style.EMPTY.withColor(ChatFormatting.BLUE)
				.withItalic(Boolean.FALSE)));

		return book;
	}

	private MutableComponent entry(ItemStack item, int amount, boolean unfinished) {
		int stacks = amount / 64;
		int remainder = amount % 64;
		MutableComponent tc = Component.empty();
		tc.append(item.getItem()
			.getName(item)
			.copy()
			.setStyle(Style.EMPTY
				.withHoverEvent(new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(item)))));

		if (!unfinished)
			tc.append(" ✔");
		tc.withStyle(unfinished ? ChatFormatting.BLUE : ChatFormatting.DARK_GREEN);
		return tc.append(Component.literal("\n" + " x" + amount)
				.withStyle(ChatFormatting.BLACK))
			.append(Component.literal(" | " + stacks + "▤ +" + remainder + "\n")
				.withStyle(ChatFormatting.GRAY));
	}

}
