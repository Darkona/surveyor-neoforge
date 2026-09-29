package folk.sisby.surveyor.terrain;

import folk.sisby.surveyor.SurveyorDebug;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.packet.S2CUpdateRegionPacket;
import folk.sisby.surveyor.util.ChunkUtil;
import folk.sisby.surveyor.util.RegionPos;
import folk.sisby.surveyor.util.RegistryPalette;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;

public class WorldTerrain {
	protected final WorldSummary summary;
	protected final Map<RegionPos, RegionSummary> regions = new ConcurrentHashMap<>();
	protected final File folder;
	protected final Map<RegionPos, Map<UUID, BitSet>> queuedUpdates = new LinkedHashMap<>();
	// Chunks being summarised on a worker (sisby-folk/surveyor#148); the map is only touched on the server thread.
	public static final int MAX_PENDING = 256;
	private final Long2ObjectOpenHashMap<ChunkSummaryJob> pending = new Long2ObjectOpenHashMap<>();
	private final ConcurrentLinkedQueue<ChunkSummaryJob> finished = new ConcurrentLinkedQueue<>();

	public static WorldTerrain of(Level world) {
		if (world == null) return null;
		WorldSummary summary = WorldSummary.of(world);
		return summary == null ? null : summary.terrain();
	}

	public WorldTerrain(WorldSummary summary, Map<RegionPos, RegionSummary> regions, File folder) {
		this.summary = summary;
		this.regions.putAll(regions);
		this.folder = folder;
	}

	public static Set<ChunkPos> toKeys(Map<RegionPos, BitSet> bitSets) {
		return toKeys(bitSets, Comparator.comparingInt(pos -> pos.x() + pos.z()));
	}

	public static Set<ChunkPos> toKeys(Map<RegionPos, BitSet> bitSets, ChunkPos originChunk) {
		ChunkPos oPos = new ChunkPos(RegionPos.chunkToRegion(originChunk.x), RegionPos.chunkToRegion(originChunk.z));
		return toKeys(bitSets, Comparator.comparingDouble(pos -> (oPos.x - pos.x()) * (oPos.x - pos.x()) + (oPos.z - pos.z()) * (oPos.z - pos.z())));
	}

	public static Set<ChunkPos> toKeys(Map<RegionPos, BitSet> bitSets, Comparator<RegionPos> regionComparator) {
		Set<ChunkPos> set = new LinkedHashSet<>();
		bitSets.entrySet().stream().sorted(Map.Entry.comparingByKey(regionComparator)).forEach(e -> e.getValue().stream().forEach(i -> set.add(e.getKey().toChunk(i))));
		return set;
	}

	public static WorldTerrain load(WorldSummary summary, File folder) {
		Map<RegionPos, RegionSummary> regions = new HashMap<>();
		ChunkUtil.getRegionFiles(folder, "c").forEach((pos, file) -> regions.put(pos, RegionSummary.fromFile(file, summary, pos)));
		if (SurveyorDebug.on) SurveyorDebug.log("{}: found {} terrain regions in {}", summary.dimension().location(), regions.size(), folder);
		return new WorldTerrain(summary, regions, folder);
	}

	public static void onChunkLoad(Level world, LevelChunk chunk) {
		WorldTerrain terrain = WorldTerrain.of(world);
		if (terrain == null) return;
		ChunkSummary chunkSummary = terrain.get(chunk.getPos());
		if (chunkSummary == null || !Surveyor.CONFIG.lazyClientUpdating || !ChunkUtil.airCount(chunk).equals(chunkSummary.getAirCount())) {
			terrain.put(world, chunk);
		}
	}

	public static void onChunkUnload(Level world, LevelChunk chunk) {
		WorldTerrain terrain = WorldTerrain.of(world);
		if (terrain == null) return;
		if (chunk.isUnsaved()) {
			terrain.put(world, chunk);
		}
	}

	public boolean contains(ChunkPos pos) {
		RegionPos regionPos = RegionPos.of(pos);
		return regions.containsKey(regionPos) && regions.get(regionPos).contains(pos);
	}

