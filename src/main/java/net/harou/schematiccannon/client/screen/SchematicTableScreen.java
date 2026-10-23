package net.harou.schematiccannon.client.screen;

import com.mojang.blaze3d.Blaze3D;
import java.util.List;

import net.harou.schematiccannon.client.ClientSchematicLoader;
import net.harou.schematiccannon.client.SchematicCannonClient;
import net.harou.schematiccannon.client.gui.BaseContainerScreen;
import net.harou.schematiccannon.client.gui.GuiTextures;
import net.harou.schematiccannon.client.gui.Icons;
import net.harou.schematiccannon.client.gui.widget.IconButton;
import net.harou.schematiccannon.client.gui.widget.Label;
import net.harou.schematiccannon.client.gui.widget.SelectionScrollInput;
import net.harou.schematiccannon.registry.ModItems;
import net.harou.schematiccannon.table.SchematicTableMenu;
import net.harou.schematiccannon.util.Lang;
import net.harou.schematiccannon.util.SchematicFiles;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class SchematicTableScreen extends BaseContainerScreen<SchematicTableMenu> {

	private final Component uploading = Lang.translate("gui.schematicTable.uploading");
	private final Component finished = Lang.translate("gui.schematicTable.finished");
	private final Component refresh = Lang.translate("gui.schematicTable.refresh");
	private final Component folder = Lang.translate("gui.schematicTable.open_folder");
	private final Component noSchematics = Lang.translate("gui.schematicTable.noSchematics");
	private final Component availableSchematicsTitle = Lang.translate("gui.schematicTable.availableSchematics");

	protected GuiTextures background;

	private SelectionScrollInput schematicsArea;
	private IconButton confirmButton;
	private Label schematicsLabel;

	// Ticks to wait for the server to report a requested upload (it may also reject it)
	private static final int UPLOAD_REQUEST_TIMEOUT = 40;
	private int uploadRequestTicks;
	private float progress;
	private float chasingProgress;
	private float lastChasingProgress;

	private final ItemStack renderedItem = new ItemStack(ModItems.SCHEMATIC_TABLE);

	public SchematicTableScreen(SchematicTableMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, GuiTextures.SCHEMATIC_TABLE.getWidth(),
			GuiTextures.SCHEMATIC_TABLE.getHeight() + 4 + GuiTextures.PLAYER_INVENTORY.getHeight());
		background = GuiTextures.SCHEMATIC_TABLE;
	}

	@Override
	protected void init() {
		setWindowOffset(-11, 8);
		super.init();

		ClientSchematicLoader schematicSender = SchematicCannonClient.SCHEMATIC_SENDER;
		int selected = schematicsArea == null ? 0 : schematicsArea.getState();
		schematicsArea = null;
		schematicSender.refresh();

		int x = leftPos;
		int y = topPos + 2;

		schematicsLabel = new Label(x + 51, y + 26, CommonComponents.EMPTY).withShadow();
		addRenderableWidget(schematicsLabel);
		createSchematicsArea(schematicSender.getAvailableSchematics());
		if (schematicsArea != null)
			schematicsArea.setState(selected);

		confirmButton = new IconButton(x + 44, y + 56, Icons.I_CONFIRM);
		confirmButton.withCallback(() -> {
			if (menu.canWrite() && schematicsArea != null) {
				lastChasingProgress = chasingProgress = progress = 0;
				Component schematic = schematicSender.getAvailableSchematics()
					.get(schematicsArea.getState());
				if (schematicSender.startNewUpload(schematic.getString()))
					uploadRequestTicks = UPLOAD_REQUEST_TIMEOUT;
			}
		});

		IconButton folderButton = new IconButton(x + 20, y + 21, Icons.I_OPEN_FOLDER);
		folderButton.withCallback(() -> {
			SchematicFiles.createFolderIfMissing(SchematicFiles.SCHEMATICS_DIR);
			Blaze3D.openPath(SchematicFiles.SCHEMATICS_DIR);
		});
		folderButton.setToolTip(folder);

		IconButton refreshButton = new IconButton(x + 206, y + 21, Icons.I_REFRESH);
		refreshButton.withCallback(() -> {
			schematicSender.refresh();
			if (schematicsArea != null)
				removeWidget(schematicsArea);
			createSchematicsArea(schematicSender.getAvailableSchematics());
		});
		refreshButton.setToolTip(refresh);

		addRenderableWidget(confirmButton);
		addRenderableWidget(folderButton);
		addRenderableWidget(refreshButton);
		containerTick();
	}

	private void createSchematicsArea(List<Component> schematics) {
		schematicsLabel.text = CommonComponents.EMPTY;
		if (schematics.isEmpty()) {
			schematicsArea = null;
			return;
		}
		schematicsArea = new SelectionScrollInput(leftPos + 45, topPos + 23, 139, 18).forOptions(schematics)
			.titled(availableSchematicsTitle.plainCopy())
			.writingTo(schematicsLabel);
		addRenderableWidget(schematicsArea);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		super.extractBackground(graphics, mouseX, mouseY, partialTicks);
		int invX = getLeftOfCentered(GuiTextures.PLAYER_INVENTORY.getWidth());
		int invY = topPos + background.getHeight() + 4;
		renderPlayerInventory(graphics, invX, invY);

		int x = leftPos;
		int y = topPos;

		background.render(graphics, x, y);

		Component titleText;
		if (menu.contentHolder.isUploading)
			titleText = uploading;
		else if (menu.getSlot(1)
			.hasItem())
			titleText = finished;
		else
			titleText = title;

		graphics.text(font, titleText, x + (background.getWidth() - 8 - font.width(titleText)) / 2, y + 4,
			0xFF505050, false);

		if (schematicsArea == null)
			graphics.text(font, noSchematics, x + 54, y + 26, 0xFFD3D3D3);

		renderScaledItem(graphics, renderedItem, x + background.getWidth(), y + background.getHeight() - 40, 3);

		GuiTextures.SCHEMATIC_TABLE_PROGRESS.renderPartial(graphics, x + 70, y + 59,
			Mth.lerp(partialTicks, lastChasingProgress, chasingProgress));
	}

	@Override
	protected void containerTick() {
		super.containerTick();

		boolean finished = menu.getSlot(1)
			.hasItem();

		if (menu.contentHolder.isUploading || finished) {
			if (finished) {
				chasingProgress = lastChasingProgress = progress = 1;
			} else {
				lastChasingProgress = chasingProgress;
				progress = menu.contentHolder.uploadingProgress;
				chasingProgress += (progress - chasingProgress) * .5f;
			}
			confirmButton.active = false;
			uploadRequestTicks = 0;

			schematicsLabel.colored(0xFFCCDDFF);
			String uploadingSchematic = menu.contentHolder.uploadingSchematic;
			schematicsLabel.text = uploadingSchematic == null ? null : Component.literal(uploadingSchematic);
			if (schematicsArea != null)
				schematicsArea.visible = false;

		} else {
			progress = 0;
			chasingProgress = lastChasingProgress = 0;
			if (uploadRequestTicks > 0)
				uploadRequestTicks--;
			confirmButton.active = uploadRequestTicks == 0 && menu.canWrite() && schematicsArea != null;

			schematicsLabel.colored(-1);
			if (schematicsArea != null) {
				schematicsArea.writingTo(schematicsLabel);
				schematicsArea.visible = true;
			}
		}
	}

}
