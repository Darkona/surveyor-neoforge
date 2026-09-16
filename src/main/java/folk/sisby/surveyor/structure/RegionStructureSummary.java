package folk.sisby.surveyor.structure;

import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.google.common.collect.HashBasedTable;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.util.MapUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

	protected final Table<ResourceKey<Structure>, ChunkPos, StructureStartSummary> starts = Tables.synchronizedTable(HashBasedTable.create());
	protected boolean dirty = false;

	RegionStructureSummary() {
	}

	RegionStructureSummary(Table<ResourceKey<Structure>, ChunkPos, StructureStartSummary> starts) {
		this.starts.putAll(starts);
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

	protected static RegionStructureSummary readNbt(CompoundTag nbt) {
		Table<ResourceKey<Structure>, ChunkPos, StructureStartSummary> starts = HashBasedTable.create();
		CompoundTag structuresCompound = nbt.getCompound(KEY_STRUCTURES);
		for (String structureId : structuresCompound.getAllKeys()) {
			ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(structureId));
			CompoundTag structureCompound = structuresCompound.getCompound(structureId);
			CompoundTag startsCompound = structureCompound.getCompound(KEY_STARTS);
			for (String posKey : startsCompound.getAllKeys()) {
				int x = Integer.parseInt(posKey.split(",")[0]);
				int z = Integer.parseInt(posKey.split(",")[1]);
				CompoundTag startCompound = startsCompound.getCompound(posKey);
				List<StructurePieceSummary> pieces = new ArrayList<>();
				for (Tag pieceElement : startCompound.getList(KEY_PIECES, Tag.TAG_COMPOUND)) {
					pieces.add(readStructurePieceNbt((CompoundTag) pieceElement));
				}
				starts.put(key, new ChunkPos(x, z), new StructureStartSummary(pieces));
			}
		}
		return new RegionStructureSummary(starts);
	}

	public boolean contains(Level world, StructureStart start) {
		ResourceKey<Structure> key = world.registryAccess().registryOrThrow(Registries.STRUCTURE).getResourceKey(start.getStructure()).orElse(null);
		if (key == null) {
			Surveyor.LOGGER.error("Encountered an unregistered structure! {} | {}", start, start.getStructure());
			return true;
		}
		return contains(key, start.getChunkPos());
	}

	public boolean contains(ResourceKey<Structure> key, ChunkPos pos) {
		return starts.contains(key, pos);
	}

	public StructureStartSummary get(ResourceKey<Structure> key, ChunkPos pos) {
		return starts.get(key, pos);
	}

	public Multimap<ResourceKey<Structure>, ChunkPos> keySet() {
		return MapUtil.keyMultiMap(starts);
	}

	public void put(ServerLevel world, StructureStart start) {
		ResourceKey<Structure> key = world.registryAccess().registryOrThrow(Registries.STRUCTURE).getResourceKey(start.getStructure()).orElseThrow();
		StructureStartSummary summary = summarisePieces(StructurePieceSerializationContext.fromLevel(world), start);
		put(key, start.getChunkPos(), summary);
	}

	public void put(ResourceKey<Structure> key, ChunkPos pos, StructureStartSummary summary) {
		starts.put(key, pos, summary);
		dirty();
	}

	protected CompoundTag writeNbt(CompoundTag nbt) {
		CompoundTag structuresCompound = new CompoundTag();
		starts.rowMap().forEach((key, inner) -> {
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

	public boolean isDirty() {
		return dirty && Surveyor.CONFIG.structures != SystemMode.FROZEN;
	}

	private void dirty() {
		dirty = true;
	}
}
