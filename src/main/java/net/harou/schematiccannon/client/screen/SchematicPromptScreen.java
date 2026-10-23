package net.harou.schematiccannon.client.screen;


import net.harou.schematiccannon.client.SchematicCannonClient;
import net.harou.schematiccannon.client.gui.BaseContainerScreen;
import net.harou.schematiccannon.client.gui.BaseScreen;
import net.harou.schematiccannon.client.gui.GuiTextures;
import net.harou.schematiccannon.client.gui.Icons;
import net.harou.schematiccannon.client.gui.widget.IconButton;
import net.harou.schematiccannon.registry.ModItems;
import net.harou.schematiccannon.util.Lang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Asks for the file name after selecting an area with the Schematic and Pen.
 */
public class SchematicPromptScreen extends BaseScreen {

	private final GuiTextures background;

	private final Component convertLabel = Lang.translate("schematicAndPen.convert");
	private final Component abortLabel = Lang.translate("action.discard");
	private final Component confirmLabel = Lang.translate("action.saveToFile");

	private EditBox nameField;

	public SchematicPromptScreen() {
		super(Lang.translate("schematicAndPen.title"));
		background = GuiTextures.SCHEMATIC_PROMPT;
	}

	@Override
	public void init() {
		setWindowSize(background.getWidth(), background.getHeight());
		super.init();

		int x = guiLeft;
		int y = guiTop + 2;

		String typed = nameField == null ? "" : nameField.getValue();
		nameField = new EditBox(font, x + 49, y + 26, 131, 10, CommonComponents.EMPTY);
		nameField.setTextColor(-1);
		nameField.setBordered(false);
		nameField.setMaxLength(35);
		nameField.setValue(typed);
		addRenderableWidget(nameField);

		IconButton abort = new IconButton(x + 7, y + 53, Icons.I_TRASH);
		abort.withCallback(() -> {
			SchematicCannonClient.SCHEMATIC_AND_PEN_HANDLER.discard();
			onClose();
		});
		abort.setToolTip(abortLabel);
		addRenderableWidget(abort);

		IconButton confirm = new IconButton(x + 158, y + 53, Icons.I_CONFIRM);
		confirm.withCallback(() -> confirm(false));
		confirm.setToolTip(confirmLabel);
		addRenderableWidget(confirm);

		IconButton convert = new IconButton(x + 180, y + 53, Icons.I_SCHEMATIC);
		convert.withCallback(() -> confirm(true));
		convert.setToolTip(convertLabel);
		addRenderableWidget(convert);
	}

	@Override
	protected void renderWindow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		int x = guiLeft;
		int y = guiTop;

		background.render(graphics, x, y);
		graphics.text(font, title, x + (background.getWidth() - 8 - font.width(title)) / 2, y + 4, 0xFF505050,
			false);

		graphics.item(new ItemStack(ModItems.SCHEMATIC), x + 22, y + 24);
		BaseContainerScreen.renderScaledItem(graphics, new ItemStack(ModItems.SCHEMATIC_AND_PEN),
			x + background.getWidth() + 6, y + background.getHeight() - 38, 3);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (getFocused() == nameField && event.isConfirmation()) {
			confirm(false);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void setInitialFocus() {
		setInitialFocus(nameField);
	}

	private void confirm(boolean convertImmediately) {
		SchematicCannonClient.SCHEMATIC_AND_PEN_HANDLER.saveSchematic(nameField.getValue(), convertImmediately);
		onClose();
	}
}
