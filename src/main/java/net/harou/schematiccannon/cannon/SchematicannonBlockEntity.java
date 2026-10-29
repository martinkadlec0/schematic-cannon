package net.harou.schematiccannon.cannon;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
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
import net.harou.schematiccannon.schematic.EntityPlacement;
import net.harou.schematiccannon.schematic.EntityPlacements;
import net.harou.schematiccannon.schematic.ItemRequirement;
import net.harou.schematiccannon.schematic.OrphanedSettles;
import net.harou.schematiccannon.schematic.SchematicItem;
import net.harou.schematiccannon.schematic.SchematicPrinter;
import net.harou.schematiccannon.schematic.SettleQueue;
import net.harou.schematiccannon.schematic.source.PlacementResult;
import net.harou.schematiccannon.schematic.source.SchematicSource;
import net.harou.schematiccannon.util.BlockHelper;
import net.harou.schematiccannon.util.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
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
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.Bees;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;
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
	// Blocks are placed without updates and settled once the print is complete
	private boolean frozenPrint;
	// Where frozen shots of the print landed, each once in landing order (saved); settled once the print is over, also
	// when a restart doesn't resume it, so nothing stays frozen
	private final LongLinkedOpenHashSet frozenPlaced = new LongLinkedOpenHashSet();
	// The schematic frozenPlaced belongs to (saved), so a resumed print keeps adding to it
	private ItemStack frozenBlueprint = ItemStack.EMPTY;
	// Positions to settle (saved), a limited number each tick
	private final SettleQueue settleQueue = new SettleQueue();
	// A schematic the cannon gave back into its input slot because the output slot was full; it is not loaded again
	// until it changes, e.g. when it is deployed again
	private ItemStack returnedBlueprint = ItemStack.EMPTY;
	// A schematic whose placement its source refused; checked again only once it is taken out (e.g. to move it) or
	// replaced, as the check may be costly
	private ItemStack rejectedBlueprint = ItemStack.EMPTY;
	private int printerCooldown;
	// The current fluid waits for the blocks still in the air, see waitForLandingBlocks
	private boolean waitingForBlocks;
	// The current target waits for gunpowder
	private boolean waitingForFuel;
	// A block was left out (missing item, or skipped where it leaves a hole), so its fluids are skipped too
	private boolean blocksMissing;
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
		// The cannon can't settle once it is gone, so the server takes over
		settleFrozenPrint();
		if (level instanceof ServerLevel serverLevel && !settleQueue.isEmpty()) {
			OrphanedSettles.add(serverLevel, settleQueue.positions());
			settleQueue.clear();
		}
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
			returnedBlueprint = input.read("ReturnedSchematic", ItemStack.OPTIONAL_CODEC)
				.orElse(ItemStack.EMPTY);
			frozenBlueprint = input.read("FrozenSchematic", ItemStack.OPTIONAL_CODEC)
				.orElse(ItemStack.EMPTY);
			frozenPlaced.clear();
			input.read("FrozenPlaced", Codec.LONG_STREAM)
				.ifPresent(positions -> positions.forEach(frozenPlaced::add));
			settleQueue.clear();
			input.read("SettleQueue", Codec.LONG_STREAM)
				.ifPresent(settleQueue::addAll);
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
			output.store("ReturnedSchematic", ItemStack.OPTIONAL_CODEC, returnedBlueprint);
			// Also before the first frozen shot landed, so a resumed print is recognised
			if (!frozenBlueprint.isEmpty())
				output.store("FrozenSchematic", ItemStack.OPTIONAL_CODEC, frozenBlueprint);
			if (!frozenPlaced.isEmpty())
				output.store("FrozenPlaced", Codec.LONG_STREAM, frozenPlaced.longStream());
			if (!settleQueue.isEmpty())
				output.store("SettleQueue", Codec.LONG_STREAM, settleQueue.positions());
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
		// After the printer was set up, which may resume a frozen print
		tickSettling();
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
			// Putting it back in loads it again, e.g. to print a returned schematic once more or check a placement again
			rejectedBlueprint = ItemStack.EMPTY;
			returnedBlueprint = ItemStack.EMPTY;
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
		if (printer.isLoaded() && !SchematicItem.isSame(blueprint, loadedBlueprint)) {
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

		// Update Target
		if (missingItem == null && !positionNotLoaded && !waitingForBlocks && !waitingForFuel) {
			if (!printer.advanceCurrentPos()) {
				// Settling a frozen print needs all blocks to have landed
				if (flyingBlocks.isEmpty())
					finishedPrinting();
				return;
			}
			sendUpdate = true;
		}
		if (waitForLandingBlocks())
			return;

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
		// Fluids skip the block rules (they only fill air or waterlog a block already there), but not the distance
		if (requirement.isInvalid() || !printer.shouldPlaceCurrent(level, this::shouldPlace)
			|| printer.isPrintingFluids() && isNextToCannon(printer.getCurrentTarget())) {
			if (!statusMsg.equals("searching")) {
				statusMsg = "searching";
				sendUpdate = true;
			}
			blockSkipped = true;
			if (!printer.isPrintingFluids() && !requirement.isInvalid() && leavesHole(printer.getCurrentTarget(),
				printer.getCurrentBlockState()))
				blocksMissing = true;
			// The block we waited for is not needed anymore, e.g. it was placed by hand
			waitingForFuel = false;
			if (missingItem != null) {
				missingItem = null;
				resume();
			}
			return;
		}

		// Not into a build with holes left by missing blocks, the fluid would flow out
		if (blocksMissing && printer.isPrintingFluids()) {
			statusMsg = "skipping";
			blockSkipped = true;
			waitingForFuel = false;
			return;
		}

		// Check fuel, only for a shot that is fired, so a print can finish on its last gunpowder. The target is kept.
		if (remainingFuel <= 0) {
			refillFuelIfPossible();
			if (remainingFuel <= 0) {
				waitingForFuel = true;
				autoPause("noGunpowder");
				return;
			}
		}
		waitingForFuel = false;

		// Find item
		List<ItemRequirement.StackRequirement> requiredItems = requirement.getRequiredItems();
		List<ItemStack> taken = new ArrayList<>();
		if (!requirement.isEmpty()) {
			ItemRequirement.StackRequirement missing = grabItemsFromAttachedInventories(requiredItems, taken);
			if (missing != null) {
				if (skipMissing) {
					statusMsg = "skipping";
					blockSkipped = true;
					if (!printer.isPrintingFluids())
						blocksMissing = true;
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
		boolean[] notPlaced = new boolean[1];
		printer.handleCurrentTarget((target, blockState, blockEntity) -> {
			// Launch block
			statusMsg = blockState.getBlock() != Blocks.AIR ? "placing" : "clearing";
			launchBlock(target, icon, blockState, BlockHelper.prepareBlockEntityData(level, blockState, blockEntity));
		}, (target, blockState, blockEntity) -> {
			// Launch fluid
			statusMsg = "placing";
			LaunchedItem.ForBlockState fluid = new LaunchedItem.ForBlockState(getBlockPos(), target, icon, blockState,
				null, frozenPrint);
			fluid.fluid = true;
			flyingBlocks.add(fluid);
			blocksPlaced++;
			playFiringSound();
			// What the used items leave behind: the empty bucket, or a modded fluid's own container
			for (ItemStack used : taken) {
				ItemStackTemplate remainder = used.getItem()
					.getCraftingRemainder();
				if (remainder != null)
					giveBack(remainder.create()
						.copyWithCount(used.getCount()));
			}
		}, (target, entity) -> {
			// Launch entity
			statusMsg = "placing";
			EntityPlacement placement = EntityPlacements.find(entity);
			if (placement == null) {
				launchEntity(target, icon, entity);
				return;
			}
			// Everything taken for the requirement, maybe from several stacks, as one stack
			ItemStack consumed = taken.isEmpty() ? ItemStack.EMPTY : taken.get(0)
				.copyWithCount(taken.stream()
					.mapToInt(ItemStack::getCount)
					.sum());
			Entity created = placement.create((ServerLevel) level, consumed, entity);
			if (created == null) {
				taken.forEach(this::giveBack);
				notPlaced[0] = true;
				return;
			}
			launchEntity(target, consumed.copyWithCount(1), created);
			giveBack(placement.remainder(consumed));
		});
		// The item couldn't be used, e.g. its stored mob can't be recreated: skipped like a block that can't be placed,
		// without a shot
		if (notPlaced[0]) {
			statusMsg = "skipping";
			blockSkipped = true;
			return;
		}

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

		// Without a size it was never set up, e.g. put into the cannon by a command
		Vec3i storedSize = blueprint.get(ModDataComponents.SCHEMATIC_BOUNDS);
		if (storedSize == null) {
			stop("schematicInvalid");
			return;
		}

		SchematicSource source = SchematicItem.getSource(blueprint);
		BoundingBox box = SchematicItem.getWorldBounds(blueprint, storedSize);
		// A check that looks at the world could load or generate chunks, so it waits until the whole area is loaded
		boolean checkReadsWorld = source.placementCheckReadsWorld(blueprint);
		if (checkReadsWorld && !BlockHelper.isLoaded(level, box)) {
			stop("targetNotLoaded");
			return;
		}

		// Load blocks into reader
		printer.loadSchematic(blueprint, level, true);

		if (printer.isErrored() || printer.isWorldEmpty()) {
			state = State.STOPPED;
			statusMsg = printer.isErrored() ? "schematicErrored" : "schematicExpired";
			returnSchematic(blueprint);
			printer.resetSchematic();
			sendUpdate = true;
			return;
		}

		// The stored size may be outdated, e.g. after the file was uploaded again under the same name: the loaded
		// schematic is what gets printed, so its real size is checked
		Vec3i size = printer.getSchematicSize();
		if (!size.equals(storedSize)) {
			blueprint.set(ModDataComponents.SCHEMATIC_BOUNDS, size);
			setChanged();
			box = SchematicItem.getWorldBounds(blueprint, size);
			if (checkReadsWorld && !BlockHelper.isLoaded(level, box)) {
				printer.resetSchematic();
				stop("targetNotLoaded");
				return;
			}
		}
		PlacementResult placement = source.validatePlacement((ServerLevel) level, blueprint, box);
		// The reason is shown while placing the schematic, see SchematicSyncPacket
		if (!placement.isValid()) {
			printer.resetSchematic();
			rejectedBlueprint = blueprint.copy();
			stop("invalidPlacement");
			return;
		}

		// Frozen blocks of another schematic, e.g. of a print a restart didn't resume, are settled now; those of this
		// one only when the print is over
		if (!SchematicItem.isSame(blueprint, frozenBlueprint))
			settleFrozenPrint();
		loadedBlueprint = blueprint.copy();
		frozenPrint = SchematicItem.isFrozenPrint(blueprint);
		frozenBlueprint = frozenPrint ? blueprint.copy() : ItemStack.EMPTY;
		missingItem = null;
		waitingForBlocks = false;
		waitingForFuel = false;
		blocksMissing = false;
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
	 * Replaces the schematic in the input slot with what its source gives back once it is done (by default an empty
	 * schematic). It goes to the output slot, or stays in the input slot when the output slot holds something else, so
	 * it is never dropped.
	 */
	private void returnSchematic(ItemStack blueprint) {
		ItemStack printed = SchematicItem.getSource(blueprint)
			.afterPrinting(blueprint);
		if (tryOutput(printed)) {
			inventory.setItem(SchematicannonInventory.SCHEMATIC_INPUT, ItemStack.EMPTY);
			returnedBlueprint = ItemStack.EMPTY;
		} else {
			inventory.setItem(SchematicannonInventory.SCHEMATIC_INPUT, printed);
			returnedBlueprint = printed.copy();
		}
	}

	/**
	 * Adds the stack to the output slot if it is empty or holds the same item with room left.
	 */
	private boolean tryOutput(ItemStack stack) {
		if (stack.isEmpty())
			return true;
		ItemStack output = inventory.getItem(SchematicannonInventory.SCHEMATIC_OUTPUT);
		if (output.isEmpty()) {
			inventory.setItem(SchematicannonInventory.SCHEMATIC_OUTPUT, stack);
			return true;
		}
		if (ItemStack.isSameItemSameComponents(output, stack)
			&& output.getCount() + stack.getCount() <= output.getMaxStackSize()) {
			output.grow(stack.getCount());
			inventory.setChanged();
			return true;
		}
		return false;
	}

	/**
	 * Puts items back into the attached inventories, e.g. what is left of an item used for an entity. Drops what does
	 * not fit.
	 */
	private void giveBack(ItemStack stack) {
		if (stack.isEmpty())
			return;
		long remaining = stack.getCount();
		try (Transaction transaction = Transaction.openOuter()) {
			for (Storage<ItemVariant> storage : getAttachedStorages()) {
				remaining -= storage.insert(ItemVariant.of(stack), remaining, transaction);
				if (remaining <= 0)
					break;
			}
			transaction.commit();
		}
		if (remaining > 0)
			Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + 1, worldPosition.getZ() + .5,
				stack.copyWithCount((int) remaining));
	}

	/**
	 * Takes all required items from the attached inventories, or nothing if one of them is missing.
	 *
	 * @param taken receives the stacks that were taken
	 * @return the first requirement that could not be satisfied, or null if the items were taken
	 */
	@Nullable
	protected ItemRequirement.StackRequirement grabItemsFromAttachedInventories(
		List<ItemRequirement.StackRequirement> requiredItems, List<ItemStack> taken) {
		List<Storage<ItemVariant>> storages = getAttachedStorages();
		try (Transaction transaction = Transaction.openOuter()) {
			for (ItemRequirement.StackRequirement required : requiredItems) {
				long needed = required.stack.getCount();
				// Plain items first, so e.g. a filled shulker box or a named item is only used when nothing else is there
				for (boolean plain : new boolean[] { true, false })
					for (Storage<ItemVariant> storage : storages) {
						if (needed <= 0)
							break;
						needed -= extractMatching(storage, required::matches, plain, needed, transaction, taken);
					}
				if (needed > 0) {
					taken.clear();
					return required;
				}
			}
			transaction.commit();
			return null;
		}
	}

	private static long extractMatching(Storage<ItemVariant> storage, Predicate<ItemStack> predicate, boolean plain,
		long maxAmount, Transaction transaction, List<ItemStack> taken) {
		long extracted = 0;
		for (StorageView<ItemVariant> view : storage.nonEmptyViews()) {
			ItemVariant resource = view.getResource();
			if (resource.hasComponents() == plain || hasContents(resource) || !predicate.test(resource.toStack()))
				continue;
			long amount = view.extract(resource, maxAmount - extracted, transaction);
			if (amount > 0)
				taken.add(resource.toStack((int) amount));
			extracted += amount;
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
		settleFrozenPrint();
		if (replaceMode == ConfigureSchematicannonPacket.Option.REPLACE_EMPTY.ordinal())
			printer.sendBlockUpdates(level);
		returnSchematic(inventory.getItem(SchematicannonInventory.SCHEMATIC_INPUT));
		state = State.STOPPED;
		autoPaused = false;
		statusMsg = "finished";
		resetPrinter();
		level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1, .7f);
		sendUpdate = true;
	}

	/**
	 * Ends a frozen print, also when it stops halfway: what it placed is settled over the next ticks, see
	 * {@link #tickSettling}.
	 */
	private void settleFrozenPrint() {
		frozenPrint = false;
		frozenBlueprint = ItemStack.EMPTY;
		settleQueue.addAll(frozenPlaced.longStream());
		frozenPlaced.clear();
		// Shots still in the air (the print was stopped) land with their updates, nothing settles them later
		for (LaunchedItem launched : flyingBlocks)
			if (launched instanceof LaunchedItem.ForBlockState block)
				block.frozen = false;
	}

	/**
	 * Runs the updates frozen prints skipped, a limited number of blocks per tick, see {@link SettleQueue}.
	 */
	private void tickSettling() {
		// A frozen print that can't be resumed, e.g. after a restart its schematic was taken out or its placement
		// rejected, is over too. While its schematic is still in the cannon (e.g. waiting for its area to load) it may.
		ItemStack inserted = inventory.getItem(SchematicannonInventory.SCHEMATIC_INPUT);
		boolean mayResume = SchematicItem.isSame(inserted, frozenBlueprint) && !SchematicItem.isSame(inserted, rejectedBlueprint)
			&& !SchematicItem.isSame(inserted, returnedBlueprint);
		if (!frozenPrint && !frozenPlaced.isEmpty() && !mayResume)
			settleFrozenPrint();
		settleQueue.tick(level);
	}

	protected void resetPrinter() {
		settleFrozenPrint();
		printer.resetSchematic();
		missingItem = null;
		positionNotLoaded = false;
		waitingForBlocks = false;
		waitingForFuel = false;
		blocksMissing = false;
		sendUpdate = true;
		schematicProgress = 0;
		blocksPlaced = 0;
		blocksToPlace = 0;
	}

	/**
	 * Whether leaving out the schematic's block (e.g. next to the cannon) leaves a hole fluids could flow out of: the
	 * block is wanted, but there is only air or something replaceable like grass. Not for blocks that don't hold water
	 * back anyway (flowers, torches, signs), nor what the cannon never places itself: the upper half of a door or plant
	 * (its lower half adds it, in a frozen print only when settling), fluids and structure voids (which keep what is
	 * there).
	 */
	private boolean leavesHole(BlockPos pos, BlockState wanted) {
		if (wanted.isAir() || wanted.getBlock() instanceof LiquidBlock || wanted.is(Blocks.STRUCTURE_VOID)
			|| wanted.canBeReplaced(Fluids.WATER) || BlockHelper.isPlacedByOtherHalf(wanted))
			return false;
		BlockState current = level.getBlockState(pos);
		return (current.isAir() || current.canBeReplaced())
			&& BlockHelper.getPlacedState(current) != BlockHelper.getPlacedState(wanted);
	}

	/** The cannon never builds this close to itself */
	private boolean isNextToCannon(BlockPos pos) {
		return pos.closerThan(getBlockPos(), 2f);
	}

	protected boolean shouldPlace(BlockPos pos, BlockState state, BlockEntity be, BlockState toReplace,
		BlockState toReplaceOther, boolean isNormalCube) {
		if (isNextToCannon(pos))
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

	/**
	 * Fluids wait until every block fired before them has landed. Shots fly longer the farther they go, so a fluid
	 * fired after the last wall block could otherwise land first and flow out through the gap still in the air.
	 */
	private boolean waitForLandingBlocks() {
		waitingForBlocks = printer.isPrintingFluids() && flyingBlocks.stream()
			.anyMatch(launched -> launched instanceof LaunchedItem.ForBlockState block && !block.fluid);
		return waitingForBlocks;
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
		return BlockHelper.isPlacedByOtherHalf(state);
	}

	protected void tickFlyingBlocks() {
		flyingBlocks.removeIf(launched -> {
			if (!launched.update(level))
				return false;
			// Landed; frozen blocks are remembered for settling, if they were placed (a fluid may not fit any more)
			if (launched instanceof LaunchedItem.ForBlockState block && block.frozen && block.placed)
				frozenPlaced.add(block.target.asLong());
			return true;
		});
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
			if (!blueprint.isEmpty() && !SchematicItem.isSame(blueprint, returnedBlueprint)
				&& !SchematicItem.isSame(blueprint, rejectedBlueprint))
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
		flyingBlocks.add(new LaunchedItem.ForBlockState(this.getBlockPos(), target, stack, state, data, frozenPrint));
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
		checklist.clear();

		if (printer.isLoaded() && !printer.isErrored()) {
			blocksToPlace = blocksPlaced;
			blocksToPlace += printer.markAllBlockRequirements(checklist, level, this::shouldPlace);
			printer.markAllEntityRequirements(checklist);
		}

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
		/** Missing blocks pause the print instead of being left out, so a build with gaps never counts as finished */
		public static final SchematicannonOptions DEFAULT = new SchematicannonOptions(2, false, false);

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
