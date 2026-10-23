package net.harou.schematiccannon.cannon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.harou.schematiccannon.schematic.ItemRequirement;
import net.harou.schematiccannon.util.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

public class MaterialChecklist {

	public static final int MAX_ENTRIES_PER_PAGE = 5;

	public Object2IntMap<Item> gathered = new Object2IntArrayMap<>();
	public Object2IntMap<Item> required = new Object2IntArrayMap<>();
	public boolean blocksNotLoaded;

	public void warnBlockNotLoaded() {
		blocksNotLoaded = true;
	}

	public void require(ItemRequirement requirement) {
		if (requirement.isEmpty())
			return;
		if (requirement.isInvalid())
			return;

		for (ItemRequirement.StackRequirement stack : requirement.getRequiredItems())
			putOrIncrement(required, stack.stack);
	}

	private void putOrIncrement(Object2IntMap<Item> map, ItemStack stack) {
		Item item = stack.getItem();
		if (item == Items.AIR)
			return;
		map.put(item, map.getOrDefault(item, 0) + stack.getCount());
	}

	public void collect(ItemStack stack) {
		Item item = stack.getItem();
		if (required.containsKey(item))
			gathered.put(item, gathered.getOrDefault(item, 0) + stack.getCount());
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

		List<Item> keys = new ArrayList<>(required.keySet());
		keys.sort((item1, item2) -> {
			String name1 = item1.getName(item1.getDefaultInstance())
				.getString()
				.toLowerCase(Locale.ENGLISH);
			String name2 = item2.getName(item2.getDefaultInstance())
				.getString()
				.toLowerCase(Locale.ENGLISH);
			return name1.compareTo(name2);
		});

		textComponent = Component.empty();
		List<Item> completed = new ArrayList<>();
		for (Item item : keys) {
			int amount = required.getInt(item) - gathered.getOrDefault(item, 0);

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
			textComponent.append(entry(new ItemStack(item), amount, true));
		}

		for (Item item : completed) {
			if (itemsWritten == MAX_ENTRIES_PER_PAGE) {
				itemsWritten = 0;
				textComponent.append(Component.literal("\n >>>")
					.withStyle(ChatFormatting.DARK_GREEN));
				pages.add(Filterable.passThrough(textComponent));
				textComponent = Component.empty();
			}

			itemsWritten++;
			textComponent.append(entry(new ItemStack(item), required.getInt(item), false));
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
