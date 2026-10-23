package net.harou.schematiccannon.util;

import java.util.ArrayList;
import java.util.List;

import net.harou.schematiccannon.SchematicCannon;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

public class Lang {

	public static MutableComponent translate(String key, Object... args) {
		for (int i = 0; i < args.length; i++)
			if (!(args[i] instanceof Component) && !(args[i] instanceof Number) && !(args[i] instanceof Boolean))
				args[i] = String.valueOf(args[i]);
		return Component.translatable(SchematicCannon.ID + "." + key, args);
	}

	public static List<Component> translatedOptions(String prefix, String... keys) {
		List<Component> result = new ArrayList<>(keys.length);
		for (String key : keys)
			result.add(translate((prefix != null ? prefix + "." : "") + key));
		return result;
	}

	public static void sendStatus(Player player, Component component) {
		player.sendOverlayMessage(component);
	}

}
