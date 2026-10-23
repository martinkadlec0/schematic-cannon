package net.harou.schematiccannon.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.InputConstants;

import net.harou.schematiccannon.client.SchematicCannonClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

/**
 * Lets the schematic tools consume the "Use" key when it is bound to a keyboard key.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

	@Shadow
	@Final
	private Minecraft minecraft;

	// Whether the press of the held "Use" key went to the tools, so its repeats must not reach vanilla either
	@Unique
	private boolean schematic_cannon$useConsumed;

	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void schematic_cannon$keyPress(long windowPointer, int action, KeyEvent event, CallbackInfo ci) {
		if (windowPointer != minecraft.getWindow()
			.handle() || minecraft.gui.overlay() != null || !minecraft.options.keyUse.matches(event))
			return;
		if (action == InputConstants.RELEASE) {
			schematic_cannon$useConsumed = false;
			return;
		}
		if (action == InputConstants.REPEAT) {
			if (schematic_cannon$useConsumed)
				ci.cancel();
			return;
		}
		// F3 combos are debug actions
		if (minecraft.options.keyDebugModifier.isDown())
			return;
		schematic_cannon$useConsumed = SchematicCannonClient.onUse();
		if (schematic_cannon$useConsumed)
			ci.cancel();
	}

}
