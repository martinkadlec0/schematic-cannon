package net.harou.schematiccannon.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.harou.schematiccannon.client.ModKeys;
import net.harou.schematiccannon.ModConfig;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.client.gui.TooltipHelper.Palette;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Item tooltips with a summary and usage hints, shown while holding Shift.
 */
public class ItemDescriptions {

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!ModConfig.get().enableTooltips)
				return;
			if (!BuiltInRegistries.ITEM.getKey(stack.getItem())
				.getNamespace()
				.equals(SchematicCannon.ID))
				return;
			String key = stack.getItem()
				.getDescriptionId() + ".tooltip";
			if (!Language.getInstance().has(key + ".summary"))
				return;
			lines.addAll(Math.min(1, lines.size()), getLines(key, ModKeys.tooltipKeyDown()));
		});
	}

	private static List<Component> getLines(String key, boolean shift) {
		List<Component> lines = new ArrayList<>();

		lines.add(TooltipHelper.holdShift(shift));

		if (!shift)
			return lines;

		lines.add(CommonComponents.EMPTY);
		lines.addAll(TooltipHelper.cutTextComponent(Component.translatable(key + ".summary", ModKeys.getSprintKeyName(), ModKeys.getUseKeyName(), ModKeys.getSneakKeyName()), Palette.STANDARD));

		for (int i = 1; Language.getInstance().has(key + ".condition" + i); i++) {
			if (i == 1)
				lines.add(CommonComponents.EMPTY);
			lines.add(Component.translatable(key + ".condition" + i, ModKeys.getSprintKeyName(), ModKeys.getUseKeyName(), ModKeys.getSneakKeyName())
				.withStyle(ChatFormatting.GRAY));
			lines.addAll(
				TooltipHelper.cutTextComponent(Component.translatable(key + ".behaviour" + i, ModKeys.getSprintKeyName(), ModKeys.getUseKeyName(), ModKeys.getSneakKeyName()), Palette.STANDARD, 1));
		}

		return lines;
	}

}
