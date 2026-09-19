package folk.sisby.surveyor.structure;

import folk.sisby.surveyor.util.SafeNbtWriter;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Table;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.packet.S2CStructuresAddedPacket;
import folk.sisby.surveyor.util.ChunkUtil;
import folk.sisby.surveyor.util.MapUtil;
import folk.sisby.surveyor.util.RegionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class WorldStructures {
	public static final String KEY_STRUCTURES = "structures";
	public static final String KEY_TYPE = "type";
	public static final String KEY_TAGS = "tags";

	protected final WorldSummary summary;
	protected final Map<RegionPos, RegionStructureSummary> regions = new ConcurrentHashMap<>();
	protected final Map<ResourceKey<Structure>, ResourceKey<StructureType<?>>> structureTypes = new ConcurrentHashMap<>();
	protected final Multimap<ResourceKey<Structure>, TagKey<Structure>> structureTags = Multimaps.synchronizedSetMultimap(HashMultimap.create());
	protected boolean dirty = false;

	public static WorldStructures of(Level world) {
		return Optional.ofNullable(world).map(WorldSummary::of).map(WorldSummary::structures).orElse(null);
	}

	public WorldStructures(WorldSummary summary, Map<RegionPos, RegionStructureSummary> regions, Map<ResourceKey<Structure>, ResourceKey<StructureType<?>>> structureTypes, Multimap<ResourceKey<Structure>, TagKey<Structure>> structureTags) {
		this.summary = summary;
		this.regions.putAll(regions);
		this.structureTypes.putAll(structureTypes);
		this.structureTags.putAll(structureTags);
	}

	public static StructurePieceSummary readStructurePieceNbt(CompoundTag nbt) {
		if (nbt.getString("id").equals(BuiltInRegistries.STRUCTURE_PIECE.getKey(StructurePieceType.JIGSAW).toString())) {
			return new JigsawPieceSummary(nbt);
		} else {
			return new StructurePieceSummary(nbt);
		}
	}

	protected static WorldStructures readNbt(WorldSummary summary, CompoundTag nbt, Map<RegionPos, RegionStructureSummary> regions) {
		Map<ResourceKey<Structure>, ResourceKey<StructureType<?>>> structureTypes = new ConcurrentHashMap<>();
		Multimap<ResourceKey<Structure>, TagKey<Structure>> structureTags = HashMultimap.create();
		CompoundTag structuresCompound = nbt.getCompound(KEY_STRUCTURES);
		for (String structureId : structuresCompound.getAllKeys()) {
			ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(structureId));
			CompoundTag structureCompound = structuresCompound.getCompound(structureId);
			ResourceKey<StructureType<?>> type = ResourceKey.create(Registries.STRUCTURE_TYPE, ResourceLocation.parse(structureCompound.getString(KEY_TYPE)));
			structureTypes.put(key, type);
			Collection<TagKey<Structure>> tags = structureCompound.getList(KEY_TAGS, Tag.TAG_STRING).stream().map(e -> TagKey.create(Registries.STRUCTURE, ResourceLocation.parse(e.getAsString()))).toList();
			structureTags.putAll(key, tags);
		}
		for (RegionStructureSummary region : regions.values()) {
			region.starts.rowMap().keySet().removeIf(k -> !structureTypes.containsKey(k));
		}
		return new WorldStructures(summary, regions, structureTypes, structureTags);
	}

	public static WorldStructures load(WorldSummary summary, File folder) {
		File structuresFile = new File(folder, "structures.dat");
		CompoundTag worldNbt = new CompoundTag();
		if (structuresFile.exists()) {
			try {
				worldNbt = NbtIo.readCompressed(structuresFile.toPath(), NbtAccounter.unlimitedHeap());
			} catch (IOException | ReportedNbtException e) {
				Surveyor.LOGGER.error("[Surveyor] Error loading structure summary file for {}.", summary.dimension().location(), e);
			}
		}
		Map<RegionPos, RegionStructureSummary> regions = new HashMap<>();
		ChunkUtil.getRegionNbt(folder, "s").forEach((pos, nbt) -> regions.put(pos, RegionStructureSummary.readNbt(nbt)));
		return readNbt(summary, worldNbt, regions);
	}

	public static void onChunkLoad(ServerLevel world, LevelChunk chunk) {
		WorldStructures structures = WorldStructures.of(world);
		if (structures == null) return;
		chunk.getAllStarts().forEach((structure, start) -> {
			if (!structures.contains(world, start)) structures.put(world, start);
		});
	}

	public static void onStructurePlace(ServerLevel world, StructureStart start) {
		WorldStructures structures = WorldStructures.of(world);
		if (structures != null && !structures.contains(world, start)) structures.put(world, start);
	}

	public ResourceKey<StructureType<?>> getType(ResourceKey<Structure> key) {
		return structureTypes.get(key);
	}

	public Collection<TagKey<Structure>> getTags(ResourceKey<Structure> key) {
		return structureTags.get(key);
	}

	public boolean contains(Level world, StructureStart start) {
		RegionPos regionPos = RegionPos.of(start.getChunkPos());
		return regions.containsKey(regionPos) && regions.get(regionPos).contains(world, start);
	}

	public boolean contains(ResourceKey<Structure> key, ChunkPos pos) {
		RegionPos regionPos = RegionPos.of(pos);
		return regions.containsKey(regionPos) && regions.get(regionPos).contains(key, pos);
	}

	public StructureStartSummary get(ResourceKey<Structure> key, ChunkPos pos) {
		RegionPos regionPos = RegionPos.of(pos);
		return regions.containsKey(regionPos) ? regions.get(regionPos).get(key, pos) : null;
	}

	public Map<ResourceKey<Structure>, Map<ChunkPos, StructureStartSummary>> asMap(SurveyorExploration exploration) {
		Multimap<ResourceKey<Structure>, ChunkPos> keySet = keySet(exploration);
		Map<ResourceKey<Structure>, Map<ChunkPos, StructureStartSummary>> map = new HashMap<>();
		keySet.forEach((key, pos) -> map.computeIfAbsent(key, k -> new HashMap<>()).put(pos, get(key, pos)));
		return map;
	}

	public Multimap<ResourceKey<Structure>, ChunkPos> keySet(SurveyorExploration exploration) {
		Multimap<ResourceKey<Structure>, ChunkPos> map = HashMultimap.create();
		regions.values().forEach(r -> map.putAll(r.keySet()));
		if (exploration != null) exploration.limit(summary.dimension(), map);
		return map;
	}

	public void put(ServerLevel world, StructureStart start) {
		if (Surveyor.CONFIG.structures == SystemMode.FROZEN) return;
		RegionPos regionPos = RegionPos.of(start.getChunkPos());
		ResourceKey<Structure> key = world.registryAccess().registryOrThrow(Registries.STRUCTURE).getResourceKey(start.getStructure()).orElse(null);
		if (key == null) {
			Surveyor.LOGGER.error("Encountered an unregistered structure! {} | {}", start, start.getStructure());
			return;
		}
		Optional<ResourceKey<StructureType<?>>> type = world.registryAccess().registryOrThrow(Registries.STRUCTURE_TYPE).getResourceKey(start.getStructure().type());
		if (!start.isValid()) {
			Surveyor.LOGGER.error("Cowardly refusing to save structure {} as it has no pieces! Report this to the structure mod author!", key.location());
			return;
		}
		if (type.isEmpty()) {
			Surveyor.LOGGER.error("Cowardly refusing to save structure {} as it has no structure type! Report this to the structure mod author!", key.location());
			return;
		}
		regions.computeIfAbsent(regionPos, k -> new RegionStructureSummary()).put(world, start);
		List<TagKey<Structure>> tags = world.registryAccess().registryOrThrow(Registries.STRUCTURE).wrapAsHolder(start.getStructure()).tags().toList();
		structureTypes.put(key, type.orElseThrow());
		structureTags.putAll(key, tags);
		dirty();
		SurveyorEvents.Invoke.structuresAdded(summary, key, start.getChunkPos());
	}

	public void put(ResourceKey<Structure> key, ChunkPos pos, StructureStartSummary start, ResourceKey<StructureType<?>> type, Collection<TagKey<Structure>> tagKeys) {
		if (Surveyor.CONFIG.structures == SystemMode.FROZEN) return;
		RegionPos regionPos = RegionPos.of(pos);
		regions.computeIfAbsent(regionPos, k -> new RegionStructureSummary()).put(key, pos, start);
		structureTypes.put(key, type);
		structureTags.putAll(key, tagKeys);
		dirty();
		SurveyorEvents.Invoke.structuresAdded(summary, key, pos);
	}

	protected CompoundTag writeNbt(CompoundTag nbt) {
		CompoundTag structuresCompound = new CompoundTag();
		structureTypes.forEach((key, starts) -> {
			CompoundTag structureCompound = new CompoundTag();
			structureCompound.putString(KEY_TYPE, structureTypes.get(key).location().toString());
			structureCompound.put(KEY_TAGS, new ListTag(structureTags.get(key).stream().map(t -> (Tag) StringTag.valueOf(t.location().toString())).toList(), Tag.TAG_STRING));
			structuresCompound.put(key.location().toString(), structureCompound);
		});
		nbt.put(KEY_STRUCTURES, structuresCompound);
		return nbt;
	}

	public int save(File folder) {
		List<RegionPos> savedRegions = new ArrayList<>();
		if (isDirty()) {
			File structureFile = new File(folder, "structures.dat");
			CompoundTag structureCompound = writeNbt(new CompoundTag());
			SafeNbtWriter.write(structureFile.toPath(), structureCompound);
			dirty = false;
			regions.forEach((pos, summary) -> {
				if (!summary.isDirty()) return;
				savedRegions.add(pos);
				CompoundTag regionCompound = summary.writeNbt(new CompoundTag());
				File regionFile = new File(folder, "s.%d.%d.dat".formatted(pos.x(), pos.z()));
				SafeNbtWriter.write(regionFile.toPath(), regionCompound);
				summary.dirty = false;
			});
		}
		return savedRegions.size();
	}

	public Multimap<ResourceKey<Structure>, ChunkPos> readUpdatePacket(S2CStructuresAddedPacket packet) {
		if (Surveyor.CONFIG.structures == SystemMode.FROZEN) return HashMultimap.create();
		packet.starts().cellSet().forEach(c -> put(c.getRowKey(), c.getColumnKey(), c.getValue(), packet.types().get(c.getRowKey()), packet.tags().get(c.getRowKey())));
		return MapUtil.keyMultiMap(packet.starts());
	}

	public S2CStructuresAddedPacket createUpdatePacket(boolean shared, Multimap<ResourceKey<Structure>, ChunkPos> keySet) {
		Table<ResourceKey<Structure>, ChunkPos, StructureStartSummary> packetStructures = HashBasedTable.create();
		Map<ResourceKey<Structure>, ResourceKey<StructureType<?>>> packetTypes = new HashMap<>();
		Multimap<ResourceKey<Structure>, TagKey<Structure>> packetTags = HashMultimap.create();
		keySet.forEach((key, pos) -> packetStructures.put(key, pos, get(key, pos)));
		for (ResourceKey<Structure> key : keySet.keySet()) {
			packetTypes.put(key, getType(key));
			packetTags.putAll(key, getTags(key));
		}
		return new S2CStructuresAddedPacket(summary.dimension(), shared, packetStructures, packetTypes, packetTags);
	}

	public boolean isDirty() {
		return (dirty || regions.values().stream().anyMatch(RegionStructureSummary::isDirty)) && Surveyor.CONFIG.structures != SystemMode.FROZEN;
	}

	private void dirty() {
		dirty = true;
	}
}
