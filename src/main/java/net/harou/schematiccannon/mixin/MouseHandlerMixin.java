package net.harou.schematiccannon.mixin;

import org.joml.Vector2i;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.harou.schematiccannon.client.SchematicCannonClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.ScrollWheelHandler;
import net.minecraft.client.input.MouseButtonInfo;

/**
 * Lets the schematic tools consume "Use" clicks and mouse wheel input before vanilla handles them.
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private boolean mouseGrabbed;

	// Separate from vanilla's, which only sees the scrolling we don't consume
	@Unique
	private final ScrollWheelHandler schematic_cannon$scrollWheel = new ScrollWheelHandler();

	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void schematic_cannon$onButton(long windowPointer, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
		if (windowPointer != minecraft.getWindow()
			.handle() || minecraft.gui.overlay() != null)
			return;
		if (action != InputConstants.PRESS
			|| !minecraft.options.keyUse.matches(InputConstants.Type.MOUSE.getOrCreate(buttonInfo.button())))
			return;
		boolean wasGrabbed = mouseGrabbed;
		if (!SchematicCannonClient.onUse())
			return;
		ci.cancel();
		// Like vanilla, a click into the free window grabs the mouse. Not if the click opened a screen (e.g. the
		// schematic name prompt): opening it released the mouse, and grabbing closes the screen again.
		if (!wasGrabbed && minecraft.gui.screen() == null)
			((MouseHandler) (Object) this).grabMouse();
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void schematic_cannon$onScroll(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
		if (windowPointer != minecraft.getWindow()
			.handle() || minecraft.gui.overlay() != null || !SchematicCannonClient.wantsScroll())
			return;
		ci.cancel();
		boolean discrete = minecraft.options.discreteMouseScroll()
			.get();
		double sensitivity = minecraft.options.mouseWheelSensitivity()
			.get();
		Vector2i wheel = schematic_cannon$scrollWheel.onMouseScroll((discrete ? Math.signum(xOffset) : xOffset) * sensitivity,
			(discrete ? Math.signum(yOffset) : yOffset) * sensitivity);
		int delta = wheel.y == 0 ? -wheel.x : wheel.y;
		if (delta != 0)
			SchematicCannonClient.onMouseScrolled(delta);
	}

}
