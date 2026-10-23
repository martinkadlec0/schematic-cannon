package net.harou.schematiccannon.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.harou.schematiccannon.ModConfig;
import net.harou.schematiccannon.client.gui.widget.FilteredEditBox;
import net.harou.schematiccannon.network.ConfigSyncPacket;
import net.harou.schematiccannon.util.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Edits config/schematic_cannon.json. Opened from Mod Menu. The options scroll when the window is too small for them.
 */
public class ConfigScreen extends Screen {

	private static final int HEADER_HEIGHT = 45;
	private static final int ROW_HEIGHT = 20;
	// Rows are this wide; the inputs sit at the right end, after the labels and a gap
	private static final int ROW_WIDTH = 320;
	private static final int FIELD_WIDTH = 80;
	private static final int LABEL_GAP = 8;
	// ScrollableLayout reserves room for the scrollbar on both sides (4 spacing + 6 bar)
	private static final int SCROLLBAR_RESERVE = 10;

	private final Screen parent;
	private final ModConfig config;
	private final List<IntOption> intOptions = new ArrayList<>();
	private final List<StringWidget> labels = new ArrayList<>();
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT,
		HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT);
	@Nullable
	private ScrollableLayout body;
	@Nullable
	private Button doneButton;

	public ConfigScreen(Screen parent) {
		super(Lang.translate("config.title"));
		this.parent = parent;
		// Edit what is in the file, not values a remote server sent us
		this.config = ModConfig.readFromFile();
	}

	@Override
	protected void init() {
		LinearLayout header = layout.addToHeader(LinearLayout.vertical()
			.spacing(4));
		header.defaultCellSetting()
			.alignHorizontallyCenter();
		header.addChild(new StringWidget(title, font));
		header.addChild(new StringWidget(Lang.translate("config.serverNote")
			.withStyle(ChatFormatting.GRAY), font));

		GridLayout options = new GridLayout().rowSpacing(4)
			.columnSpacing(LABEL_GAP);
		GridLayout.RowHelper rows = options.createRowHelper(2);

		addSection(rows, "config.section.client");
		addBool(rows, "enableTooltips", config.enableTooltips, v -> config.enableTooltips = v);
		addBool(rows, "smoothAnimations", config.smoothAnimations, v -> config.smoothAnimations = v);

		addSection(rows, "config.section.server");
		addInt(rows, "schematicannonDelay", c -> c.schematicannonDelay, (c, v) -> c.schematicannonDelay = v);
		addInt(rows, "schematicannonShotsPerGunpowder", c -> c.schematicannonShotsPerGunpowder,
			(c, v) -> c.schematicannonShotsPerGunpowder = v);
		addBool(rows, "creativePrintIncludesAir", config.creativePrintIncludesAir, v -> config.creativePrintIncludesAir = v);
		addInt(rows, "maxSchematics", c -> c.maxSchematics, (c, v) -> c.maxSchematics = v);
		addInt(rows, "maxTotalSchematicSize", c -> c.maxTotalSchematicSize, (c, v) -> c.maxTotalSchematicSize = v);
		addInt(rows, "maxSchematicPacketSize", c -> c.maxSchematicPacketSize, (c, v) -> c.maxSchematicPacketSize = v);
		addInt(rows, "schematicIdleTimeout", c -> c.schematicIdleTimeout, (c, v) -> c.schematicIdleTimeout = v);

		body = layout.addToContents(new ScrollableLayout(minecraft, options, layout.getContentHeight()));

		LinearLayout footer = layout.addToFooter(LinearLayout.horizontal()
			.spacing(8));
		doneButton = footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> saveAndClose())
			.width(150)
			.build());
		footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
			.width(150)
			.build());

		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
		updateDoneButton();
	}

	/**
	 * Called on resize instead of rebuilding the widgets, so typed values stay.
	 */
	@Override
	protected void repositionElements() {
		// Narrower rows on very small GUIs, so the scrollbar stays on screen
		int rowWidth = Math.min(ROW_WIDTH, width - 2 * SCROLLBAR_RESERVE);
		labels.forEach(label -> label.setWidth(Math.max(0, rowWidth - FIELD_WIDTH - LABEL_GAP)));
		body.arrangeElements();
		body.setMaxHeight(layout.getContentHeight());
		layout.arrangeElements();
	}

	private void addSection(GridLayout.RowHelper rows, String key) {
		// Only as wide as the text: the grid spreads a spanning cell's width over its columns, which would widen the input column
		StringWidget section = new StringWidget(Lang.translate(key)
			.withStyle(ChatFormatting.GOLD), font);
		section.setHeight(ROW_HEIGHT);
		rows.addChild(section, 2, rows.newCellSettings()
			.paddingTop(4));
	}

	private void addLabel(GridLayout.RowHelper rows, String name) {
		// Longer labels are cut off, like vanilla option labels
		labels.add(rows.addChild(new StringWidget(ROW_WIDTH - FIELD_WIDTH - LABEL_GAP, ROW_HEIGHT,
			Lang.translate("config.option." + name), font), rows.newCellSettings()
				.alignVerticallyMiddle()));
	}

	private void addInt(GridLayout.RowHelper rows, String name, Function<ModConfig, Integer> getter,
		BiConsumer<ModConfig, Integer> setter) {
		addLabel(rows, name);
		FilteredEditBox box = new FilteredEditBox(font, 0, 0, FIELD_WIDTH, ROW_HEIGHT - 2,
			Lang.translate("config.option." + name));
		box.setMaxLength(10);
		box.setValue(String.valueOf(getter.apply(config)));
		box.setFilter(s -> s.isEmpty() || s.chars()
			.allMatch(Character::isDigit));
		box.setResponder(s -> updateDoneButton());
		box.setTooltip(tooltip(name));
		rows.addChild(box, rows.newCellSettings()
			.alignVerticallyMiddle());
		intOptions.add(new IntOption(box, setter));
	}

	private void addBool(GridLayout.RowHelper rows, String name, boolean value, Consumer<Boolean> setter) {
		addLabel(rows, name);
		rows.addChild(CycleButton.onOffBuilder(value)
			.displayOnlyValue()
			.withTooltip(v -> tooltip(name))
			.create(0, 0, FIELD_WIDTH, ROW_HEIGHT, Lang.translate("config.option." + name), (b, v) -> setter.accept(v)));
	}

	private static Tooltip tooltip(String name) {
		return Tooltip.create(Lang.translate("config.option." + name + ".tooltip"));
	}

	private void updateDoneButton() {
		if (doneButton != null)
			doneButton.active = intOptions.stream()
				.noneMatch(o -> o.box.getValue()
					.isEmpty());
	}

	private void saveAndClose() {
		for (IntOption option : intOptions)
			option.setter.accept(config, (int) Math.min(Integer.MAX_VALUE, Long.parseLong(option.box.getValue())));
		config.clamp();
		ModConfig.save(config);
		applyToRunningGame();
		onClose();
	}

	/**
	 * Server values apply right away in singleplayer (and for players on an opened LAN world). On a remote server they
	 * stay as that server sent them; only the client options change.
	 */
	private void applyToRunningGame() {
		boolean remote = minecraft.getConnection() != null && !minecraft.hasSingleplayerServer();
		if (remote) {
			ModConfig.get().enableTooltips = config.enableTooltips;
			ModConfig.get().smoothAnimations = config.smoothAnimations;
			return;
		}

		ModConfig.set(config);
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server != null)
			server.execute(() -> {
				for (ServerPlayer player : server.getPlayerList()
					.getPlayers())
					ServerPlayNetworking.send(player, ConfigSyncPacket.fromConfig());
			});
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	private record IntOption(EditBox box, BiConsumer<ModConfig, Integer> setter) {
	}

}
