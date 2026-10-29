package net.harou.schematiccannon.client;

import java.nio.FloatBuffer;
import java.util.Locale;

import org.lwjgl.sdl.SDLMouse;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.harou.schematiccannon.SchematicCannon;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public class ModKeys {

	public static final KeyMapping TOOL_MENU = new KeyMapping("key." + SchematicCannon.ID + ".toolmenu",
		InputConstants.KEY_LALT, KeyMapping.Category.register(SchematicCannon.asResource("main")));

	public static void register() {
		KeyMappingHelper.registerKeyMapping(TOOL_MENU);
	}

	public static String getToolMenuKeyName() {
		return getKeyName(TOOL_MENU);
	}

	/**
	 * Holding the sprint key (Ctrl by default) makes the tools select a position in mid-air and lets the mouse wheel
	 * control the tool.
	 */
	public static boolean sprintKeyDown() {
		return isHeld(Minecraft.getInstance().options.keySprint);
	}

	/**
	 * Shift itself, not the sneak key: shows tooltip details, as in vanilla and most mods, wherever sneak is bound.
	 */
	public static boolean tooltipKeyDown() {
		return Minecraft.getInstance()
			.hasShiftDown();
	}

	/**
	 * The sneak key (Shift by default) scrolls faster in lists.
	 */
	public static boolean sneakKeyDown() {
		return isHeld(Minecraft.getInstance().options.keyShift);
	}

	public static String getUseKeyName() {
		return getKeyName(Minecraft.getInstance().options.keyUse);
	}

	public static String getSprintKeyName() {
		return getKeyName(Minecraft.getInstance().options.keySprint);
	}

	public static String getSneakKeyName() {
		return getKeyName(Minecraft.getInstance().options.keyShift);
	}

	private static String getKeyName(KeyMapping mapping) {
		return mapping.getTranslatedKeyMessage()
			.getString()
			.toUpperCase(Locale.ROOT);
	}

	/**
	 * Whether the bound key is held right now. Unlike KeyMapping#isDown this also works while a screen is open and
	 * ignores toggle sprint/sneak.
	 */
	private static boolean isHeld(KeyMapping mapping) {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping);
		if (key.getType() == InputConstants.Type.KEYBOARD)
			return InputConstants.isKeyDown(key.getValue());
		if (key.getType() == InputConstants.Type.MOUSE)
			return (SDLMouse.SDL_GetMouseState((FloatBuffer) null, null) & 1 << key.getValue() - 1) != 0;
		return false;
	}

}
