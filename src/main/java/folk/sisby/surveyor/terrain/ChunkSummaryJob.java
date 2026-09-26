package folk.sisby.surveyor.terrain;

import folk.sisby.surveyor.util.RegistryPalette;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Summarises a {@link ChunkSnapshot} on a worker (sisby-folk/surveyor#148). The worker only fills local palettes and
 * floor heights; {@link #toSummary} maps them into the region's palettes and reads light, on the level's thread.
 */
final class ChunkSummaryJob implements Runnable {
	private static final int QUEUED = 0;
	private static final int RUNNING = 1;
	private static final int DONE = 2;

	final ChunkSnapshot snapshot;
	private final Consumer<ChunkSummaryJob> onDone;
	private final AtomicInteger state = new AtomicInteger(QUEUED);
	private final CompletableFuture<Void> done = new CompletableFuture<>();
	// Written by the thread that computes, read after done completes or after the job is taken from the done queue.
	private ChunkScan.Floors floors;
	private LocalPalette<Biome> biomes;
	private LocalPalette<Block> blocks;
	private @Nullable Throwable error;

	ChunkSummaryJob(ChunkSnapshot snapshot, Consumer<ChunkSummaryJob> onDone) {
		this.snapshot = snapshot;
		this.onDone = onDone;
	}

	void submit() {
		Worker.EXECUTOR.execute(this);
	}

	@Override
	public void run() {
		if (state.compareAndSet(QUEUED, RUNNING)) compute();
	}

	/**
	 * Computes the job here if no worker started it, else waits for the worker.
	 */
	void finish() {
		if (state.compareAndSet(QUEUED, RUNNING)) {
			compute();
		} else {
			done.join();
		}
	}

	private void compute() {
		try {
			LocalPalette<Biome> biomes = new LocalPalette<>();
			LocalPalette<Block> blocks = new LocalPalette<>();
			ChunkSnapshot s = snapshot;
			floors = ChunkScan.scan(s.sections(), s.minSection, s.pos.getMinBlockX(), s.pos.getMinBlockZ(), s.minBuildHeight, s.maxBuildHeight, s.layerHeights, s, biomes::indexOf, blocks::indexOf);
			this.biomes = biomes;
			this.blocks = blocks;
		} catch (Throwable t) {
			error = t;
		} finally {
			state.set(DONE);
			done.complete(null);
			onDone.accept(this);
		}
	}

	@Nullable Throwable error() {
		return error;
	}

	/**
	 * Adds the values to the region's palettes in first-seen order, as the in-place scan does, then builds the summary.
	 */
	ChunkSummary toSummary(RegistryPalette<Biome> biomePalette, RegistryPalette<Block> blockPalette, ChunkScan.BlockLight light) {
		int[] biomeRemap = new int[biomes.values.size()];
		for (int i = 0; i < biomeRemap.length; i++) biomeRemap[i] = biomePalette.findOrAdd(biomes.values.get(i));
		int[] blockRemap = new int[blocks.values.size()];
		for (int i = 0; i < blockRemap.length; i++) blockRemap[i] = blockPalette.findOrAdd(blocks.values.get(i));
		return new ChunkSummary(snapshot.airCount, snapshot.layerHeights, ChunkScan.layers(floors, snapshot.pos.getMinBlockX(), snapshot.pos.getMinBlockZ(), snapshot.layerHeights, light, biomeRemap, blockRemap));
	}

	private static final class LocalPalette<T> {
		private final Reference2IntOpenHashMap<T> indices = new Reference2IntOpenHashMap<>();
		private final ObjectArrayList<T> values = new ObjectArrayList<>();

		private LocalPalette() {
			indices.defaultReturnValue(-1);
		}

		int indexOf(T value) {
			int index = indices.getInt(value);
			if (index == -1) {
				index = values.size();
				values.add(value);
				indices.put(value, index);
			}
			return index;
		}
	}

	private static final class Worker {
		private static final AtomicInteger COUNT = new AtomicInteger();
		private static final int THREADS = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() / 4));
		private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(THREADS, r -> {
			Thread thread = new Thread(r, "Surveyor Chunk Summary " + COUNT.incrementAndGet());
			thread.setDaemon(true);
			thread.setPriority(Thread.NORM_PRIORITY - 1);
			return thread;
		});
	}
}
