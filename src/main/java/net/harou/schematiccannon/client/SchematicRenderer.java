package net.harou.schematiccannon.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.client.render.VertexRecorder;
import net.harou.schematiccannon.schematic.level.SchematicLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Renders a schematic preview. Block models are tessellated once per render layer into a vertex list that is replayed
 * every frame, block entities are drawn through their renderers.
 */
public class SchematicRenderer {

	private final Map<RenderType, VertexRecorder> meshes = new LinkedHashMap<>();
	private final SchematicLevel schematic;
	private final BlockPos anchor;
	private final List<BlockEntity> renderedBlockEntities = new ArrayList<>();
	private boolean changed;
	private boolean ambientOcclusion;
	private boolean cutoutLeaves;

	// Grass, foliage and water colors come from the biomes where the schematic is shown
	private static final long TINT_REDRAW_DELAY_MS = 300;
	private final Supplier<Matrix4fc> localToWorld;
	private final Matrix4f tintedAt = new Matrix4f();
	private final Matrix4f movedTo = new Matrix4f();
	private long movedAt;

	/**
	 * @param localToWorld where the schematic is shown, for biome tints
	 */
	public SchematicRenderer(SchematicLevel world, Supplier<Matrix4fc> localToWorld) {
		this.anchor = world.anchor;
		this.schematic = world;
		this.localToWorld = localToWorld;
		this.changed = true;
		world.getRenderedBlockEntities()
			.forEach(renderedBlockEntities::add);
	}

	public void update() {
		changed = true;
	}

	public void render(PoseStack ms, SubmitNodeCollector collector, CameraRenderState cameraState, Vec3 camera,
		float pt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.ambientOcclusion()
			.get() != ambientOcclusion || mc.options.cutoutLeaves()
				.get() != cutoutLeaves)
			changed = true;
		checkMoved();
		if (changed)
			redraw();
		changed = false;

		meshes.forEach((layer, mesh) -> collector.submitCustomGeometry(ms, layer, mesh::renderInto));

		renderBlockEntities(ms, collector, cameraState, camera, pt);
	}

	/**
	 * Redraws with the new biome tints once the schematic stopped moving, not on every step of a move.
	 */
	private void checkMoved() {
		Matrix4fc current = localToWorld.get();
		if (current.equals(tintedAt, 1e-3f))
			return;
		if (!current.equals(movedTo, 1e-3f)) {
			movedTo.set(current);
			movedAt = Util.getMillis();
		} else if (Util.getMillis() - movedAt > TINT_REDRAW_DELAY_MS)
			changed = true;
	}

	private void renderBlockEntities(PoseStack ms, SubmitNodeCollector collector, CameraRenderState cameraState,
		Vec3 camera, float pt) {
		var dispatcher = Minecraft.getInstance()
			.getBlockEntityRenderDispatcher();
		renderedBlockEntities.removeIf(blockEntity -> {
			BlockEntityRenderer<BlockEntity, BlockEntityRenderState> renderer = dispatcher.getRenderer(blockEntity);
			if (renderer == null)
				return false;
			BlockPos pos = blockEntity.getBlockPos();
			ms.pushPose();
			ms.translate(pos.getX(), pos.getY(), pos.getZ());
			try {
				BlockEntityRenderState state = renderer.createRenderState();
				renderer.extractRenderState(blockEntity, state, pt, camera, null);
				state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
				renderer.submit(state, ms, collector, cameraState);
				return false;
			} catch (Exception e) {
				// Some renderers expect a real level, don't try them again
				SchematicCannon.LOGGER.debug("Could not render block entity in schematic preview", e);
				return true;
			} finally {
				ms.popPose();
			}
		});
	}

	protected void redraw() {
		meshes.clear();

		Minecraft mc = Minecraft.getInstance();
		BlockStateModelSet models = mc.getModelManager()
			.getBlockStateModelSet();
		ambientOcclusion = mc.options.ambientOcclusion()
			.get();
		cutoutLeaves = mc.options.cutoutLeaves()
			.get();
		ModelBlockRenderer blockRenderer = new ModelBlockRenderer(ambientOcclusion, true, mc.getBlockColors());
		BlockQuadOutput output = (x, y, z, quad, instance) -> getMesh(quad.materialInfo()
			.layer()).putBlockBakedQuad(x, y, z, quad, instance);
		BlockQuadOutput solidOutput =
			(x, y, z, quad, instance) -> getMesh(ChunkSectionLayer.SOLID).putBlockBakedQuad(x, y, z, quad, instance);
		tintedAt.set(localToWorld.get());
		movedTo.set(tintedAt);
		BlockAndTintGetter level = new RenderLevel(schematic, mc.level, new Matrix4f(tintedAt), anchor);
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
		BoundingBox bounds = schematic.getBounds();

		schematic.renderMode = true;
		for (BlockPos localPos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(),
			bounds.maxY(), bounds.maxZ())) {
			BlockPos pos = mutableBlockPos.setWithOffset(localPos, anchor);
			BlockState state = schematic.getBlockState(pos);
			if (state.getRenderShape() != RenderShape.MODEL)
				continue;
			blockRenderer.tesselateBlock(ModelBlockRenderer.forceOpaque(cutoutLeaves, state) ? solidOutput : output,
				localPos.getX(), localPos.getY(), localPos.getZ(), level, pos, state, models.get(state), state.getSeed(pos));
		}
		schematic.renderMode = false;
	}

	private VertexRecorder getMesh(ChunkSectionLayer layer) {
		return meshes.computeIfAbsent(switch (layer) {
			case SOLID -> RenderTypes.solidMovingBlock();
			case CUTOUT -> RenderTypes.cutoutMovingBlock();
			case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
		}, $ -> new VertexRecorder());
	}

	/**
	 * Client-side view of the schematic for block tessellation: full bright, tinted by the biomes of the real level.
	 */
	private record RenderLevel(SchematicLevel schematic, BlockAndTintGetter level, Matrix4fc localToWorld,
		BlockPos anchor) implements BlockAndTintGetter {
		@Override
		public CardinalLighting cardinalLighting() {
			return level.cardinalLighting();
		}

		@Override
		public int getBlockTint(BlockPos pos, ColorResolver color) {
			Vector3f world = localToWorld.transformPosition(pos.getX() - anchor.getX() + .5f,
				pos.getY() - anchor.getY() + .5f, pos.getZ() - anchor.getZ() + .5f, new Vector3f());
			return level.getBlockTint(BlockPos.containing(world.x, world.y, world.z), color);
		}

		@Override
		public LevelLightEngine getLightEngine() {
			return LevelLightEngine.EMPTY;
		}

		@Override
		public int getBrightness(LightLayer layer, BlockPos pos) {
			return 15;
		}

		@Override
		public BlockEntity getBlockEntity(BlockPos pos) {
			return schematic.getBlockEntity(pos);
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			return schematic.getBlockState(pos);
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return schematic.getFluidState(pos);
		}

		@Override
		public int getHeight() {
			return schematic.getHeight();
		}

		@Override
		public int getMinY() {
			return schematic.getMinY();
		}
	}

}
