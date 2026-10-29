package net.harou.schematiccannon.schematic;

import net.harou.schematiccannon.ModConfig;
import net.harou.schematiccannon.client.SchematicCannonClient;
import net.harou.schematiccannon.registry.ModDataComponents;
import net.harou.schematiccannon.registry.ModItems;
import net.harou.schematiccannon.schematic.source.SchematicSource;
import net.harou.schematiccannon.schematic.source.SchematicSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A schematic that can be deployed and printed. Other mods may extend it for schematics from their own
 * {@link SchematicSource}.
 */
public class SchematicItem extends Item {

	public SchematicItem(Properties properties) {
		// Every schematic item, also of other mods, gets the marker the tooltip hangs on
		super(properties.component(ModDataComponents.SCHEMATIC, Unit.INSTANCE));
	}

	/**
	 * Whether the stacks are the same schematic placed the same way. The sync id doesn't count: it changes with every
	 * sync of the placement, even one that keeps it (see {@link ModDataComponents#SCHEMATIC_SYNC_ID}).
	 */
	public static boolean isSame(ItemStack a, ItemStack b) {
		return ItemStack.matches(withoutSyncId(a), withoutSyncId(b));
	}

	private static ItemStack withoutSyncId(ItemStack stack) {
		if (!stack.has(ModDataComponents.SCHEMATIC_SYNC_ID))
			return stack;
		ItemStack copy = stack.copy();
		copy.remove(ModDataComponents.SCHEMATIC_SYNC_ID);
		return copy;
	}

	/**
	 * Deploying, moving and other changes edit the schematic in hand all the time; the hand shouldn't dip each time.
	 */
	@Override
	public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack,
		ItemStack newStack) {
		return false;
	}

	/**
	 * @param owner UUID of the player who uploaded the file, see {@link ModDataComponents#SCHEMATIC_OWNER}
	 */
	public static ItemStack create(Level level, String schematic, String owner) {
		ItemStack blueprint = new ItemStack(ModItems.SCHEMATIC);
		blueprint.set(ModDataComponents.SCHEMATIC_OWNER, owner);
		blueprint.set(ModDataComponents.SCHEMATIC_FILE, schematic);
		initPlacement(blueprint);
		writeSize(level, blueprint);
		return blueprint;
	}

	/**
	 * Sets the placement components of a new, undeployed schematic.
	 */
	public static void initPlacement(ItemStack blueprint) {
		blueprint.set(ModDataComponents.SCHEMATIC_DEPLOYED, false);
		blueprint.set(ModDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO);
		blueprint.set(ModDataComponents.SCHEMATIC_ROTATION, Rotation.NONE);
		blueprint.set(ModDataComponents.SCHEMATIC_MIRROR, Mirror.NONE);
	}

	/**
	 * Whether the stack is a schematic with a structure, so it can be shown and printed.
	 */
	public static boolean isValid(ItemStack stack) {
		return stack.getItem() instanceof SchematicItem && stack.has(ModDataComponents.SCHEMATIC_BOUNDS);
	}

	public static SchematicSource getSource(ItemStack stack) {
		return SchematicSources.get(stack);
	}

	/**
	 * Stores the size of the structure on the stack. Only works on the server.
	 */
	public static void writeSize(Level level, ItemStack blueprint) {
		StructureTemplate t = loadSchematic(level, blueprint);
		blueprint.set(ModDataComponents.SCHEMATIC_BOUNDS, t.getSize());
	}

	/**
	 * The stack in the player's inventory slot, -1 for the main hand, as sent by the client; empty if there is no such
	 * slot.
	 */
	public static ItemStack getInSlot(ServerPlayer player, int slot) {
		if (slot < -1 || slot >= player.getInventory()
			.getContainerSize())
			return ItemStack.EMPTY;
		return slot == -1 ? player.getMainHandItem()
			: player.getInventory()
				.getItem(slot);
	}

	/**
	 * Whether the stack prints frozen: placed without updates and settled once complete, see
	 * {@link SchematicSource#prefersFrozenPrint}.
	 */
	public static boolean isFrozenPrint(ItemStack stack) {
		return ModConfig.get().alwaysFrozenPrint || getSource(stack).prefersFrozenPrint(stack);
	}

	/**
	 * Where the deployed schematic with the given size is in the world, at the stack's anchor, rotation and mirror.
	 */
	public static BoundingBox getWorldBounds(ItemStack stack, Vec3i size) {
		return getWorldBounds(stack.getOrDefault(ModDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO),
			stack.getOrDefault(ModDataComponents.SCHEMATIC_ROTATION, Rotation.NONE),
			stack.getOrDefault(ModDataComponents.SCHEMATIC_MIRROR, Mirror.NONE), size);
	}

	/**
	 * Where a schematic of the given size is in the world when placed at the anchor, like
	 * {@link StructureTemplate#getBoundingBox} computes it.
	 */
	public static BoundingBox getWorldBounds(BlockPos anchor, Rotation rotation, Mirror mirror, Vec3i size) {
		BlockPos corner1 = StructureTemplate.transform(BlockPos.ZERO, mirror, rotation, BlockPos.ZERO);
		BlockPos corner2 = StructureTemplate.transform(BlockPos.ZERO.offset(size.offset(-1, -1, -1)), mirror, rotation,
			BlockPos.ZERO);
		return BoundingBox.fromCorners(corner1, corner2)
			.moved(anchor.getX(), anchor.getY(), anchor.getZ());
	}

	public static StructurePlaceSettings getSettings(ItemStack blueprint) {
		return getSettings(blueprint, true);
	}

	public static StructurePlaceSettings getSettings(ItemStack blueprint, boolean processNBT) {
		StructurePlaceSettings settings = new StructurePlaceSettings();
		settings.setRotation(blueprint.getOrDefault(ModDataComponents.SCHEMATIC_ROTATION, Rotation.NONE));
		settings.setMirror(blueprint.getOrDefault(ModDataComponents.SCHEMATIC_MIRROR, Mirror.NONE));
		if (processNBT)
			settings.addProcessor(SchematicProcessor.INSTANCE);
		return settings;
	}

	/**
	 * Loads the structure of the stack from its source. Templates only exist on the server, clients get them through
	 * {@link net.harou.schematiccannon.client.ClientSchematicCache}.
	 */
	public static StructureTemplate loadSchematic(Level level, ItemStack blueprint) {
		if (!(level instanceof ServerLevel serverLevel))
			return new StructureTemplate();
		return getSource(blueprint).load(serverLevel, blueprint);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getPlayer() != null && !onItemUse(context.getPlayer(), context.getHand()))
			return super.useOn(context);
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!onItemUse(player, hand))
			return super.use(level, player, hand);
		return InteractionResult.SUCCESS;
	}

	private boolean onItemUse(Player player, InteractionHand hand) {
		if (!player.isShiftKeyDown() || hand != InteractionHand.MAIN_HAND)
			return false;
		if (!isValid(player.getItemInHand(hand)))
			return false;
		if (player.level()
			.isClientSide())
			openEditScreen(player.getItemInHand(hand));
		return true;
	}

	/**
	 * Called on the client on shift + right click. Opens the screen for typing in the placement.
	 */
	protected void openEditScreen(ItemStack stack) {
		SchematicCannonClient.openSchematicEditScreen();
	}

}
