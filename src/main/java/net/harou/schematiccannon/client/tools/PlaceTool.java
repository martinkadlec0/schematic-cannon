package net.harou.schematiccannon.client.tools;

import net.minecraft.client.Minecraft;

/**
 * Creative mode only: places the whole schematic at once.
 */
public class PlaceTool extends SchematicToolBase {

	@Override
	public boolean usesMouseWheel() {
		return false;
	}

	@Override
	public boolean handleRightClick() {
		if (!Minecraft.getInstance().player.isCreative())
			return false;
		schematicHandler.printInstantly();
		return true;
	}

}
