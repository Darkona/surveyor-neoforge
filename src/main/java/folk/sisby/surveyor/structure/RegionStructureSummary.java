package folk.sisby.surveyor.structure;

import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashBasedTable;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.util.RegionPos;
import folk.sisby.surveyor.util.SafeNbtWriter;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.function.Predicate;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public class RegionStructureSummary {
	public static final String KEY_STRUCTURES = "structures";
	public static final String KEY_STARTS = "starts";
	public static final String KEY_PIECES = "pieces";

	// Fix (sisby-folk/surveyor#115): every start is indexed, but pieces are only held while the region is loaded; the
	// file is read on demand and dropped again when no chunk of the region is loaded. State is guarded by this monitor.
	private final Map<ResourceKey<Structure>, LongSet> index = new HashMap<>();
	private @Nullable Table<ResourceKey<Structure>, ChunkPos, StructureStartSummary> starts;
	private @Nullable File file;
	private final @Nullable RegionPos regionPos;
	protected boolean dirty = false;
	private int pendingWrites = 0;
	private boolean unloadAfterWrite = false;
	private boolean loadFailed = false;

	RegionStructureSummary() {
		this(null, null);
	}

	/**
	 * A new, empty region, saved to and reloaded from {@code file}.
	 */
	RegionStructureSummary(@Nullable File file, @Nullable RegionPos regionPos) {
		this.file = file;
		this.regionPos = regionPos;
		this.starts = HashBasedTable.create();
	}

	RegionStructureSummary(Table<ResourceKey<Structure>, ChunkPos, StructureStartSummary> starts) {
		this(null, null);
		starts.cellSet().forEach(c -> putLoaded(c.getRowKey(), c.getColumnKey(), c.getValue()));
	}

	/**
	 * Indexes a region file's starts without building their pieces; they're read when first asked for.
	 */
	static RegionStructureSummary index(File file, RegionPos regionPos, CompoundTag nbt) {
		RegionStructureSummary region = new RegionStructureSummary(file, regionPos);
		region.starts = null;
		CompoundTag structuresCompound = nbt.getCompound(KEY_STRUCTURES);
		for (String structureId : structuresCompound.getAllKeys()) {
			ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(structureId));
			for (String posKey : structuresCompound.getCompound(structureId).getCompound(KEY_STARTS).getAllKeys()) {
				region.index.computeIfAbsent(key, k -> new LongOpenHashSet()).add(parsePos(posKey));
			}
		}
		return region;
	}

	private static long parsePos(String posKey) {
		int comma = posKey.indexOf(',');
		return ChunkPos.asLong(Integer.parseInt(posKey.substring(0, comma)), Integer.parseInt(posKey.substring(comma + 1)));
	}

	protected static StructureStartSummary summarisePieces(StructurePieceSerializationContext context, StructureStart start) {
		List<StructurePieceSummary> pieces = new ArrayList<>();
		for (StructurePiece piece : start.getPieces()) {
			if (piece.getType().equals(StructurePieceType.JIGSAW)) {
				pieces.addAll(JigsawPieceSummary.tryFromPiece(piece));
			} else {
				pieces.add(StructurePieceSummary.fromPiece(context, piece, start.getPieces().size() <= 10));
			}
		}
		return new StructureStartSummary(pieces);
	}

	public static StructurePieceSummary readStructurePieceNbt(CompoundTag nbt) {
		if (nbt.getString("id").equals(BuiltInRegistries.STRUCTURE_PIECE.getKey(StructurePieceType.JIGSAW).toString())) {
			return new JigsawPieceSummary(nbt);
		} else {
			return new StructurePieceSummary(nbt);
		}
	}

	private static StructureStartSummary readStart(CompoundTag startCompound) {
		List<StructurePieceSummary> pieces = new ArrayList<>();
		for (Tag pieceElement : startCompound.getList(KEY_PIECES, Tag.TAG_COMPOUND)) {
			pieces.add(readStructurePieceNbt((CompoundTag) pieceElement));
		}
		return new StructureStartSummary(pieces);
	}

	protected static RegionStructureSummary readNbt(CompoundTag nbt) {
		Table<ResourceKey<Structure>, ChunkPos, StructureStartSummary> starts = HashBasedTable.create();
		CompoundTag structuresCompound = nbt.getCompound(KEY_STRUCTURES);
		for (String structureId : structuresCompound.getAllKeys()) {
			ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(structureId));
			CompoundTag startsCompound = structuresCompound.getCompound(structureId).getCompound(KEY_STARTS);
			for (String posKey : startsCompound.getAllKeys()) {
				starts.put(key, new ChunkPos(parsePos(posKey)), readStart(startsCompound.getCompound(posKey)));
			}
		}
		return new RegionStructureSummary(starts);
	}

	/**
	 * Reads the pieces of the indexed starts from the file, if they aren't in memory.
	 */
	private synchronized Table<ResourceKey<Structure>, ChunkPos, StructureStartSummary> load() {
		if (starts != null) return starts;
		starts = HashBasedTable.create();
		loadFailed = false;
		if (index.isEmpty() || file == null || !file.exists()) return starts;
		try {
			CompoundTag structuresCompound = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap()).getCompound(KEY_STRUCTURES);
			int missing = 0;
			for (Map.Entry<ResourceKey<Structure>, LongSet> entry : index.entrySet()) {
				CompoundTag startsCompound = structuresCompound.getCompound(entry.getKey().location().toString()).getCompound(KEY_STARTS);
				for (LongIterator it = entry.getValue().iterator(); it.hasNext(); ) {
					ChunkPos pos = new ChunkPos(it.nextLong());
					String posKey = pos.x + "," + pos.z;
					if (startsCompound.contains(posKey, Tag.TAG_COMPOUND)) {
						starts.put(entry.getKey(), pos, readStart(startsCompound.getCompound(posKey)));
					} else {
						missing++;
					}
				}
			}
			if (missing > 0) Surveyor.LOGGER.warn("[Surveyor] Structure region {} is missing {} indexed starts.", file.getName(), missing);
		} catch (IOException | RuntimeException e) {
			loadFailed = true;
			Surveyor.LOGGER.error("[Surveyor] Error reading structure region {}; it won't be overwritten this session.", file.getName(), e);
		}
		return starts;
	}

	public boolean contains(Level world, StructureStart start) {
		ResourceKey<Structure> key = world.registryAccess().registryOrThrow(Registries.STRUCTURE).getResourceKey(start.getStructure()).orElse(null);
		if (key == null) {
			Surveyor.LOGGER.error("Encountered an unregistered structure! {} | {}", start, start.getStructure());
			return true;
		}
		return contains(key, start.getChunkPos());
	}

	public synchronized boolean contains(ResourceKey<Structure> key, ChunkPos pos) {
		LongSet positions = index.get(key);
		return positions != null && positions.contains(pos.toLong());
	}

	public synchronized StructureStartSummary get(ResourceKey<Structure> key, ChunkPos pos) {
		if (!contains(key, pos)) return null;
		return load().get(key, pos);
	}

	public synchronized Multimap<ResourceKey<Structure>, ChunkPos> keySet() {
		Multimap<ResourceKey<Structure>, ChunkPos> keySet = HashMultimap.create();
		index.forEach((key, positions) -> {
			for (LongIterator it = positions.iterator(); it.hasNext(); ) keySet.put(key, new ChunkPos(it.nextLong()));
		});
		return keySet;
	}

	public void put(ServerLevel world, StructureStart start) {
		ResourceKey<Structure> key = world.registryAccess().registryOrThrow(Registries.STRUCTURE).getResourceKey(start.getStructure()).orElse(null);
		if (key == null) return; // contains() already logged it
		StructureStartSummary summary = summarisePieces(StructurePieceSerializationContext.fromLevel(world), start);
		put(key, start.getChunkPos(), summary);
	}

	public synchronized void put(ResourceKey<Structure> key, ChunkPos pos, StructureStartSummary summary) {
		load(); // the other starts must be in memory to be written back
		putLoaded(key, pos, summary);
		dirty();
	}

	private synchronized void putLoaded(ResourceKey<Structure> key, ChunkPos pos, StructureStartSummary summary) {
		starts.put(key, pos, summary);
		index.computeIfAbsent(key, k -> new LongOpenHashSet()).add(pos.toLong());
	}

	/**
	 * Drops the starts of structures that aren't kept, like the load used to for structures without a saved type.
	 */
	synchronized void retainKeys(Predicate<ResourceKey<Structure>> keep) {
		index.keySet().removeIf(keep.negate());
		if (starts != null) starts.rowMap().keySet().removeIf(keep.negate());
	}

	protected synchronized CompoundTag writeNbt(CompoundTag nbt) {
		CompoundTag structuresCompound = new CompoundTag();
		load().rowMap().forEach((key, inner) -> {
			CompoundTag structureCompound = new CompoundTag();
			CompoundTag startsCompound = new CompoundTag();
			inner.forEach((pos, summary) -> {
				ListTag pieceList = new ListTag(summary.getChildren().stream().map(p -> (Tag) p.toNbt()).toList(), Tag.TAG_COMPOUND);
				CompoundTag startCompound = new CompoundTag();
				startCompound.put(KEY_PIECES, pieceList);
				startsCompound.put("%s,%s".formatted(pos.x, pos.z), startCompound);
			});
			structureCompound.put(KEY_STARTS, startsCompound);
			structuresCompound.put(key.location().toString(), structureCompound);
		});
		nbt.put(KEY_STRUCTURES, structuresCompound);
		return nbt;
	}

	/**
	 * Writes the region if it changed, then drops its pieces once written if {@code unload}.
	 *
	 * @return whether a write was queued
	 */
	boolean save(File file, boolean unload) {
		CompoundTag nbt;
		synchronized (this) {
			if (!isDirty()) {
				if (unload) unload();
				return false;
			}
			if (loadFailed) {
				Surveyor.LOGGER.error("[Surveyor] Not saving structure region {}: it couldn't be read, and saving would drop its structures.", file.getName());
				return false;
			}
			nbt = writeNbt(new CompoundTag());
			this.file = file;
			dirty = false;
			pendingWrites++;
			unloadAfterWrite = unload;
		}
		SafeNbtWriter.write(file.toPath(), nbt).whenComplete((v, t) -> {
			synchronized (RegionStructureSummary.this) {
				pendingWrites--;
				if (unloadAfterWrite && !dirty && pendingWrites == 0) starts = null;
			}
		});
		return true;
	}

	/**
	 * Drops the pieces from memory, once nothing is left to write; they're read again when asked for.
	 */
	synchronized void unload() {
		if (starts == null || dirty || file == null) return;
		if (pendingWrites == 0) {
			starts = null;
		} else {
			unloadAfterWrite = true;
		}
	}

	public synchronized boolean isLoaded() {
		return starts != null;
	}

	public boolean isUnloaded(Level world) {
		if (regionPos == null) return false;
		int chunkX = regionPos.chunkX(), chunkZ = regionPos.chunkZ();
		for (int x = 0; x < RegionPos.CHUNK_SIZE; x++) {
			for (int z = 0; z < RegionPos.CHUNK_SIZE; z++) {
				if (world.hasChunk(chunkX + x, chunkZ + z)) return false;
			}
		}
		return true;
	}

	public synchronized boolean isDirty() {
		return dirty && Surveyor.CONFIG.structures != SystemMode.FROZEN;
	}

	private synchronized void dirty() {
		dirty = true;
	}
}
