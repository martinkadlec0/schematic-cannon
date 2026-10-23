package net.harou.schematiccannon.client.screen;

import java.util.List;

import net.harou.schematiccannon.client.SchematicCannonClient;
import net.harou.schematiccannon.client.SchematicHandler;
import net.harou.schematiccannon.client.gui.BaseContainerScreen;
import net.harou.schematiccannon.client.gui.BaseScreen;
import net.harou.schematiccannon.client.gui.GuiTextures;
import net.harou.schematiccannon.client.gui.Icons;
import net.harou.schematiccannon.client.gui.widget.IconButton;
import net.harou.schematiccannon.client.gui.widget.Label;
import net.harou.schematiccannon.client.gui.widget.SelectionScrollInput;
import net.harou.schematiccannon.registry.ModDataComponents;
import net.harou.schematiccannon.registry.ModItems;
import net.harou.schematiccannon.util.Lang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.harou.schematiccannon.client.gui.widget.FilteredEditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * Opened with shift + right click on a schematic: type in the position, rotation and mirroring.
 */
public class SchematicEditScreen extends BaseScreen {

	private final List<Component> rotationOptions =
		Lang.translatedOptions("schematic.rotation", "none", "cw90", "cw180", "cw270");
	private final List<Component> mirrorOptions =
		Lang.translatedOptions("schematic.mirror", "none", "leftRight", "frontBack");
	private final Component rotationLabel = Lang.translate("schematic.rotation");
	private final Component mirrorLabel = Lang.translate("schematic.mirror");

	private final GuiTextures background;
	private final SchematicHandler handler;

	private FilteredEditBox xInput;
	private FilteredEditBox yInput;
	private FilteredEditBox zInput;

	private SelectionScrollInput rotationArea;
	private SelectionScrollInput mirrorArea;
	private BlockPos anchor;

	public SchematicEditScreen() {
		background = GuiTextures.SCHEMATIC;
		handler = SchematicCannonClient.SCHEMATIC_HANDLER;
	}

	@Override
	protected void init() {
		setWindowSize(background.getWidth(), background.getHeight());
		setWindowOffset(-6, 0);
		super.init();

		int x = guiLeft;
		int y = guiTop + 2;

		// Resizing the window runs init again: keep what was entered
		boolean rebuild = xInput != null;
		String[] typed = rebuild ? new String[] { xInput.getValue(), yInput.getValue(), zInput.getValue() } : null;
		int rotationState = rebuild ? rotationArea.getState() : 0;
		int mirrorState = rebuild ? mirrorArea.getState() : 0;

		xInput = new FilteredEditBox(font, x + 50, y + 26, 34, 10, CommonComponents.EMPTY);
		yInput = new FilteredEditBox(font, x + 90, y + 26, 34, 10, CommonComponents.EMPTY);
		zInput = new FilteredEditBox(font, x + 130, y + 26, 34, 10, CommonComponents.EMPTY);

		if (!rebuild)
			anchor = handler.isDeployed() ? handler.getTransformation()
				.getAnchor() : minecraft.player.blockPosition();
		for (FilteredEditBox widget : new FilteredEditBox[] { xInput, yInput, zInput }) {
			widget.setMaxLength(9);
			widget.setBordered(false);
			widget.setTextColor(-1);
			widget.setFilter(s -> {
				if (s.isEmpty() || s.equals("-"))
					return true;
				try {
					Integer.parseInt(s);
					return true;
				} catch (NumberFormatException e) {
					return false;
				}
			});
		}
		xInput.setValue(rebuild ? typed[0] : "" + anchor.getX());
		yInput.setValue(rebuild ? typed[1] : "" + anchor.getY());
		zInput.setValue(rebuild ? typed[2] : "" + anchor.getZ());

		StructurePlaceSettings settings = handler.getTransformation()
			.toSettings();
		Label labelR = new Label(x + 50, y + 48, CommonComponents.EMPTY).withShadow();
		rotationArea = new SelectionScrollInput(x + 45, y + 43, 118, 18).forOptions(rotationOptions)
			.titled(rotationLabel.plainCopy())
			.setState(rebuild ? rotationState
				: settings.getRotation()
					.ordinal())
			.writingTo(labelR);

		Label labelM = new Label(x + 50, y + 70, CommonComponents.EMPTY).withShadow();
		mirrorArea = new SelectionScrollInput(x + 45, y + 65, 118, 18).forOptions(mirrorOptions)
			.titled(mirrorLabel.plainCopy())
			.setState(rebuild ? mirrorState
				: settings.getMirror()
					.ordinal())
			.writingTo(labelM);

		addRenderableWidgets(xInput, yInput, zInput);
		addRenderableWidgets(labelR, labelM, rotationArea, mirrorArea);

		IconButton confirmButton =
			new IconButton(x + background.getWidth() - 33, y + background.getHeight() - 26, Icons.I_CONFIRM);
		confirmButton.withCallback(this::onClose);
		addRenderableWidget(confirmButton);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isPaste()) {
			String coords = minecraft.keyboardHandler.getClipboard()
				.replaceAll(" ", "");
			String[] split = coords.split(",");
			if (split.length == 3) {
				try {
					for (String s : split)
						Integer.parseInt(s);
					xInput.setValue(split[0]);
					yInput.setValue(split[1]);
					zInput.setValue(split[2]);
					return true;
				} catch (NumberFormatException ignored) {
				}
			}
		}

		return super.keyPressed(event);
	}

	@Override
	protected void renderWindow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		int x = guiLeft;
		int y = guiTop;

		background.render(graphics, x, y);
		String title = handler.getCurrentSchematicName();
		graphics.text(font, title, x + (background.getWidth() - 8 - font.width(title)) / 2, y + 4, 0xFF505050,
			false);

		BaseContainerScreen.renderScaledItem(graphics, new ItemStack(ModItems.SCHEMATIC),
			x + background.getWidth() + 6, y + background.getHeight() - 40, 3);
	}

	@Override
	public void removed() {
		if (minecraft.level == null || !handler.isActive())
			return;
		// An empty field keeps the previous coordinate
		int limit = Level.MAX_LEVEL_SIZE;
		BlockPos newLocation = new BlockPos(Mth.clamp(parseOr(xInput, anchor.getX()), -limit, limit),
			Mth.clamp(parseOr(yInput, anchor.getY()), minecraft.level.getMinY(), minecraft.level.getMaxY()),
			Mth.clamp(parseOr(zInput, anchor.getZ()), -limit, limit));

		StructurePlaceSettings settings = new StructurePlaceSettings();
		settings.setRotation(Rotation.values()[rotationArea.getState()]);
		settings.setMirror(Mirror.values()[mirrorArea.getState()]);

		ItemStack item = handler.getActiveSchematicItem();
		if (item != null) {
			item.set(ModDataComponents.SCHEMATIC_DEPLOYED, true);
			item.set(ModDataComponents.SCHEMATIC_ANCHOR, newLocation);
		}

		handler.getTransformation()
			.init(newLocation, settings, handler.getBounds());
		handler.markDirty();
		handler.deploy();
	}

	private static int parseOr(FilteredEditBox box, int fallback) {
		try {
			return Integer.parseInt(box.getValue());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
