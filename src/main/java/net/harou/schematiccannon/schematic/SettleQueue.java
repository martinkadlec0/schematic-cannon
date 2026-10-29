package net.harou.schematiccannon.schematic;

import java.util.stream.LongStream;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.harou.schematiccannon.util.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Blocks of a frozen print waiting to be settled (see {@link BlockHelper#settleSchematicBlock(Level, BlockPos)}), a
 * limited number per tick so a large print doesn't freeze the server. Positions in unloaded chunks wait until they are
 * loaded again.
 */
public class SettleQueue {

	/** Blocks settled per tick at most */
	public static final int PER_TICK = 1024;
	/** How often positions waiting for their chunk are tried again */
	private static final int RETRY_TICKS = 100;

	private final LongArrayList queue = new LongArrayList();
	/** The next position of the queue; settled ones are dropped all at once when the queue is done */
	private int head;
	private final LongArrayList waiting = new LongArrayList();

	public void addAll(LongStream positions) {
		positions.forEach(queue::add);
	}

	public boolean isEmpty() {
		return head >= queue.size() && waiting.isEmpty();
	}

	/** The positions still to settle, e.g. to save them */
	public LongStream positions() {
		return LongStream.concat(queue.longStream()
			.skip(head), waiting.longStream());
	}

	public void clear() {
		queue.clear();
		head = 0;
		waiting.clear();
	}

	public void tick(Level level) {
		settle(level, PER_TICK);
	}

	/**
	 * Settles up to the given number of blocks.
	 */
	public void settle(Level level, int max) {
		if (head >= queue.size()) {
			queue.clear();
			head = 0;
			if (!waiting.isEmpty() && level.getGameTime() % RETRY_TICKS == 0) {
				queue.addAll(waiting);
				waiting.clear();
			}
		}
		// In long, as max may be Integer.MAX_VALUE to settle everything
		int end = (int) Math.min(queue.size(), (long) head + max);
		for (; head < end; head++) {
			long position = queue.getLong(head);
			BlockPos pos = BlockPos.of(position);
			if (level.isLoaded(pos))
				BlockHelper.settleSchematicBlock(level, pos);
			else
				waiting.add(position);
		}
	}

}
