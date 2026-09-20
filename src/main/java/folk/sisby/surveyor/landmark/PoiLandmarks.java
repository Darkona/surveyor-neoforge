package folk.sisby.surveyor.landmark;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Block landmarks for points of interest (lodestones by default).
 * <p>
 * Fix: a POI placed during world generation is reported to the main thread while its chunk is still generating.
 * Building the landmark reads the block and its block entity, which made the main thread finish generating that chunk
 * on the spot (stalls, worse with C2ME). Such POIs now wait until their chunk is fully loaded.
 */
public final class PoiLandmarks {
	private static final int PER_TICK = 16;
	private static final BlockPos.MutableBlockPos TICK_POS = new BlockPos.MutableBlockPos(); // main thread only
	private static final Map<ResourceKey<Level>, Pending> PENDING = new ConcurrentHashMap<>();

	private PoiLandmarks() {
	}

	private static final class Pending {
		final Long2ObjectOpenHashMap<LongArrayList> byChunk = new Long2ObjectOpenHashMap<>();
		final LongArrayFIFOQueue ready = new LongArrayFIFOQueue();
	}

	/**
	 * Called on the main thread when a POI is added.
	 */
	public static void onPoiAdded(ServerLevel world, BlockPos pos) {
		if (WorldLandmarks.of(world) == null || tracked(world, pos) == null) return;
		if (world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null) {
			add(world, pos);
		} else if (Surveyor.CONFIG.builtins.poiLandmarksFromWorldgen) {
			Pending pending = PENDING.computeIfAbsent(world.dimension(), k -> new Pending());
			synchronized (pending) {
				long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
				LongArrayList positions = pending.byChunk.get(chunk);
				if (positions == null) pending.byChunk.put(chunk, positions = new LongArrayList(2));
				positions.add(pos.asLong());
			}
		}
	}

	/**
	 * Called when a chunk is fully loaded; may run off the main thread.
	 */
	public static void onChunkLoad(ServerLevel world, ChunkPos chunk) {
		Pending pending = PENDING.get(world.dimension());
		if (pending == null) return;
		synchronized (pending) {
			LongArrayList positions = pending.byChunk.remove(chunk.toLong());
			if (positions != null) for (int i = 0; i < positions.size(); i++) pending.ready.enqueue(positions.getLong(i));
		}
	}

	/**
	 * Called every level tick on the main thread; builds a few ready landmarks.
	 */
	public static void onTick(ServerLevel world) {
		Pending pending = PENDING.get(world.dimension());
		if (pending == null) return;
		for (int i = 0; i < PER_TICK; i++) {
			long packed;
			synchronized (pending) {
				if (pending.ready.isEmpty()) return;
				packed = pending.ready.dequeueLong();
			}
			TICK_POS.set(packed);
			int chunkX = TICK_POS.getX() >> 4, chunkZ = TICK_POS.getZ() >> 4;
			if (world.getChunkSource().getChunkNow(chunkX, chunkZ) == null) { // unloaded again: wait for its next load
				synchronized (pending) {
					long chunk = ChunkPos.asLong(chunkX, chunkZ);
					LongArrayList positions = pending.byChunk.get(chunk);
					if (positions == null) pending.byChunk.put(chunk, positions = new LongArrayList(2));
					positions.add(packed);
				}
				continue;
			}
			add(world, TICK_POS.immutable());
		}
	}

	public static void onLevelUnload(ServerLevel world) {
		PENDING.remove(world.dimension());
	}

	private static ResourceLocation tracked(ServerLevel world, BlockPos pos) {
		Holder<PoiType> poiType = world.getPoiManager().getType(pos).orElse(null);
		if (poiType == null || poiType.unwrapKey().isEmpty()) return null;
		ResourceLocation poi = poiType.unwrapKey().get().location();
		return Surveyor.CONFIG.builtins.poiLandmarks.contains(poi.toString()) ? poi : null;
	}

	private static void add(ServerLevel world, BlockPos pos) {
		WorldLandmarks landmarks = WorldLandmarks.of(world);
		ResourceLocation poi = tracked(world, pos);
		if (landmarks == null || poi == null) return;
		landmarks.put(Landmark.global(
			ResourceLocation.fromNamespaceAndPath(poi.getNamespace(), "poi/%s/%s/%s/%s".formatted(poi.getPath(), pos.getX(), pos.getY(), pos.getZ())),
			builder -> LandmarkComponentTypes.forBlock(builder, world, pos)
		));
	}
}