	public ChunkSummary get(ChunkPos pos) {
		RegionPos regionPos = RegionPos.of(pos);
		return regions.containsKey(regionPos) ? regions.get(regionPos).get(pos) : null;
	}

	public RegionSummary getRegion(RegionPos regionPos) {
		return regions.computeIfAbsent(regionPos, k -> RegionSummary.fromEmpty(folder, summary, regionPos));
	}

	public RegistryPalette<Biome>.ValueView getBiomePalette(ChunkPos pos) {
		RegionPos regionPos = RegionPos.of(pos);
		return regions.get(regionPos).getBiomePalette();
	}

	public RegistryPalette<Block>.ValueView getBlockPalette(ChunkPos pos) {
		RegionPos regionPos = RegionPos.of(pos);
		return regions.get(regionPos).getBlockPalette();
	}

	public Map<RegionPos, BitSet> bitSet(SurveyorExploration exploration) {
		Map<RegionPos, BitSet> map = new HashMap<>();
		regions.forEach((p, r) -> map.put(p, r.bitSet()));
		return exploration == null ? map : exploration.limit(summary.dimension(), map);
	}

	public void put(Level world, LevelChunk chunk) {
		if (Surveyor.CONFIG.terrain == SystemMode.FROZEN) return;
		if (world instanceof ServerLevel && Surveyor.CONFIG.asyncChunkSummaries && world.getHeight() != 0 && pending.size() < MAX_PENDING) {
			ChunkSnapshot snapshot = ChunkSnapshot.of(chunk.getSections(), chunk.getPos(), chunk.getMinSection(), world.getMinBuildHeight(), world.getMaxBuildHeight(), DimensionSupport.getSummaryLayers(world), null);
			if (snapshot != null) {
				ChunkSummaryJob job = new ChunkSummaryJob(snapshot, finished::add);
				pending.put(chunk.getPos().toLong(), job); // a job still pending for this chunk is now stale
				job.submit();
				SurveyorDebug.count(SurveyorDebug.Count.CHUNKS_OFF_THREAD);
				SurveyorDebug.max(SurveyorDebug.Count.CHUNKS_PENDING_MAX, pending.size());
				return;
			}
			SurveyorDebug.count(SurveyorDebug.Count.CHUNKS_IN_PLACE_READS_LEVEL);
		} else if (SurveyorDebug.on && world instanceof ServerLevel) {
			SurveyorDebug.count(pending.size() >= MAX_PENDING && Surveyor.CONFIG.asyncChunkSummaries ? SurveyorDebug.Count.CHUNKS_IN_PLACE_QUEUE_FULL : SurveyorDebug.Count.CHUNKS_IN_PLACE_ASYNC_OFF);
		}
		putInPlace(world, chunk);
	}

	private void putInPlace(Level world, LevelChunk chunk) {
		SurveyorDebug.count(SurveyorDebug.Count.CHUNKS_IN_PLACE);
		if (!pending.isEmpty()) pending.remove(chunk.getPos().toLong());
		regions.computeIfAbsent(RegionPos.of(chunk.getPos()), k -> RegionSummary.fromEmpty(folder, summary, RegionPos.of(chunk.getPos()))).putChunk(world, chunk);
		SurveyorEvents.Invoke.terrainUpdated(WorldSummary.of(world), chunk.getPos());
	}

	private void publish(ServerLevel world, ChunkSummaryJob job) {
		ChunkPos pos = job.snapshot.pos;
		if (job.error() != null) {
			SurveyorDebug.count(SurveyorDebug.Count.CHUNKS_FAILED);
			Surveyor.LOGGER.error("[Surveyor] Error summarising chunk {} off-thread; summarising it in place.", pos, job.error());
			LevelChunk chunk = world.getChunkSource().getChunkNow(pos.x, pos.z);
			if (chunk != null) putInPlace(world, chunk);
			return;
		}
		regions.computeIfAbsent(RegionPos.of(pos), k -> RegionSummary.fromEmpty(folder, summary, RegionPos.of(pos))).putScanned(world, job);
		SurveyorDebug.count(SurveyorDebug.Count.CHUNKS_PUBLISHED);
		SurveyorEvents.Invoke.terrainUpdated(summary, pos);
	}

