package net.harou.schematiccannon.registry;

import java.util.function.UnaryOperator;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.cannon.SchematicannonBlockEntity.SchematicannonOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

public class ModDataComponents {

	public static final StreamCodec<ByteBuf, Mirror> MIRROR_STREAM_CODEC =
		ByteBufCodecs.idMapper(ByIdMap.continuous(Mirror::ordinal, Mirror.values(), ByIdMap.OutOfBoundsStrategy.ZERO),
			Mirror::ordinal);

	/**
	 * Marks every schematic item (a default component of {@link net.harou.schematiccannon.schematic.SchematicItem}), so
	 * the schematic tooltip has a component to hang on even before the schematic is set up
	 */
	public static final DataComponentType<Unit> SCHEMATIC = register("schematic",
		builder -> builder.persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

	public static final DataComponentType<Boolean> SCHEMATIC_DEPLOYED = register("schematic_deployed",
		builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

	/** UUID of the player who uploaded the schematic file, the folder it is stored in on the server */
	public static final DataComponentType<String> SCHEMATIC_OWNER = register("schematic_owner",
		builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

	public static final DataComponentType<String> SCHEMATIC_FILE = register("schematic_file",
		builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

	/** The {@link net.harou.schematiccannon.schematic.source.SchematicSource} of the stack, the file source if missing */
	public static final DataComponentType<Identifier> SCHEMATIC_SOURCE = register("schematic_source",
		builder -> builder.persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC));

	public static final DataComponentType<BlockPos> SCHEMATIC_ANCHOR = register("schematic_anchor",
		builder -> builder.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC));

	/**
	 * The id of the client's sync that set the placement, so the client can tell a late answer to an earlier sync of its
	 * own from a placement set elsewhere. Only while playing, not saved.
	 */
	public static final DataComponentType<Integer> SCHEMATIC_SYNC_ID = register("schematic_sync_id",
		builder -> builder.networkSynchronized(ByteBufCodecs.INT));

	public static final DataComponentType<Rotation> SCHEMATIC_ROTATION = register("schematic_rotation",
		builder -> builder.persistent(Rotation.CODEC).networkSynchronized(Rotation.STREAM_CODEC));

	public static final DataComponentType<Mirror> SCHEMATIC_MIRROR = register("schematic_mirror",
		builder -> builder.persistent(Mirror.CODEC).networkSynchronized(MIRROR_STREAM_CODEC));

	public static final DataComponentType<Vec3i> SCHEMATIC_BOUNDS = register("schematic_bounds",
		builder -> builder.persistent(Vec3i.CODEC).networkSynchronized(Vec3i.STREAM_CODEC));

	public static final DataComponentType<SchematicannonOptions> SCHEMATICANNON_OPTIONS = register("schematicannon_options",
		builder -> builder.persistent(SchematicannonOptions.CODEC).networkSynchronized(SchematicannonOptions.STREAM_CODEC));

	private static <T> DataComponentType<T> register(String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, SchematicCannon.asResource(name),
			builder.apply(DataComponentType.builder()).build());
	}

	public static void init() {
	}

}
