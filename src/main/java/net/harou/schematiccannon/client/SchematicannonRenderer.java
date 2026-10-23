package net.harou.schematiccannon.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.cannon.LaunchedItem;
import net.harou.schematiccannon.cannon.SchematicannonBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

public class SchematicannonRenderer
	implements BlockEntityRenderer<SchematicannonBlockEntity, SchematicannonRenderer.RenderState> {

	public static final ExtraModelKey<BlockStateModel> CONNECTOR = ExtraModelKey.create(() -> "schematicannon connector");
	public static final ExtraModelKey<BlockStateModel> PIPE = ExtraModelKey.create(() -> "schematicannon pipe");
	public static final ExtraModelKey<BlockStateModel> BARREL = ExtraModelKey.create(() -> "schematicannon barrel");

	public static void registerModels(ModelLoadingPlugin.Context context) {
		context.addModel(CONNECTOR,
			SimpleUnbakedExtraModel.blockStateModel(SchematicCannon.asResource("block/schematicannon/connector")));
		context.addModel(PIPE, SimpleUnbakedExtraModel.blockStateModel(SchematicCannon.asResource("block/schematicannon/pipe")));
		context.addModel(BARREL,
			SimpleUnbakedExtraModel.blockStateModel(SchematicCannon.asResource("block/schematicannon/barrel")));
	}

	private static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();
	private static final int[] NO_TINTS = new int[0];

	private final ItemModelResolver itemModelResolver;
	private final BlockModelResolver blockModelResolver;

	public SchematicannonRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.itemModelResolver();
		this.blockModelResolver = context.blockModelResolver();
	}

	public static class RenderState extends BlockEntityRenderState {
		float yaw;
		float pitch;
		float recoil;
		final List<FlyingItem> flyingItems = new ArrayList<>();
	}

	/**
	 * A block or item in flight, positioned relative to the cannon.
	 */
	record FlyingItem(Vec3 location, float progress, @Nullable BlockModelRenderState block,
		@Nullable ItemStackRenderState item) {
	}

	@Override
	public RenderState createRenderState() {
		return new RenderState();
	}

	@Override
	public void extractRenderState(SchematicannonBlockEntity blockEntity, RenderState state, float partialTicks,
		Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
		BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPos, crumblingOverlay);

		double[] cannonAngles = getCannonAngles(blockEntity, blockEntity.getBlockPos(), partialTicks);
		state.yaw = (float) cannonAngles[0];
		state.pitch = (float) cannonAngles[1];
		state.recoil = (float) getRecoil(blockEntity, partialTicks);

		state.flyingItems.clear();
		for (LaunchedItem launched : blockEntity.flyingBlocks)
			if (launched.ticksRemaining != 0)
				extractLaunchedItem(blockEntity, launched, state, partialTicks);
	}

	private void extractLaunchedItem(SchematicannonBlockEntity blockEntity, LaunchedItem launched, RenderState state,
		float partialTicks) {
		// Calculate position of flying block
		Vec3 start = Vec3.atCenterOf(blockEntity.getBlockPos()
			.above());
		Vec3 target = Vec3.atCenterOf(launched.target);
		Vec3 distance = target.subtract(start);

		double yDifference = target.y - start.y;
		double throwHeight = Math.sqrt(distance.lengthSqr()) * .6f + yDifference;
		Vec3 cannonOffset = distance.add(0, throwHeight, 0)
			.normalize()
			.scale(2);
		start = start.add(cannonOffset);
		yDifference = target.y - start.y;

		float progress = ((float) launched.totalTicks - (launched.ticksRemaining + 1 - partialTicks)) / launched.totalTicks;
		Vec3 blockLocationXZ = target.subtract(start)
			.scale(progress)
			.multiply(1, 0, 1);

		// Height is determined through a bezier curve
		double yOffset = 2 * (1 - progress) * progress * throwHeight + progress * progress * yDifference;
		Vec3 blockLocation = blockLocationXZ.add(0.5, yOffset + 1.5, 0.5)
			.add(cannonOffset);

		if (launched instanceof LaunchedItem.ForBlockState forBlockState) {
			BlockModelRenderState block = new BlockModelRenderState();
			blockModelResolver.update(block, forBlockState.state, BLOCK_DISPLAY_CONTEXT);
			state.flyingItems.add(new FlyingItem(blockLocation, progress, block, null));
		} else if (launched instanceof LaunchedItem.ForEntity) {
			ItemStackRenderState item = new ItemStackRenderState();
			itemModelResolver.updateForTopItem(item, launched.stack, ItemDisplayContext.GROUND, blockEntity.getLevel(),
				null, 0);
			state.flyingItems.add(new FlyingItem(blockLocation, progress, null, item));
		}

		// Spawn particles for the launch
		if (launched.ticksRemaining == launched.totalTicks && blockEntity.firstRenderTick) {
			start = start.subtract(.5, .5, .5);
			blockEntity.firstRenderTick = false;
			RandomSource r = blockEntity.getLevel()
				.getRandom();
			for (int i = 0; i < 10; i++) {
				double sX = cannonOffset.x * .01f;
				double sY = (cannonOffset.y + 1) * .01f;
				double sZ = cannonOffset.z * .01f;
				double rX = r.nextFloat() - sX * 40;
				double rY = r.nextFloat() - sY * 40;
				double rZ = r.nextFloat() - sZ * 40;
				blockEntity.getLevel()
					.addParticle(ParticleTypes.CLOUD, start.x + rX, start.y + rY, start.z + rZ, sX, sY, sZ);
			}
		}
	}

	@Override
	public void submit(RenderState state, PoseStack ms, SubmitNodeCollector collector, CameraRenderState camera) {
		for (FlyingItem flyingItem : state.flyingItems)
			submitFlyingItem(flyingItem, state, ms, collector);

		ms.pushPose();
		ms.translate(.5f, 0, .5f);
		ms.rotateDegrees(Axis.YP, state.yaw + 90);
		ms.translate(-.5f, 0, -.5f);
		submitModel(CONNECTOR, state, ms, collector);
		ms.popPose();

		ms.pushPose();
		ms.translate(.5f, 15 / 16f, .5f);
		ms.rotateDegrees(Axis.YP, state.yaw + 90);
		ms.rotateDegrees(Axis.ZP, state.pitch);
		ms.translate(-.5f, -15 / 16f, -.5f);
		submitModel(PIPE, state, ms, collector);
		// Only the slim barrel recoils, sliding into the breech
		ms.translate(0, -state.recoil / 100, 0);
		submitModel(BARREL, state, ms, collector);
		ms.popPose();
	}

	private static void submitModel(ExtraModelKey<BlockStateModel> key, RenderState state, PoseStack ms,
		SubmitNodeCollector collector) {
		BlockStateModel model = Minecraft.getInstance()
			.getModelManager()
			.getModel(key);
		if (model == null)
			return;
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(42), parts);
		collector.submitBlockModel(ms, Sheets.cutoutBlockItemSheet(), parts, NO_TINTS, state.lightCoords,
			OverlayTexture.NO_OVERLAY, 0);
	}

	private static void submitFlyingItem(FlyingItem flyingItem, RenderState state, PoseStack ms,
		SubmitNodeCollector collector) {
		Vec3 location = flyingItem.location();
		float t = flyingItem.progress();

		ms.pushPose();
		ms.translate(location.x, location.y, location.z);
		ms.translate(.125f, .125f, .125f);
		ms.rotateDegrees(Axis.YP, 360 * t);
		ms.rotateDegrees(Axis.XP, 360 * t);
		ms.translate(-.125f, -.125f, -.125f);

		if (flyingItem.block() != null) {
			float scale = .3f;
			ms.scale(scale, scale, scale);
			flyingItem.block()
				.submit(ms, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		} else if (flyingItem.item() != null) {
			float scale = 1.2f;
			ms.scale(scale, scale, scale);
			flyingItem.item()
				.submit(ms, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		}

		ms.popPose();
	}

	public static double[] getCannonAngles(SchematicannonBlockEntity blockEntity, BlockPos pos, float partialTicks) {
		double yaw;
		double pitch;

		BlockPos target = blockEntity.printer.getCurrentTarget();
		if (target != null) {

			// Calculate Angle of Cannon
			Vec3 diff = Vec3.atLowerCornerOf(target.subtract(pos));
			if (blockEntity.previousTarget != null) {
				diff = (Vec3.atLowerCornerOf(blockEntity.previousTarget)
					.add(Vec3.atLowerCornerOf(target.subtract(blockEntity.previousTarget))
						.scale(partialTicks))).subtract(Vec3.atLowerCornerOf(pos));
			}

			double diffX = diff.x();
			double diffZ = diff.z();
			yaw = Mth.atan2(diffX, diffZ);
			yaw = yaw / Math.PI * 180;

			float distance = Mth.sqrt((float) (diffX * diffX + diffZ * diffZ));
			double yOffset = distance * 2f;
			pitch = Mth.atan2(distance, diff.y() * 3 + yOffset);
			pitch = pitch / Math.PI * 180 + 10;

		} else {
			yaw = blockEntity.defaultYaw;
			pitch = 40;
		}

		return new double[] { yaw, pitch };
	}

	public static double getRecoil(SchematicannonBlockEntity blockEntity, float partialTicks) {
		double recoil = 0;

		for (LaunchedItem launched : blockEntity.flyingBlocks) {
			if (launched.ticksRemaining == 0)
				continue;

			// Apply Recoil if block was just launched
			if ((launched.ticksRemaining + 1 - partialTicks) > launched.totalTicks - 10)
				recoil = Math.max(recoil, (launched.ticksRemaining + 1 - partialTicks) - launched.totalTicks + 10);
		}

		return recoil;
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 128;
	}

}
