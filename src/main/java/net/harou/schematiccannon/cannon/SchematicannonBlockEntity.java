package net.harou.schematiccannon.cannon;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiCache;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.harou.schematiccannon.ModConfig;
import net.harou.schematiccannon.foundation.MenuOpenData;
import net.harou.schematiccannon.foundation.SyncedBlockEntity;
import net.harou.schematiccannon.network.ConfigureSchematicannonPacket;
import net.harou.schematiccannon.registry.ModBlockEntityTypes;
import net.harou.schematiccannon.registry.ModDataComponents;
import net.harou.schematiccannon.registry.ModItems;
import net.harou.schematiccannon.schematic.ItemRequirement;
import net.harou.schematiccannon.schematic.SchematicPrinter;
import net.harou.schematiccannon.util.BlockHelper;
import net.harou.schematiccannon.util.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.Bees;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class SchematicannonBlockEntity extends SyncedBlockEntity
	implements ExtendedMenuProvider<MenuOpenData>, Clearable {

	public static final int MAX_ANCHOR_DISTANCE = 256;

	// Inventory
	public SchematicannonInventory inventory;

	public boolean sendUpdate;
	// Sync
	public boolean dontUpdateChecklist;

	// Printer
	public SchematicPrinter printer;
	public ItemStack missingItem;
	public boolean positionNotLoaded;
	// Paused because of missing items, fuel or chunks, not by the player
	public boolean autoPaused;
	// The schematic the printer was loaded from
	private ItemStack loadedBlueprint = ItemStack.EMPTY;
	private int printerCooldown;
	private int skipsLeft;
	private boolean blockSkipped;

	public BlockPos previousTarget;
	public List<LaunchedItem> flyingBlocks;
	public MaterialChecklist checklist;

	// Gui information
	public int remainingFuel;
	public float bookPrintingProgress;
	public float schematicProgress;
	public String statusMsg;
	public State state;
	public int blocksPlaced;
	public int blocksToPlace;

	// Settings
	public int replaceMode;
	public boolean skipMissing;
	public boolean replaceBlockEntities;

	@Nullable
	private List<BlockApiCache<Storage<ItemVariant>, Direction>> storageCaches;

	// Render
	public boolean firstRenderTick;
	public float defaultYaw;

	public SchematicannonBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntityTypes.SCHEMATICANNON, pos, state);
		flyingBlocks = new LinkedList<>();
		inventory = new SchematicannonInventory(this);
		statusMsg = "idle";
		this.state = State.STOPPED;
		replaceMode = SchematicannonOptions.DEFAULT.replaceMode;
		skipMissing = SchematicannonOptions.DEFAULT.skipMissing;
		replaceBlockEntities = SchematicannonOptions.DEFAULT.replaceBlockEntities;
		checklist = new MaterialChecklist();
		printer = new SchematicPrinter();
	}

	/**
	 * The inventories next to the cannon. Lookups are cached, they run every tick while printing.
	 */
	protected List<Storage<ItemVariant>> getAttachedStorages() {
		if (storageCaches == null) {
			storageCaches = new ArrayList<>(6);
			for (Direction direction : Direction.values())
				storageCaches.add(BlockApiCache.create(ItemStorage.SIDED, (ServerLevel) level,
					worldPosition.relative(direction)));
		}

		List<Storage<ItemVariant>> storages = new ArrayList<>(6);
		for (Direction direction : Direction.values()) {
			if (!level.isLoaded(worldPosition.relative(direction)))
				continue;
			Storage<ItemVariant> storage = storageCaches.get(direction.ordinal())
				.find(direction.getOpposite());
			if (storage != null)
				storages.add(storage);
		}
		return storages;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null)
			Containers.dropContents(level, pos, inventory);
	}

	@Override
	public void clearContent() {
		inventory.clearContent();
	}

	@Override
	protected void read(ValueInput input, boolean clientPacket) {
		if (!clientPacket) {
			NonNullList<ItemStack> items = NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
			ContainerHelper.loadAllItems(input.childOrEmpty("Inventory"), items);
			for (int i = 0; i < items.size(); i++)
				inventory.setItem(i, items.get(i));
		}

		// Gui information
		statusMsg = input.getStringOr("Status", "idle");
		schematicProgress = input.getFloatOr("Progress", 0);
		bookPrintingProgress = input.getFloatOr("PaperProgress", 0);
		remainingFuel = input.getIntOr("RemainingFuel", 0);
		String stateString = input.getStringOr("State", "");
		state = stateString.isEmpty() ? State.STOPPED : State.valueOf(stateString);
		blocksPlaced = input.getIntOr("AmountPlaced", 0);
		blocksToPlace = input.getIntOr("AmountToPlace", 0);

		missingItem = input.read("MissingItem", ItemStack.CODEC)
			.orElse(null);

		// Settings
		SchematicannonOptions options = input.read("Options", SchematicannonOptions.CODEC)
			.orElse(SchematicannonOptions.DEFAULT);
		replaceMode = options.replaceMode;
		skipMissing = options.skipMissing;
		replaceBlockEntities = options.replaceBlockEntities;

		// Printer & Flying Blocks
		if (clientPacket)
			input.child("Printer")
				.ifPresent(printer::read);
		input.childrenList("FlyingBlocks")
			.ifPresent(this::readFlyingBlocks);

		defaultYaw = input.getFloatOr("DefaultYaw", 0);
	}

	protected void readFlyingBlocks(ValueInput.ValueInputList list) {
		if (list.isEmpty())
			flyingBlocks.clear();

		boolean pastDead = false;
		int i = 0;

		for (ValueInput entry : list) {
			LaunchedItem launched = LaunchedItem.read(entry);
			BlockPos readBlockPos = launched.target;

			// Always write to Server block entity
			if (level == null || !level.isClientSide()) {
				flyingBlocks.add(launched);
				continue;
			}

			// Delete all Client side blocks that are now missing on the server
			while (!pastDead && !flyingBlocks.isEmpty() && !flyingBlocks.get(0).target.equals(readBlockPos))
				flyingBlocks.remove(0);

			pastDead = true;

			// Add new server side blocks
			if (i >= flyingBlocks.size())
				flyingBlocks.add(launched);

			// Don't do anything with existing
			i++;
		}
	}

	@Override
	protected void write(ValueOutput output, boolean clientPacket) {
		if (!clientPacket) {
			NonNullList<ItemStack> items = NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
			for (int i = 0; i < items.size(); i++)
				items.set(i, inventory.getItem(i));
			ContainerHelper.saveAllItems(output.child("Inventory"), items);
		}

		// Gui information
		output.putFloat("Progress", schematicProgress);
		output.putFloat("PaperProgress", bookPrintingProgress);
		output.putInt("RemainingFuel", remainingFuel);
		output.putString("Status", statusMsg);
		output.putString("State", state.name());
		output.putInt("AmountPlaced", blocksPlaced);
		output.putInt("AmountToPlace", blocksToPlace);

		if (clientPacket && missingItem != null && !missingItem.isEmpty())
			output.store("MissingItem", ItemStack.CODEC, missingItem);

		// Settings
		output.store("Options", SchematicannonOptions.CODEC,
			new SchematicannonOptions(replaceMode, skipMissing, replaceBlockEntities));

		// Printer & Flying Blocks
		if (clientPacket)
			printer.write(output.child("Printer"));

		ValueOutput.ValueOutputList flyingBlocksOutput = output.childrenList("FlyingBlocks");
		for (LaunchedItem b : flyingBlocks)
			b.write(flyingBlocksOutput.addChild());

		output.putFloat("DefaultYaw", defaultYaw);
	}

	public void tick() {
		firstRenderTick = true;
		previousTarget = printer.getCurrentTarget();
		tickFlyingBlocks();

		if (level.isClientSide())
			return;

		checkBlueprint();

		// Update Fuel and Paper
		tickPaperPrinter();
		if (state != State.STOPPED)
			refillFuelIfPossible();

		// Update Printer
		skipsLeft = 1000;
		blockSkipped = true;

		while (blockSkipped && skipsLeft-- > 0)
			tickPrinter();

		schematicProgress = 0;
		if (blocksToPlace > 0)
			schematicProgress = (float) blocksPlaced / blocksToPlace;

		// Update Client block entity
		if (sendUpdate) {
			sendUpdate = false;
			sendData();
		}
	}

	/**
	 * Stops printing when the schematic was taken out or replaced by another one.
	 */
	protected void checkBlueprint() {
		ItemStack blueprint = inventory.getItem(SchematicannonInventory.SCHEMATIC_INPUT);
		if (blueprint.isEmpty()) {
			if (printer.isLoaded())
				resetPrinter();
			boolean keepStatus = statusMsg.equals("finished") || statusMsg.equals("schematicErrored")
				|| statusMsg.equals("schematicExpired");
			if (state != State.STOPPED || !statusMsg.equals("idle") && !keepStatus) {
				state = State.STOPPED;
				autoPaused = false;
				if (!keepStatus)
					statusMsg = "idle";
				sendUpdate = true;
			}
			return;
		}
		if (printer.isLoaded() && !ItemStack.matches(blueprint, loadedBlueprint)) {
			resetPrinter();
			state = State.STOPPED;
			autoPaused = false;
		}
	}

	protected void tickPrinter() {
		ItemStack blueprint = inventory.getItem(SchematicannonInventory.SCHEMATIC_INPUT);
		blockSkipped = false;
		if (blueprint.isEmpty())
			return;

		// Skip if not Active
		if (state == State.STOPPED) {
			if (printer.isLoaded())
				resetPrinter();
			return;
		}

		// Paused by the player; automatic pauses keep checking whether they can resume
		if (state == State.PAUSED && !autoPaused)
			return;

		if (!printer.isLoaded())
			return;

		// Cooldown from last shot
		if (printerCooldown > 0) {
			printerCooldown--;
			return;
		}

		// Check Fuel
		if (remainingFuel <= 0) {
			refillFuelIfPossible();
			if (remainingFuel <= 0) {
				autoPause("noGunpowder");
				return;
			}
		}

		// Update Target
		if (missingItem == null && !positionNotLoaded) {
			if (!printer.advanceCurrentPos()) {
				finishedPrinting();
				return;
			}
			sendUpdate = true;
		}

		// Check block; positions outside the build height can never be loaded
		BlockPos currentTarget = printer.getCurrentTarget();
		if (level.isOutsideBuildHeight(currentTarget) || !level.getWorldBorder()
			.isWithinBounds(currentTarget)) {
			blockSkipped = true;
			return;
		}
		if (!level.isLoaded(currentTarget)) {
			positionNotLoaded = true;
			autoPause("targetNotLoaded");
			return;
		}
		if (positionNotLoaded) {
			positionNotLoaded = false;
			resume();
		}

		// Get item requirement
		ItemRequirement requirement = printer.getCurrentRequirement();
		if (requirement.isInvalid() || !printer.shouldPlaceCurrent(level, this::shouldPlace)) {
			if (!statusMsg.equals("searching")) {
				statusMsg = "searching";
				sendUpdate = true;
			}
			blockSkipped = true;
			// The block we waited for is not needed anymore, e.g. it was placed by hand
			if (missingItem != null) {
				missingItem = null;
				resume();
			}
			return;
		}

		// Find item
		List<ItemRequirement.StackRequirement> requiredItems = requirement.getRequiredItems();
		if (!requirement.isEmpty()) {
			ItemRequirement.StackRequirement missing = grabItemsFromAttachedInventories(requiredItems);
			if (missing != null) {
				if (skipMissing) {
					statusMsg = "skipping";
					blockSkipped = true;
					if (missingItem != null) {
						missingItem = null;
						resume();
					}
					return;
				}

				if (missingItem == null || !ItemStack.matches(missingItem, missing.stack))
					sendUpdate = true;
				missingItem = missing.stack;
				autoPause("missingBlock");
				return;
			}
		}

		// Success
		resume();
		ItemStack icon = requirement.isEmpty() || requiredItems.isEmpty() ? ItemStack.EMPTY : requiredItems.get(0).stack;
		printer.handleCurrentTarget((target, blockState, blockEntity) -> {
			// Launch block
			statusMsg = blockState.getBlock() != Blocks.AIR ? "placing" : "clearing";
			launchBlock(target, icon, blockState, BlockHelper.prepareBlockEntityData(level, blockState, blockEntity));
		}, (target, entity) -> {
			// Launch entity
			statusMsg = "placing";
			launchEntity(target, icon, entity);
		});

		printerCooldown = ModConfig.get().schematicannonDelay;
		remainingFuel -= 1;
		sendUpdate = true;
		missingItem = null;
	}

	public int getShotsPerGunpowder() {
		return ModConfig.get().schematicannonShotsPerGunpowder;
	}

	/**
	 * Called every tick while a schematic is inserted but not loaded.
	 */
	protected void initializePrinter(ItemStack blueprint) {
		BlockPos anchor = blueprint.get(ModDataComponents.SCHEMATIC_ANCHOR);
		if (anchor == null) {
			stop("schematicInvalid");
			return;
		}

		if (!blueprint.getOrDefault(ModDataComponents.SCHEMATIC_DEPLOYED, false)) {
			stop("schematicNotPlaced");
			return;
		}

		if (!anchor.closerThan(getBlockPos(), MAX_ANCHOR_DISTANCE)) {
			stop("targetOutsideRange");
			return;
		}

		// Load blocks into reader
		printer.loadSchematic(blueprint, level, true);

		if (printer.isErrored() || printer.isWorldEmpty()) {
			state = State.STOPPED;
			statusMsg = printer.isErrored() ? "schematicErrored" : "schematicExpired";
			inventory.setItem(SchematicannonInventory.SCHEMATIC_INPUT, ItemStack.EMPTY);
			outputEmptySchematic();
			printer.resetSchematic();
			sendUpdate = true;
			return;
		}

		loadedBlueprint = blueprint.copy();
		missingItem = null;
		autoPaused = false;
		state = State.PAUSED;
		statusMsg = "ready";
		updateChecklist();
		sendUpdate = true;
	}

	private void stop(String status) {
		if (state == State.STOPPED && statusMsg.equals(status))
			return;
		state = State.STOPPED;
		autoPaused = false;
		statusMsg = status;
		sendUpdate = true;
	}

	/**
	 * Pauses until the reason is gone, unlike a pause by the player.
	 */
	private void autoPause(String status) {
		if (state == State.PAUSED && autoPaused && statusMsg.equals(status))
			return;
		state = State.PAUSED;
		autoPaused = true;
		statusMsg = status;
		sendUpdate = true;
	}

	private void resume() {
		state = State.RUNNING;
		autoPaused = false;
	}

	/**
	 * Adds to the empty schematics in the output slot, or drops the schematic when the slot is full or holds something else.
	 */
	private void outputEmptySchematic() {
		ItemStack emptySchematic = new ItemStack(ModItems.EMPTY_SCHEMATIC);
		ItemStack output = inventory.getItem(SchematicannonInventory.SCHEMATIC_OUTPUT);
		if (output.isEmpty())
			inventory.setItem(SchematicannonInventory.SCHEMATIC_OUTPUT, emptySchematic);
		else if (ItemStack.isSameItemSameComponents(output, emptySchematic) && output.getCount() < output.getMaxStackSize()) {
			output.grow(1);
			inventory.setChanged();
		} else
			Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + 1, worldPosition.getZ() + .5,
				emptySchematic);
	}

	/**
	 * Takes all required items from the attached inventories, or nothing if one of them is missing.
	 *
	 * @return the first requirement that could not be satisfied, or null if the items were taken
	 */
	@Nullable
	protected ItemRequirement.StackRequirement grabItemsFromAttachedInventories(
		List<ItemRequirement.StackRequirement> requiredItems) {
		List<Storage<ItemVariant>> storages = getAttachedStorages();
		try (Transaction transaction = Transaction.openOuter()) {
			for (ItemRequirement.StackRequirement required : requiredItems) {
				long needed = required.stack.getCount();
				// Plain items first, so e.g. a filled shulker box or a named item is only used when nothing else is there
				for (boolean plain : new boolean[] { true, false })
					for (Storage<ItemVariant> storage : storages) {
						if (needed <= 0)
							break;
						needed -= extractMatching(storage, required::matches, plain, needed, transaction);
					}
				if (needed > 0)
					return required;
			}
			transaction.commit();
			return null;
		}
	}

	private static long extractMatching(Storage<ItemVariant> storage, Predicate<ItemStack> predicate, boolean plain,
		long maxAmount, Transaction transaction) {
		long extracted = 0;
		for (StorageView<ItemVariant> view : storage.nonEmptyViews()) {
			ItemVariant resource = view.getResource();
			if (resource.hasComponents() == plain || hasContents(resource) || !predicate.test(resource.toStack()))
				continue;
			extracted += view.extract(resource, maxAmount - extracted, transaction);
			if (extracted >= maxAmount)
				break;
		}
		return extracted;
	}

	private static boolean hasContents(ItemVariant resource) {
		ItemContainerContents container = resource.getComponents()
			.get(DataComponents.CONTAINER);
		BundleContents bundle = resource.getComponents()
			.get(DataComponents.BUNDLE_CONTENTS);
		Bees bees = resource.getComponents()
			.get(DataComponents.BEES);
		return container != null && container.nonEmptyItems()
			.iterator()
			.hasNext() || bundle != null && !bundle.isEmpty() || bees != null && !bees.bees()
				.isEmpty() || resource.getComponents()
					.has(DataComponents.BLOCK_ENTITY_DATA);
	}

	public void finishedPrinting() {
		if (replaceMode == ConfigureSchematicannonPacket.Option.REPLACE_EMPTY.ordinal())
			printer.sendBlockUpdates(level);
		inventory.setItem(SchematicannonInventory.SCHEMATIC_INPUT, ItemStack.EMPTY);
		outputEmptySchematic();
		state = State.STOPPED;
		autoPaused = false;
		statusMsg = "finished";
		resetPrinter();
		level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1, .7f);
		sendUpdate = true;
	}

	protected void resetPrinter() {
		printer.resetSchematic();
		missingItem = null;
		positionNotLoaded = false;
		sendUpdate = true;
		schematicProgress = 0;
		blocksPlaced = 0;
		blocksToPlace = 0;
	}

	protected boolean shouldPlace(BlockPos pos, BlockState state, BlockEntity be, BlockState toReplace,
		BlockState toReplaceOther, boolean isNormalCube) {
		if (pos.closerThan(getBlockPos(), 2f))
			return false;
		if (!replaceBlockEntities
			&& (toReplace.hasBlockEntity() || (toReplaceOther != null && toReplaceOther.hasBlockEntity())))
			return false;

		if (shouldIgnoreBlockState(state, be))
			return false;

		boolean placingAir = state.isAir();

		if (replaceMode == 3)
			return true;
		if (replaceMode == 2 && !placingAir)
			return true;
		if (replaceMode == 1 && (isNormalCube || (!toReplace.isRedstoneConductor(level, pos)
			&& (toReplaceOther == null || !toReplaceOther.isRedstoneConductor(level, pos)))) && !placingAir)
			return true;
		if (replaceMode == 0 && !toReplace.isRedstoneConductor(level, pos)
			&& (toReplaceOther == null || !toReplaceOther.isRedstoneConductor(level, pos)) && !placingAir)
			return true;

		return false;
	}

	protected boolean shouldIgnoreBlockState(BlockState state, BlockEntity be) {
		// Block doesn't have a mapping (Water, lava, etc)
		if (state.getBlock() == Blocks.STRUCTURE_VOID)
			return true;

		ItemRequirement requirement = ItemRequirement.of(state, be);
		if (requirement.isEmpty())
			return false;
		if (requirement.isInvalid())
			return false;

		// Block doesn't need to be placed twice (Doors, beds, double plants)
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
			&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER)
			return true;
		if (state.hasProperty(BlockStateProperties.BED_PART)
			&& state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD)
			return true;
		return false;
	}

	protected void tickFlyingBlocks() {
		flyingBlocks.removeIf(b -> b.update(level));
	}

	protected void refillFuelIfPossible() {
		if (remainingFuel > getShotsPerGunpowder()) {
			remainingFuel = getShotsPerGunpowder();
			sendUpdate = true;
			return;
		}

		if (remainingFuel > 0)
			return;

		ItemStack gunpowder = inventory.getItem(SchematicannonInventory.GUNPOWDER);
		if (!gunpowder.isEmpty()) {
			gunpowder.shrink(1);
			inventory.setChanged();
		} else {
			boolean externalGunpowderFound = false;
			try (Transaction transaction = Transaction.openOuter()) {
				for (Storage<ItemVariant> storage : getAttachedStorages()) {
					if (storage.extract(ItemVariant.of(Items.GUNPOWDER), 1, transaction) == 1) {
						transaction.commit();
						externalGunpowderFound = true;
						break;
					}
				}
			}
			if (!externalGunpowderFound)
				return;
		}

		remainingFuel += getShotsPerGunpowder();
		if (statusMsg.equals("noGunpowder")) {
			if (autoPaused)
				resume();
			statusMsg = "ready";
		}
		sendUpdate = true;
	}

	protected void tickPaperPrinter() {
		ItemStack blueprint = inventory.getItem(SchematicannonInventory.SCHEMATIC_INPUT);
		ItemStack paper = inventory.getItem(SchematicannonInventory.BOOK_INPUT);
		ItemStack output = inventory.getItem(SchematicannonInventory.BOOK_OUTPUT);
		boolean outputFull = output.getCount() >= output.getMaxStackSize() && !output.isEmpty();

		if (!printer.isLoaded()) {
			if (!blueprint.isEmpty())
				initializePrinter(blueprint);
			return;
		}

		if (paper.isEmpty() || outputFull) {
			if (bookPrintingProgress != 0)
				sendUpdate = true;
			bookPrintingProgress = 0;
			dontUpdateChecklist = false;
			return;
		}

		if (bookPrintingProgress >= 1) {
			bookPrintingProgress = 0;

			if (!dontUpdateChecklist)
				updateChecklist();

			dontUpdateChecklist = true;
			inventory.removeItem(SchematicannonInventory.BOOK_INPUT, 1);
			ItemStack stack = checklist.createWrittenBook();
			stack.setCount(output.getCount() + 1);
			inventory.setItem(SchematicannonInventory.BOOK_OUTPUT, stack);
			sendUpdate = true;
			return;
		}

		bookPrintingProgress += 0.05f;
		sendUpdate = true;
	}

	protected void launchBlock(BlockPos target, ItemStack stack, BlockState state, @Nullable CompoundTag data) {
		if (!state.isAir())
			blocksPlaced++;
		flyingBlocks.add(new LaunchedItem.ForBlockState(this.getBlockPos(), target, stack, state, data));
		playFiringSound();
	}

	protected void launchEntity(BlockPos target, ItemStack stack, Entity entity) {
		blocksPlaced++;
		flyingBlocks.add(new LaunchedItem.ForEntity(this.getBlockPos(), target, stack, entity));
		playFiringSound();
	}

	public void playFiringSound() {
		level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, .1f, 1.1f);
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
		return new SchematicannonMenu(id, inv, this);
	}

	@Override
	public MenuOpenData getScreenOpeningData(ServerPlayer player) {
		return MenuOpenData.of(this);
	}

	@Override
	public Component getDisplayName() {
		return Lang.translate("gui.schematicannon.title");
	}

	public void updateChecklist() {
		checklist.required.clear();
		checklist.blocksNotLoaded = false;

		if (printer.isLoaded() && !printer.isErrored()) {
			blocksToPlace = blocksPlaced;
			blocksToPlace += printer.markAllBlockRequirements(checklist, level, this::shouldPlace);
			printer.markAllEntityRequirements(checklist);
		}

		checklist.gathered.clear();
		for (Storage<ItemVariant> storage : getAttachedStorages())
			for (StorageView<ItemVariant> view : storage.nonEmptyViews())
				if (!hasContents(view.getResource()))
					checklist.collect(view.getResource()
					.toStack((int) Math.min(Integer.MAX_VALUE, view.getAmount())));
		sendUpdate = true;
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter componentInput) {
		SchematicannonOptions options = componentInput.getOrDefault(ModDataComponents.SCHEMATICANNON_OPTIONS,
			SchematicannonOptions.DEFAULT);
		replaceMode = options.replaceMode;
		skipMissing = options.skipMissing;
		replaceBlockEntities = options.replaceBlockEntities;
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		components.set(ModDataComponents.SCHEMATICANNON_OPTIONS,
			new SchematicannonOptions(replaceMode, skipMissing, replaceBlockEntities));
	}

	public enum State {
		STOPPED, PAUSED, RUNNING
	}

	public record SchematicannonOptions(int replaceMode, boolean skipMissing, boolean replaceBlockEntities) {
		public static final SchematicannonOptions DEFAULT = new SchematicannonOptions(2, true, false);

		public static final Codec<SchematicannonOptions> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 3).fieldOf("replace_mode").forGetter(SchematicannonOptions::replaceMode),
			Codec.BOOL.fieldOf("skip_missing").forGetter(SchematicannonOptions::skipMissing),
			Codec.BOOL.fieldOf("replace_block_entities").forGetter(SchematicannonOptions::replaceBlockEntities)
		).apply(i, SchematicannonOptions::new));

		public static final StreamCodec<ByteBuf, SchematicannonOptions> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, SchematicannonOptions::replaceMode,
			ByteBufCodecs.BOOL, SchematicannonOptions::skipMissing,
			ByteBufCodecs.BOOL, SchematicannonOptions::replaceBlockEntities,
			SchematicannonOptions::new
		);
	}
}
