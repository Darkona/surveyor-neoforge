package folk.sisby.surveyor.structure;

import folk.sisby.surveyor.util.ArrayUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public class StructurePieceSummary extends StructurePiece {
	protected final CompoundTag pieceNbt;
	protected final ResourceKey<StructurePieceType> typeKey;

	public StructurePieceSummary(StructurePieceType type, int chainLength, BoundingBox boundingBox, CompoundTag pieceNbt) {
		super(type, chainLength, boundingBox);
		this.typeKey = BuiltInRegistries.STRUCTURE_PIECE.getResourceKey(type).orElseThrow();
		this.pieceNbt = pieceNbt;
	}

	public StructurePieceSummary(CompoundTag nbt) {
		super(BuiltInRegistries.STRUCTURE_PIECE.get(ResourceLocation.parse(nbt.getString("id"))), nbt); // Might set the type as null
		this.typeKey = ResourceKey.create(Registries.STRUCTURE_PIECE, ResourceLocation.parse(nbt.getString("id")));
		this.pieceNbt = nbt.getCompound("nbt");
	}

	public static StructurePieceSummary fromPiece(StructurePieceSerializationContext context, StructurePiece piece, boolean withNbt) {
		StructurePieceSummary summary = new StructurePieceSummary(piece.getType(), piece.getGenDepth(), piece.getBoundingBox(), new CompoundTag());
		if (withNbt) {
			CompoundTag summaryNbt = summary.toNbt();
			CompoundTag pieceNbt = piece.createTag(context);
			for (String key : summaryNbt.getAllKeys()) {
				pieceNbt.remove(key);
			}
			for (String key : pieceNbt.getAllKeys()) {
				summary.pieceNbt.put(key, pieceNbt.get(key));
			}
		}
		return summary;
	}

	public final CompoundTag toNbt() {
		CompoundTag nbt = new CompoundTag();
		nbt.putString("id", typeKey.location().toString());
		nbt.putIntArray("BB", ArrayUtil.ofBlockBox(this.boundingBox));
		Direction direction = this.getOrientation();
		nbt.putInt("O", direction == null ? -1 : direction.get2DDataValue());
		nbt.putInt("GD", this.genDepth);
		this.addAdditionalSaveData(null, nbt);  // context only used for writeNbt
		return nbt;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
		if (!pieceNbt.isEmpty()) nbt.put("nbt", pieceNbt);
	}

	@Override
	public void postProcess(WorldGenLevel world, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
	}

	public CompoundTag getPieceNbt() {
		return pieceNbt;
	}
}
