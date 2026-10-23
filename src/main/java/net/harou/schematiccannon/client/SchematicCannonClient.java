package net.harou.schematiccannon.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.client.gui.ItemDescriptions;
import net.harou.schematiccannon.client.outline.Outliner;
import net.harou.schematiccannon.client.render.ModBufferSource;
import net.harou.schematiccannon.client.screen.SchematicEditScreen;
import net.harou.schematiccannon.client.screen.SchematicTableScreen;
import net.harou.schematiccannon.client.screen.SchematicannonScreen;
import net.harou.schematiccannon.network.ConfigSyncPacket;
import net.harou.schematiccannon.registry.ModBlockEntityTypes;
import net.harou.schematiccannon.registry.ModMenuTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;

public class SchematicCannonClient implements ClientModInitializer {

	public static final ClientSchematicLoader SCHEMATIC_SENDER = new ClientSchematicLoader();
	public static final SchematicHandler SCHEMATIC_HANDLER = new SchematicHandler();
	public static final SchematicAndPenHandler SCHEMATIC_AND_PEN_HANDLER = new SchematicAndPenHandler();

	@Override
	public void onInitializeClient() {
		ModKeys.register();
		ItemDescriptions.register();

		MenuScreens.register(ModMenuTypes.SCHEMATICANNON, SchematicannonScreen::new);
		MenuScreens.register(ModMenuTypes.SCHEMATIC_TABLE, SchematicTableScreen::new);
		BlockEntityRenderers.register(ModBlockEntityTypes.SCHEMATICANNON, SchematicannonRenderer::new);
		ModelLoadingPlugin.register(SchematicannonRenderer::registerModels);

		ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPacket.TYPE, (packet, context) -> packet.apply());

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			SCHEMATIC_SENDER.cancelUploads();
			SCHEMATIC_HANDLER.reset();
			SCHEMATIC_AND_PEN_HANDLER.reset();
			Outliner.getInstance()
				.clear();
		}));
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> {
			SCHEMATIC_HANDLER.onLevelChange();
			SCHEMATIC_AND_PEN_HANDLER.reset();
		});
		ClientTickEvents.END_CLIENT_TICK.register(SchematicCannonClient::onTick);
		LevelRenderEvents.COLLECT_SUBMITS.register(SchematicCannonClient::onRenderSchematic);
		LevelRenderEvents.COLLECT_SUBMITS.register(SchematicCannonClient::onRenderOverlays);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, SchematicCannon.asResource("schematic_tools"),
			SCHEMATIC_HANDLER::renderOverlay);

		ResourceLoader.get(PackType.CLIENT_RESOURCES)
			.registerReloadListener(SchematicCannon.asResource("schematic_renderers"),
				(ResourceManagerReloadListener) resourceManager -> SCHEMATIC_HANDLER.updateRenderers());
	}

	private static void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null)
			return;
		SCHEMATIC_SENDER.tick();
		SCHEMATIC_AND_PEN_HANDLER.tick();
		SCHEMATIC_HANDLER.tick();
		Outliner.getInstance()
			.tickOutlines();
	}

	private static void onRenderSchematic(LevelRenderContext context) {
		CameraRenderState cameraState = context.levelState().cameraRenderState;
		SCHEMATIC_HANDLER.renderSchematic(context.poseStack(), context.submitNodeCollector(), cameraState, cameraState.pos,
			context.levelState().worldPartialTicks);
	}

	private static void onRenderOverlays(LevelRenderContext context) {
		PoseStack ms = context.poseStack();
		ModBufferSource buffer = ModBufferSource.getInstance();
		buffer.begin();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float pt = context.levelState().worldPartialTicks;

		ms.pushPose();
		SCHEMATIC_HANDLER.renderOverlays(ms, buffer, camera, pt);
		Outliner.getInstance()
			.renderOutlines(ms, buffer, camera, pt);
		buffer.submit(context.submitNodeCollector());
		ms.popPose();
	}

	/**
	 * Called by the input mixins when the "Use" key or button is pressed. Returns true if the press was consumed.
	 */
	public static boolean onUse() {
		if (Minecraft.getInstance().gui.screen() != null)
			return false;
		return SCHEMATIC_HANDLER.onUse() || SCHEMATIC_AND_PEN_HANDLER.onUse();
	}

	/**
	 * Called by the mouse handler mixin before vanilla gets the scroll event.
	 */
	public static boolean wantsScroll() {
		return Minecraft.getInstance().gui.screen() == null
			&& (SCHEMATIC_HANDLER.wantsScroll() || SCHEMATIC_AND_PEN_HANDLER.wantsScroll());
	}

	/**
	 * Called by the mouse handler mixin with whole scroll steps.
	 */
	public static void onMouseScrolled(int delta) {
		if (!SCHEMATIC_HANDLER.mouseScrolled(delta))
			SCHEMATIC_AND_PEN_HANDLER.mouseScrolled(delta);
	}

	public static void openSchematicEditScreen() {
		Minecraft.getInstance()
			.gui.setScreen(new SchematicEditScreen());
	}

}
