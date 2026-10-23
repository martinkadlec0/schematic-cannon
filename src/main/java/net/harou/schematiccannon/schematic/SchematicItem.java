package net.harou.schematiccannon.schematic;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

import net.harou.schematiccannon.SchematicCannon;
import net.harou.schematiccannon.client.SchematicCannonClient;
import net.harou.schematiccannon.registry.ModDataComponents;
import net.harou.schematiccannon.registry.ModItems;
import net.harou.schematiccannon.util.Lang;
import net.harou.schematiccannon.util.SchematicFiles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class SchematicItem extends Item {

	public SchematicItem(Properties properties) {
		super(properties);
	}

	public static ItemStack create(Level level, String schematic, String owner) {
		ItemStack blueprint = new ItemStack(ModItems.SCHEMATIC);

		blueprint.set(ModDataComponents.SCHEMATIC_DEPLOYED, false);
		blueprint.set(ModDataComponents.SCHEMATIC_OWNER, owner);
		blueprint.set(ModDataComponents.SCHEMATIC_FILE, schematic);
		blueprint.set(ModDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO);
		blueprint.set(ModDataComponents.SCHEMATIC_ROTATION, Rotation.NONE);
		blueprint.set(ModDataComponents.SCHEMATIC_MIRROR, Mirror.NONE);

		writeSize(level, blueprint);
		return blueprint;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
		TooltipFlag flag) {
		if (stack.has(ModDataComponents.SCHEMATIC_FILE))
			tooltip.accept(Component.literal(stack.get(ModDataComponents.SCHEMATIC_FILE))
				.withStyle(ChatFormatting.GOLD));
		else
			tooltip.accept(Lang.translate("schematic.invalid")
				.withStyle(ChatFormatting.RED));
		super.appendHoverText(stack, context, display, tooltip, flag);
	}

	public static void writeSize(Level level, ItemStack blueprint) {
		StructureTemplate t = loadSchematic(level, blueprint);
		blueprint.set(ModDataComponents.SCHEMATIC_BOUNDS, t.getSize());
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

	public static StructureTemplate loadSchematic(Level level, ItemStack blueprint) {
		StructureTemplate t = new StructureTemplate();
		String owner = blueprint.get(ModDataComponents.SCHEMATIC_OWNER);
		String schematic = blueprint.get(ModDataComponents.SCHEMATIC_FILE);

		if (owner == null || schematic == null || !schematic.endsWith(".nbt"))
			return t;

		try {
			Path dir;
			Path file;
			if (!level.isClientSide()) {
				dir = SchematicFiles.UPLOADED_SCHEMATICS_DIR;
				file = Paths.get(owner, schematic);
			} else {
				dir = SchematicFiles.SCHEMATICS_DIR;
				file = Paths.get(schematic);
			}

			Path path = dir.resolve(file)
				.normalize();
			if (!path.startsWith(dir))
				return t;

			CompoundTag nbt = NbtIo.readCompressed(path, NbtAccounter.create(0x20000000L));
			// Like vanilla structure loading: upgrade files saved by older versions
			nbt = DataFixTypes.STRUCTURE.updateToCurrentVersion(DataFixers.getDataFixer(), nbt,
				NbtUtils.getDataVersion(nbt, 500));
			t.load(level.holderLookup(Registries.BLOCK), nbt);
		} catch (Exception e) {
			SchematicCannon.LOGGER.warn("Failed to read schematic", e);
		}

		return t;
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
		if (!player.getItemInHand(hand)
			.has(ModDataComponents.SCHEMATIC_FILE))
			return false;
		if (player.level()
			.isClientSide())
			SchematicCannonClient.openSchematicEditScreen();
		return true;
	}

}
