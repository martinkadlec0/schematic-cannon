package net.harou.schematiccannon.client.screen;

import static net.minecraft.ChatFormatting.BLUE;
import static net.minecraft.ChatFormatting.GRAY;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.harou.schematiccannon.client.ModKeys;
import net.harou.schematiccannon.cannon.SchematicannonBlockEntity;
import net.harou.schematiccannon.cannon.SchematicannonMenu;
import net.harou.schematiccannon.client.gui.BaseContainerScreen;
import net.harou.schematiccannon.client.gui.GuiTextures;
import net.harou.schematiccannon.client.gui.Icons;
import net.harou.schematiccannon.client.gui.TooltipHelper;
import net.harou.schematiccannon.client.gui.TooltipHelper.Palette;
import net.harou.schematiccannon.client.gui.widget.IconButton;
import net.harou.schematiccannon.client.gui.widget.Indicator;
import net.harou.schematiccannon.client.gui.widget.Indicator.State;
import net.harou.schematiccannon.network.ConfigureSchematicannonPacket;
import net.harou.schematiccannon.network.ConfigureSchematicannonPacket.Option;
import net.harou.schematiccannon.registry.ModItems;
import net.harou.schematiccannon.util.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class SchematicannonScreen extends BaseContainerScreen<SchematicannonMenu> {

	private static final GuiTextures BG_BOTTOM = GuiTextures.SCHEMATICANNON_BOTTOM;
	private static final GuiTextures BG_TOP = GuiTextures.SCHEMATICANNON_TOP;
	// Dark ink on the paper background
	private static final int STATUS_COLOR = 0xFF2B2D3C;

	private final Component listPrinter = Lang.translate("gui.schematicannon.listPrinter");
	private static final String GUNPOWDER_LEVEL = "gui.schematicannon.gunpowderLevel";
	private static final String SHOTS_REMAINING = "gui.schematicannon.shotsRemaining";
	private static final String SHOW_SETTINGS = "gui.schematicannon.showOptions";
	private static final String SHOTS_REMAINING_WITH_BACKUP = "gui.schematicannon.shotsRemainingWithBackup";

	private static final String SLOT_GUNPOWDER = "gui.schematicannon.slot.gunpowder";
	private static final String SLOT_LIST_PRINTER = "gui.schematicannon.slot.listPrinter";
	private static final String SLOT_SCHEMATIC = "gui.schematicannon.slot.schematic";

	private final Component optionEnabled = Lang.translate("gui.schematicannon.optionEnabled");
	private final Component optionDisabled = Lang.translate("gui.schematicannon.optionDisabled");

	protected List<IconButton> replaceLevelButtons;

	protected IconButton skipMissingButton;
	protected IconButton skipBlockEntitiesButton;

	protected IconButton playButton;
	protected Indicator playIndicator;
	protected IconButton pauseButton;
	protected Indicator pauseIndicator;
	protected IconButton resetButton;
	protected Indicator resetIndicator;

	private boolean showSettings;

	protected List<AbstractWidget> placementSettingWidgets;

	private final ItemStack renderedItem = new ItemStack(ModItems.SCHEMATICANNON);

	public SchematicannonScreen(SchematicannonMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, BG_TOP.getWidth(),
			BG_TOP.getHeight() + BG_BOTTOM.getHeight() + 2 + GuiTextures.PLAYER_INVENTORY.getHeight());
		placementSettingWidgets = new ArrayList<>();
	}

	@Override
	protected void init() {
		setWindowOffset(-11, 0);
		super.init();

		int x = leftPos;
		int y = topPos;

		// Play Pause Stop
		playButton = new IconButton(x + 75, y + 85, Icons.I_PLAY);
		playButton.withCallback(() -> sendOptionUpdate(Option.PLAY, true));
		playIndicator = new Indicator(x + 75, y + 79);
		pauseButton = new IconButton(x + 93, y + 85, Icons.I_PAUSE);
		pauseButton.withCallback(() -> sendOptionUpdate(Option.PAUSE, true));
		pauseIndicator = new Indicator(x + 93, y + 79);
		resetButton = new IconButton(x + 111, y + 85, Icons.I_STOP);
		resetButton.withCallback(() -> sendOptionUpdate(Option.STOP, true));
		resetIndicator = new Indicator(x + 111, y + 79);
		addRenderableWidgets(playButton, playIndicator, pauseButton, pauseIndicator, resetButton, resetIndicator);

		IconButton confirmButton = new IconButton(x + 180, y + 111, Icons.I_CONFIRM);
		confirmButton.withCallback(this::onClose);
		addRenderableWidget(confirmButton);

		IconButton showSettingsButton = new IconButton(x + 8, y + 111, Icons.I_PLACEMENT_SETTINGS);
		showSettingsButton.withCallback(() -> {
			showSettings = !showSettings;
			initPlacementSettings();
		});
		showSettingsButton.setToolTip(Lang.translate(SHOW_SETTINGS));
		addRenderableWidget(showSettingsButton);

		initPlacementSettings();
		containerTick();
	}

	private void initPlacementSettings() {
		removeWidgets(placementSettingWidgets);
		placementSettingWidgets.clear();

		if (!showSettings)
			return;

		int x = leftPos;
		int y = topPos;

		// Replace settings
		replaceLevelButtons = new ArrayList<>(4);
		List<Icons> icons =
			List.of(Icons.I_DONT_REPLACE, Icons.I_REPLACE_SOLID, Icons.I_REPLACE_ANY, Icons.I_REPLACE_EMPTY);
		List<Component> toolTips = List.of(Lang.translate("gui.schematicannon.option.dontReplaceSolid"),
			Lang.translate("gui.schematicannon.option.replaceWithSolid"),
			Lang.translate("gui.schematicannon.option.replaceWithAny"),
			Lang.translate("gui.schematicannon.option.replaceWithEmpty"));

		for (int i = 0; i < 4; i++) {
			IconButton replaceLevelButton = new IconButton(x + 33 + i * 18, y + 111, icons.get(i));
			int replaceMode = i;
			replaceLevelButton.withCallback(() -> {
				if (menu.contentHolder.replaceMode != replaceMode)
					sendOptionUpdate(Option.values()[replaceMode], true);
			});
			replaceLevelButton.setToolTip(toolTips.get(i));
			replaceLevelButtons.add(replaceLevelButton);
		}
		placementSettingWidgets.addAll(replaceLevelButtons);

		// Other Settings
		skipMissingButton = new IconButton(x + 111, y + 111, Icons.I_SKIP_MISSING);
		skipMissingButton.withCallback(() -> sendOptionUpdate(Option.SKIP_MISSING, !menu.contentHolder.skipMissing));
		skipMissingButton.setToolTip(Lang.translate("gui.schematicannon.option.skipMissing"));
		placementSettingWidgets.add(skipMissingButton);

		skipBlockEntitiesButton = new IconButton(x + 135, y + 111, Icons.I_SKIP_BLOCK_ENTITIES);
		skipBlockEntitiesButton.withCallback(
			() -> sendOptionUpdate(Option.SKIP_BLOCK_ENTITIES, !menu.contentHolder.replaceBlockEntities));
		skipBlockEntitiesButton.setToolTip(Lang.translate("gui.schematicannon.option.skipBlockEntities"));
		placementSettingWidgets.add(skipBlockEntitiesButton);

		addRenderableWidgets(placementSettingWidgets);
	}

	@Override
	protected void containerTick() {
		super.containerTick();

		SchematicannonBlockEntity be = menu.contentHolder;

		if (showSettings) {
			for (int replaceMode = 0; replaceMode < replaceLevelButtons.size(); replaceMode++)
				replaceLevelButtons.get(replaceMode).green = replaceMode == be.replaceMode;
			skipMissingButton.green = be.skipMissing;
			skipBlockEntitiesButton.green = !be.replaceBlockEntities;
		}

		playIndicator.state = State.OFF;
		pauseIndicator.state = State.OFF;
		resetIndicator.state = State.OFF;

		switch (be.state) {
		case PAUSED -> {
			pauseIndicator.state = State.YELLOW;
			playButton.active = true;
			pauseButton.active = false;
			resetButton.active = true;
		}
		case RUNNING -> {
			playIndicator.state = State.GREEN;
			playButton.active = false;
			pauseButton.active = true;
			resetButton.active = true;
		}
		case STOPPED -> {
			resetIndicator.state = State.RED;
			playButton.active = true;
			pauseButton.active = false;
			resetButton.active = false;
		}
		}

		handleTooltips();
	}

	protected void handleTooltips() {
		if (!showSettings)
			return;

		for (AbstractWidget w : placementSettingWidgets)
			if (w instanceof IconButton button && !button.getToolTip()
				.isEmpty()) {
				button.setToolTip(button.getToolTip()
					.get(0));
				button.getToolTip()
					.add(TooltipHelper.holdShift(ModKeys.sneakKeyDown()));
			}

		if (ModKeys.sneakKeyDown()) {
			fillToolTip(skipMissingButton, "skipMissing");
			fillToolTip(skipBlockEntitiesButton, "skipBlockEntities");
			fillToolTip(replaceLevelButtons.get(0), "dontReplaceSolid");
			fillToolTip(replaceLevelButtons.get(1), "replaceWithSolid");
			fillToolTip(replaceLevelButtons.get(2), "replaceWithAny");
			fillToolTip(replaceLevelButtons.get(3), "replaceWithEmpty");
		}
	}

	private void fillToolTip(IconButton button, String tooltipKey) {
		if (!button.isHovered())
			return;
		boolean enabled = button.green;
		List<Component> tip = button.getToolTip();
		tip.add((enabled ? optionEnabled : optionDisabled).plainCopy()
			.withStyle(enabled ? ChatFormatting.DARK_GREEN : ChatFormatting.RED));
		tip.addAll(TooltipHelper.cutTextComponent(
			Lang.translate("gui.schematicannon.option." + tooltipKey + ".description"), Palette.ALL_GRAY));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		super.extractBackground(graphics, mouseX, mouseY, partialTicks);
		int invX = getLeftOfCentered(GuiTextures.PLAYER_INVENTORY.getWidth());
		int invY = topPos + BG_TOP.getHeight() + BG_BOTTOM.getHeight() + 2;
		renderPlayerInventory(graphics, invX, invY);

		int x = leftPos;
		int y = topPos;

		BG_TOP.render(graphics, x, y);
		BG_BOTTOM.render(graphics, x, y + BG_TOP.getHeight());
		GuiTextures.SCHEMATIC_TITLE.render(graphics, x, y - 2);

		SchematicannonBlockEntity be = menu.contentHolder;
		GuiTextures.SCHEMATICANNON_PROGRESS.renderPartial(graphics, x + 44, y + 64, Math.min(be.schematicProgress, 1));
		GuiTextures.SCHEMATICANNON_FUEL.renderPartial(graphics, x + 36, y + 19,
			be.remainingFuel / (float) be.getShotsPerGunpowder());
		GuiTextures.SCHEMATICANNON_CHECKLIST_PROGRESS.renderPartial(graphics, x + 154, y + 20, be.bookPrintingProgress);

		if (!be.inventory.getItem(0)
			.isEmpty())
			GuiTextures.SCHEMATICANNON_HIGHLIGHT.render(graphics, x + 10, y + 60);

		renderScaledItem(graphics, renderedItem, x + BG_TOP.getWidth(), y + BG_TOP.getHeight() + BG_BOTTOM.getHeight() - 48,
			5);

		graphics.text(font, title, x + (BG_TOP.getWidth() - 8 - font.width(title)) / 2, y + 2, 0xFF505050, false);

		Component msg = Lang.translate("schematicannon.status." + be.statusMsg);
		int stringWidth = font.width(msg);

		if (be.missingItem != null) {
			stringWidth += 16;
			graphics.item(be.missingItem, x + 128, y + 49);
		}

		graphics.text(font, msg, x + 103 - stringWidth / 2, y + 53, STATUS_COLOR, false);

		if ("schematicErrored".equals(be.statusMsg))
			graphics.text(font, Lang.translate("schematicannon.status.schematicErroredCheckLogs"),
				x + 103 - stringWidth / 2, y + 65, STATUS_COLOR, false);
	}

	@Override
	protected void renderForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		SchematicannonBlockEntity be = menu.contentHolder;

		int x = leftPos;
		int y = topPos;

		int fuelX = x + 36, fuelY = y + 19;
		if (mouseX >= fuelX && mouseY >= fuelY && mouseX <= fuelX + GuiTextures.SCHEMATICANNON_FUEL.getWidth()
			&& mouseY <= fuelY + GuiTextures.SCHEMATICANNON_FUEL.getHeight())
			graphics.setComponentTooltipForNextFrame(font, getFuelLevelTooltip(be), mouseX, mouseY);

		if (hoveredSlot != null && !hoveredSlot.hasItem()) {
			String key = switch (hoveredSlot.index) {
			case 0 -> SLOT_SCHEMATIC;
			case 2 -> SLOT_LIST_PRINTER;
			case 4 -> SLOT_GUNPOWDER;
			default -> null;
			};
			if (key != null)
				graphics.setComponentTooltipForNextFrame(font,
					TooltipHelper.cutTextComponent(Lang.translate(key), Palette.GRAY_AND_BLUE), mouseX, mouseY);
		}

		if (be.missingItem != null) {
			int missingBlockX = x + 128, missingBlockY = y + 49;
			if (mouseX >= missingBlockX && mouseY >= missingBlockY && mouseX <= missingBlockX + 16
				&& mouseY <= missingBlockY + 16)
				graphics.setTooltipForNextFrame(font, be.missingItem, mouseX, mouseY);
		}

		int paperX = x + 112, paperY = y + 19;
		if (mouseX >= paperX && mouseY >= paperY && mouseX <= paperX + 16 && mouseY <= paperY + 16)
			graphics.setTooltipForNextFrame(font, listPrinter, mouseX, mouseY);

		super.renderForeground(graphics, mouseX, mouseY, partialTicks);
	}

	protected List<Component> getFuelLevelTooltip(SchematicannonBlockEntity be) {
		int shotsLeft = be.remainingFuel;
		int shotsLeftWithItems = shotsLeft + be.inventory.getItem(4)
			.getCount() * be.getShotsPerGunpowder();
		List<Component> tooltip = new ArrayList<>();

		int fillPercent = (int) ((be.remainingFuel / (float) be.getShotsPerGunpowder()) * 100);
		tooltip.add(Lang.translate(GUNPOWDER_LEVEL, fillPercent));
		tooltip.add(Lang.translate(SHOTS_REMAINING, Component.literal(Integer.toString(shotsLeft))
				.withStyle(BLUE))
			.withStyle(GRAY));
		if (shotsLeftWithItems != shotsLeft)
			tooltip.add(Lang.translate(SHOTS_REMAINING_WITH_BACKUP, Component.literal(Integer.toString(shotsLeftWithItems))
					.withStyle(BLUE))
				.withStyle(GRAY));

		return tooltip;
	}

	protected void sendOptionUpdate(Option option, boolean set) {
		ClientPlayNetworking.send(new ConfigureSchematicannonPacket(option, set));
	}

}
