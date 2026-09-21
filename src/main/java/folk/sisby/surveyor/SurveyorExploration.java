package folk.sisby.surveyor;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import folk.sisby.surveyor.client.SurveyorClientEvents;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.util.RegionPos;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import java.util.BitSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public interface SurveyorExploration {
	String KEY_EXPLORED_TERRAIN = "exploredTerrain";
	String KEY_EXPLORED_STRUCTURES = "exploredStructures";

	static SurveyorExploration of(ServerPlayer player) {
		return PlayerSummary.of(player).exploration();
	}

	static SurveyorExploration of(UUID player, MinecraftServer server) {
		return ServerSummary.of(server).getExploration(player);
	}

	static SurveyorExploration ofShared(ServerPlayer player) {
		return ofShared(Surveyor.getUuid(player), player.getServer());
	}

	static SurveyorExploration ofShared(UUID player, MinecraftServer server) {
		return ServerSummary.of(server).getSharingExploration(player, NetworkMode.GROUP, true);
	}

	Table<ResourceKey<Level>, RegionPos, BitSet> chunks();

	Table<ResourceKey<Level>, ResourceKey<Structure>, LongSet> starts();

	Set<UUID> sharedPlayers();

	default void copyFrom(SurveyorExploration oldExploration) {
		chunks().putAll(oldExploration.chunks());
		starts().putAll(oldExploration.starts());
	}

	boolean personal();

	default boolean exploredChunk(ResourceKey<Level> dimension, ChunkPos pos) {
		RegionPos regionPos = RegionPos.of(pos);
		return !personal() && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SERVER) || chunks().contains(dimension, regionPos) && chunks().get(dimension, regionPos).get(RegionPos.chunkToBit(pos));
	}

	default boolean exploredStructure(ResourceKey<Level> dimension, ResourceKey<Structure> structure, ChunkPos pos) {
		return !personal() && Surveyor.CONFIG.networking.structures.atLeast(NetworkMode.SERVER) || starts().contains(dimension, structure) && starts().get(dimension, structure).contains(pos.toLong());
	}

	default boolean exploredLandmark(ResourceKey<Level> dimension, Landmark landmark) {
		return landmark.owner().equals(WorldLandmarks.GLOBAL) ? !landmark.components().contains(LandmarkComponentTypes.POS) || exploredChunk(dimension, new ChunkPos(landmark.components().get(LandmarkComponentTypes.POS))) : sharedPlayers().contains(landmark.owner());
	}

	default int chunkCount() {
		return chunks().values().stream().mapToInt(BitSet::cardinality).sum();
	}

	default int structureCount() {
		return starts().values().stream().mapToInt(LongSet::size).sum();
	}

	default BitSet limit(ResourceKey<Level> dimension, RegionPos regionPos, BitSet chunks) {
		if (!personal() && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SERVER)) return chunks;
		if (chunks().contains(dimension, regionPos)) {
			chunks.and(chunks().get(dimension, regionPos));
		} else {
			chunks.clear();
		}
		return chunks;
	}

	default Map<RegionPos, BitSet> limit(ResourceKey<Level> dimension, Map<RegionPos, BitSet> chunks) {
		if (!personal() && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SERVER)) return chunks;
		if (chunks().row(dimension).isEmpty()) {
			chunks.clear();
		} else {
			chunks.forEach((regionPos, set) -> limit(dimension, regionPos, set));
		}
		return chunks;
	}

	default Multimap<ResourceKey<Structure>, ChunkPos> limit(ResourceKey<Level> dimension, Multimap<ResourceKey<Structure>, ChunkPos> starts) {
		if (!personal() && Surveyor.CONFIG.networking.structures.atLeast(NetworkMode.SERVER)) return starts;
		if (starts().row(dimension).isEmpty()) {
			starts.clear();
		} else {
			starts.keySet().removeIf(key -> !starts().contains(dimension, key));
			starts.entries().removeIf(e -> !starts().get(dimension, e.getKey()).contains(e.getValue().toLong()));
		}
		return starts;
	}

	default Table<UUID, ResourceLocation, Landmark> limit(ResourceKey<Level> dimension, Table<UUID, ResourceLocation, Landmark> landmarks) {
		Table<UUID, ResourceLocation, Landmark> outTable = HashBasedTable.create(landmarks);
		outTable.values().removeIf(l -> !exploredLandmark(dimension, l));
		return outTable;
	}

	default Multimap<UUID, ResourceLocation> limit(ResourceKey<Level> dimension, WorldLandmarks landmarks, Multimap<UUID, ResourceLocation> keys) {
		Multimap<UUID, ResourceLocation> toRemove = HashMultimap.create();
		keys.forEach((uuid, id) -> {
			if (!landmarks.contains(uuid, id) || !exploredLandmark(dimension, landmarks.get(uuid, id))) toRemove.put(uuid, id);
		});
		toRemove.forEach(keys::remove);
		return keys;
	}

	default void updateClientForMergeRegion(WorldSummary summary, RegionPos regionPos, BitSet chunks) {
		SurveyorClientEvents.Invoke.terrainUpdated(summary, Map.of(regionPos, chunks));
		Multimap<UUID, ResourceLocation> landmarkKeys = HashMultimap.create();
		WorldLandmarks landmarks = summary == null ? null : summary.landmarks();
		if (landmarks == null) return;
		Set<ChunkPos> terrainKeys = chunks.stream().mapToObj(regionPos::toChunk).collect(Collectors.toSet());
		landmarks.asMap(this).values().forEach(landmark -> {
			if (landmark.components().contains(LandmarkComponentTypes.POS) && terrainKeys.contains(new ChunkPos(landmark.components().get(LandmarkComponentTypes.POS))) && landmark.owner().equals(WorldLandmarks.GLOBAL)) landmarkKeys.put(landmark.owner(), landmark.id());
		});
		SurveyorClientEvents.Invoke.landmarksAdded(summary, landmarkKeys);
	}

	default void updateClientForLandmarks(WorldSummary summary) {
		WorldLandmarks landmarks = summary.landmarks();
		if (landmarks == null) return;
		Multimap<UUID, ResourceLocation> unexploredLandmarks = landmarks.keySet(null);
		Multimap<UUID, ResourceLocation> exploredLandmarks = landmarks.keySet(this);
		exploredLandmarks.forEach(unexploredLandmarks::remove);
		SurveyorClientEvents.Invoke.landmarksAdded(summary, exploredLandmarks);
		SurveyorClientEvents.Invoke.landmarksRemoved(summary, unexploredLandmarks);
	}

	default void mergeRegion(ResourceKey<Level> dimension, RegionPos regionPos, BitSet chunks, boolean updateClient) {
		if (!chunks().contains(dimension, regionPos)) chunks().put(dimension, regionPos, new BitSet(RegionPos.CHUNK_AREA));
		chunks().get(dimension, regionPos).or(chunks);
	}

	default void replaceTerrain(Table<ResourceKey<Level>, RegionPos, BitSet> chunks, boolean updateClient) {
		for (ResourceKey<Level> dimension : chunks.rowKeySet()) { // merge first for client updates
			chunks.row(dimension).forEach((regionPos, bitSet) -> mergeRegion(dimension, regionPos, bitSet, updateClient));
		}
		// Fix: putAll alone kept every region the new data doesn't list, so the old share group's chunks stayed explored.
		chunks().clear();
		chunks().putAll(chunks); // then replace to ditch anything else
	}

	default void updateClientForAddChunk(WorldSummary summary, ChunkPos chunkPos) {
		SurveyorClientEvents.Invoke.terrainUpdated(summary, chunkPos);
		Multimap<UUID, ResourceLocation> landmarkKeys = HashMultimap.create();
		WorldLandmarks landmarks = summary == null ? null : summary.landmarks();
		if (landmarks == null) return;
		landmarks.asMap(this).values().forEach(landmark -> {
			if (landmark.components().contains(LandmarkComponentTypes.POS) && chunkPos.equals(new ChunkPos(landmark.components().get(LandmarkComponentTypes.POS))) && landmark.owner().equals(WorldLandmarks.GLOBAL)) landmarkKeys.put(landmark.owner(), landmark.id());
		});
		SurveyorClientEvents.Invoke.landmarksAdded(summary, landmarkKeys);
	}

	default void addChunk(ResourceKey<Level> dimension, ChunkPos pos, boolean updateClient) {
		RegionPos regionPos = RegionPos.of(pos);
		if (!chunks().contains(dimension, regionPos)) chunks().put(dimension, regionPos, new BitSet(RegionPos.CHUNK_AREA));
		chunks().get(dimension, regionPos).set(RegionPos.chunkToBit(pos));
	}

	default void updateClientForAddStructure(WorldSummary summary, ResourceKey<Structure> structureKey, ChunkPos pos) {
		SurveyorClientEvents.Invoke.structuresAdded(summary, structureKey, pos);
	}

	default void addStructure(ResourceKey<Level> dimension, ResourceKey<Structure> structureKey, ChunkPos pos) {
		if (!starts().contains(dimension, structureKey)) starts().put(dimension, structureKey, new LongOpenHashSet());
		starts().get(dimension, structureKey).add(pos.toLong());
	}

	default void mergeStructures(ResourceKey<Level> dimension, ResourceKey<Structure> structureKey, LongSet starts) {
		if (!starts().contains(dimension, structureKey)) starts().put(dimension, structureKey, new LongOpenHashSet());
		starts().get(dimension, structureKey).addAll(starts);
	}

	default void replaceStructures(Table<ResourceKey<Level>, ResourceKey<Structure>, LongSet> starts) {
		for (ResourceKey<Level> dimension : starts.rowKeySet()) { // merge first for client updates
			starts.row(dimension).forEach((key, startSet) -> mergeStructures(dimension, key, startSet));
		}
		// Fix: this put the argument into itself, so the old share group's structures were never dropped.
		starts().clear();
		starts().putAll(starts); // then replace to ditch anything else
	}

	default CompoundTag write(CompoundTag nbt) {
		CompoundTag terrainCompound = new CompoundTag();
		chunks().rowMap().forEach((dimension, map) -> {
			LongList regionLongs = new LongArrayList();
			for (Map.Entry<RegionPos, BitSet> entry : map.entrySet()) {
				regionLongs.add(entry.getKey().toLong());
				if (entry.getValue().cardinality() == RegionPos.CHUNK_AREA) {
					regionLongs.add(-1);
				} else {
					long[] chunks = entry.getValue().toLongArray();
					regionLongs.add(chunks.length);
					regionLongs.addAll(LongList.of(chunks));
				}
			}
			terrainCompound.putLongArray(dimension.location().toString(), regionLongs.toLongArray());
		});
		nbt.put(KEY_EXPLORED_TERRAIN, terrainCompound);

		CompoundTag structuresCompound = new CompoundTag();
		starts().rowMap().forEach((dimension, map) -> {
			CompoundTag worldStructuresCompound = new CompoundTag();
			for (ResourceKey<Structure> structure : map.keySet()) {
				worldStructuresCompound.putLongArray(structure.location().toString(), map.get(structure).toLongArray());
			}
			structuresCompound.put(dimension.location().toString(), worldStructuresCompound);
		});
		nbt.put(KEY_EXPLORED_STRUCTURES, structuresCompound);
		return nbt;
	}

	default void read(CompoundTag nbt) {
		CompoundTag terrainCompound = nbt.getCompound(KEY_EXPLORED_TERRAIN);
		chunks().clear();
		starts().clear();
		for (String dimensionString : terrainCompound.getAllKeys()) {
			ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(dimensionString));
			long[] regionArray = terrainCompound.getLongArray(dimensionString);
			for (int i = 0; i + 1 < regionArray.length; i += 2) {
				RegionPos regionPos = RegionPos.of(regionArray[i]);
				int bitLength = (int) regionArray[i + 1];
				if (bitLength == -1) {
					BitSet set = new BitSet(RegionPos.CHUNK_AREA);
					set.set(0, RegionPos.CHUNK_AREA);
					chunks().put(dimension, regionPos, set);
				} else {
					long[] bitArray = new long[bitLength];
					System.arraycopy(regionArray, i + 2, bitArray, 0, bitLength);
					chunks().put(dimension, regionPos, BitSet.valueOf(bitArray));
					i += bitLength;
				}
			}
		}

		CompoundTag structuresCompound = nbt.getCompound(KEY_EXPLORED_STRUCTURES);
		for (String dimensionString : structuresCompound.getAllKeys()) {
			ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(dimensionString));
			CompoundTag worldStructuresCompound = structuresCompound.getCompound(dimensionString);
			for (String key : worldStructuresCompound.getAllKeys()) {
				starts().put(dimension, ResourceKey.create(Registries.STRUCTURE, ResourceLocation.tryParse(key)), new LongOpenHashSet(worldStructuresCompound.getLongArray(key)));
			}
		}
	}
}