	/**
	 * Publishes the chunks the workers finished. Server thread only.
	 */
	public void publishFinished(ServerLevel world) {
		for (ChunkSummaryJob job; (job = finished.poll()) != null; ) {
			long key = job.snapshot.pos.toLong();
			if (pending.get(key) != job) { // replaced by a newer summary
				SurveyorDebug.count(SurveyorDebug.Count.CHUNKS_STALE);
				continue;
			}
			pending.remove(key);
			publish(world, job);
		}
	}

	/**
	 * Publishes this chunk's summary now if it's still being made, so it can be read or sent. Server thread only.
	 */
	public void finishPending(ServerLevel world, ChunkPos pos) {
		if (pending.isEmpty()) return;
		ChunkSummaryJob job = pending.remove(pos.toLong());
		if (job == null) return;
		job.finish();
		publish(world, job);
	}

	/**
	 * Publishes every summary still being made, e.g. before saving. Server thread only.
	 */
	public void finishPending(ServerLevel world) {
		while (!pending.isEmpty()) {
			ChunkSummaryJob[] jobs = pending.values().toArray(new ChunkSummaryJob[0]);
			pending.clear();
			for (ChunkSummaryJob job : jobs) {
				job.finish();
				publish(world, job);
			}
		}
		finished.clear();
	}

	public static void onTick(ServerLevel world) {
		WorldTerrain terrain = WorldTerrain.of(world);
		if (terrain != null) terrain.serverTick(world);
	}

	public void sendUpdateForRegion(RegionPos regionPos, ServerPlayer player, BitSet set) {
		RegionSummary region = getRegion(regionPos);
		SurveyorExploration personalExploration = SurveyorExploration.of(player);
		BitSet personalSet = personalExploration.limit(summary.dimension(), regionPos, (BitSet) set.clone());
		if (!personalSet.isEmpty()) S2CUpdateRegionPacket.of(summary.dimension(), false, regionPos, region, personalSet).send(player);
		set.andNot(personalSet);
		if (!set.isEmpty()) S2CUpdateRegionPacket.of(summary.dimension(), true, regionPos, region, set).send(player);
	}

	public void serverTick(ServerLevel world) {
		if (!finished.isEmpty()) publishFinished(world);
		if (queuedUpdates.isEmpty() || (world.getServer().getTickCount() % Surveyor.CONFIG.networking.terrainTicks) != 0) return;
		RegionPos regionPos = queuedUpdates.keySet().iterator().next();
		RegionSummary region = getRegion(regionPos);
		queuedUpdates.get(regionPos).forEach((uuid, set) -> {
			ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
			if (player != null) sendUpdateForRegion(regionPos, player, set);
		});
		queuedUpdates.remove(regionPos);
		if (region.isLoaded() && region.isUnloaded(world)) region.save(true);
	}

	public void queueUpdate(RegionPos regionPos, BitSet set, ServerPlayer player) {
		if (getRegion(regionPos).isLoaded()) {
			sendUpdateForRegion(regionPos, player, set);
		} else {
			queuedUpdates.computeIfAbsent(regionPos, k -> new LinkedHashMap<>()).put(player.getUUID(), set);
		}
	}

	public int save(@Nullable Level world) {
		if (world instanceof ServerLevel serverWorld) finishPending(serverWorld);
		List<RegionPos> savedRegions = new ArrayList<>();
		regions.forEach((pos, summary) -> {
			if (summary.isLoaded()) {
				if (summary.isDirty()) savedRegions.add(pos);
				summary.save(world == null || summary.isUnloaded(world));
			}
		});
		return savedRegions.size();
	}

	public boolean isDirty() {
		return !pending.isEmpty() || regions.values().stream().anyMatch(RegionSummary::isDirty);
	}
}
