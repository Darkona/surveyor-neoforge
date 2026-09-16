package folk.sisby.surveyor;

import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.util.RegionPos;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class GroupExploration implements SurveyorExploration {
	private final SurveyorExploration[] members;
	private final Set<UUID> sharedPlayers;
	private SurveyorExploration materialised;

	public GroupExploration(List<SurveyorExploration> members) {
		this.members = members.toArray(SurveyorExploration[]::new);
		Set<UUID> players = new HashSet<>();
		for (SurveyorExploration member : this.members) players.addAll(member.sharedPlayers());
		this.sharedPlayers = players;
	}

	private SurveyorExploration materialised() {
		if (materialised == null) materialised = PlayerSummary.OfflinePlayerSummary.OfflinePlayerExploration.ofMerged(new HashSet<>(List.of(members)));
		return materialised;
	}

	@Override
	public Table<ResourceKey<Level>, RegionPos, BitSet> chunks() {
		return materialised().chunks();
	}

	@Override
	public Table<ResourceKey<Level>, ResourceKey<Structure>, LongSet> starts() {
		return materialised().starts();
	}

	@Override
	public Set<UUID> sharedPlayers() {
		return sharedPlayers;
	}

	@Override
	public boolean personal() {
		return false;
	}

	private static boolean terrainServerWide() {
		return Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SERVER);
	}

	private static boolean structuresServerWide() {
		return Surveyor.CONFIG.networking.structures.atLeast(NetworkMode.SERVER);
	}

	private ResourceKey<Level> terrainRowsDimension;
	private Map<RegionPos, BitSet>[] terrainRows;
	private ResourceKey<Level> startRowsDimension;
	private Map<ResourceKey<Structure>, LongSet>[] startRows;

	@SuppressWarnings("unchecked")
	private Map<RegionPos, BitSet>[] terrainRows(ResourceKey<Level> dimension) {
		if (terrainRows == null || dimension != terrainRowsDimension) {
			terrainRows = new Map[members.length];
			for (int i = 0; i < members.length; i++) terrainRows[i] = members[i].chunks().row(dimension);
			terrainRowsDimension = dimension;
		}
		return terrainRows;
	}

	@SuppressWarnings("unchecked")
	private Map<ResourceKey<Structure>, LongSet>[] startRows(ResourceKey<Level> dimension) {
		if (startRows == null || dimension != startRowsDimension) {
			startRows = new Map[members.length];
			for (int i = 0; i < members.length; i++) startRows[i] = members[i].starts().row(dimension);
			startRowsDimension = dimension;
		}
		return startRows;
	}

	private boolean memberHasChunk(ResourceKey<Level> dimension, RegionPos regionPos, int bit) {
		for (Map<RegionPos, BitSet> row : terrainRows(dimension)) {
			BitSet bits = row.get(regionPos);
			if (bits != null && bits.get(bit)) return true;
		}
		return false;
	}

	private boolean memberHasStart(ResourceKey<Level> dimension, ResourceKey<Structure> structure, long pos) {
		for (Map<ResourceKey<Structure>, LongSet> row : startRows(dimension)) {
			LongSet starts = row.get(structure);
			if (starts != null && starts.contains(pos)) return true;
		}
		return false;
	}

	private boolean membersHaveTerrain(ResourceKey<Level> dimension) {
		for (Map<RegionPos, BitSet> row : terrainRows(dimension)) if (!row.isEmpty()) return true;
		return false;
	}

	private boolean membersHaveStructures(ResourceKey<Level> dimension) {
		for (Map<ResourceKey<Structure>, LongSet> row : startRows(dimension)) if (!row.isEmpty()) return true;
		return false;
	}

	@Override
	public boolean exploredChunk(ResourceKey<Level> dimension, ChunkPos pos) {
		return terrainServerWide() || memberHasChunk(dimension, RegionPos.of(pos), RegionPos.chunkToBit(pos));
	}

	@Override
	public boolean exploredStructure(ResourceKey<Level> dimension, ResourceKey<Structure> structure, ChunkPos pos) {
		return structuresServerWide() || memberHasStart(dimension, structure, pos.toLong());
	}

	@Override
	public BitSet limit(ResourceKey<Level> dimension, RegionPos regionPos, BitSet chunks) {
		if (terrainServerWide()) return chunks;
		for (int bit = chunks.nextSetBit(0); bit >= 0; bit = chunks.nextSetBit(bit + 1)) {
			if (!memberHasChunk(dimension, regionPos, bit)) chunks.clear(bit);
		}
		return chunks;
	}

	@Override
	public Map<RegionPos, BitSet> limit(ResourceKey<Level> dimension, Map<RegionPos, BitSet> chunks) {
		if (terrainServerWide()) return chunks;
		if (!membersHaveTerrain(dimension)) {
			chunks.clear();
		} else {
			chunks.forEach((regionPos, set) -> limit(dimension, regionPos, set));
		}
		return chunks;
	}

	@Override
	public Multimap<ResourceKey<Structure>, ChunkPos> limit(ResourceKey<Level> dimension, Multimap<ResourceKey<Structure>, ChunkPos> starts) {
		if (structuresServerWide()) return starts;
		if (!membersHaveStructures(dimension)) {
			starts.clear();
		} else {
			starts.entries().removeIf(e -> !memberHasStart(dimension, e.getKey(), e.getValue().toLong()));
		}
		return starts;
	}
}
