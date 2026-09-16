package folk.sisby.surveyor.structure;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public class JigsawPieceSummary extends StructurePieceSummary {
	public static final String KEY_POS = "pos";
	public static final String KEY_DELTA_Y = "deltaY";
	public static final String KEY_ROTATION = "rotation";
	public static final String KEY_JUNCTIONS = "junctions";
	public static final BiMap<String, StructurePoolElementType<?>> TYPE_KEYS = HashBiMap.create(Map.of(
		"single", StructurePoolElementType.SINGLE,
		"feature", StructurePoolElementType.FEATURE
	));

	// Piece Data
	protected final BlockPos pos;
	protected final int deltaY;
	protected final Rotation rotation;
	protected final List<JigsawJunction> junctions;

	// Element Data
	protected final StructurePoolElementType<?> elementType;
	protected final ResourceLocation id;

	public JigsawPieceSummary(BlockPos pos, int deltaY, Rotation rotation, StructurePoolElementType<?> elementType, ResourceLocation id, int chainLength, BoundingBox boundingBox, List<JigsawJunction> junctions) {
		super(StructurePieceType.JIGSAW, chainLength, boundingBox, new CompoundTag());
		this.pos = pos;
		this.deltaY = deltaY;
		this.rotation = rotation;
		this.elementType = elementType;
		this.id = id;
		this.junctions = junctions;
	}

	public JigsawPieceSummary(CompoundTag nbt) {
		super(nbt);
		this.pos = BlockPos.of(nbt.getLong(KEY_POS));
		this.deltaY = nbt.getInt(KEY_DELTA_Y);
		this.rotation = Rotation.values()[nbt.getInt(KEY_ROTATION)];
		this.junctions = new ArrayList<>();
		if (nbt.contains(KEY_JUNCTIONS)) {
			int[] junctionArray = nbt.getIntArray(KEY_JUNCTIONS);
			for (int i = 4; i <= junctionArray.length; i += 5) {
				junctions.add(new JigsawJunction(junctionArray[i - 4], junctionArray[i - 3], junctionArray[i - 2], junctionArray[i - 1], StructureTemplatePool.Projection.values()[junctionArray[i]]));
			}
		}
		String idKey = TYPE_KEYS.keySet().stream().filter(nbt::contains).findFirst().orElseThrow();
		this.elementType = TYPE_KEYS.get(idKey);
		this.id = ResourceLocation.parse(nbt.getString(idKey));
	}

	public static List<StructurePieceSummary> tryFromElement(StructurePoolElement poolElement, PoolElementStructurePiece piece) {
		if (poolElement instanceof ListPoolElement listElement) {
			List<StructurePieceSummary> allSummaries = new ArrayList<>();
			listElement.elements.forEach(e -> allSummaries.addAll(tryFromElement(e, piece)));
			return allSummaries;
		} else if (poolElement instanceof SinglePoolElement singleElement && singleElement.template.left().isPresent()) {
			return List.of(new JigsawPieceSummary(piece.getPosition(), piece.getGroundLevelDelta(), piece.getRotation(), StructurePoolElementType.SINGLE, singleElement.template.left().orElseThrow(), piece.getGenDepth(), poolElement.getBoundingBox(piece.structureTemplateManager, piece.getPosition(), piece.getRotation()), piece.getJunctions()));
		} else if (poolElement instanceof FeaturePoolElement featureElement && featureElement.feature.unwrapKey().isPresent()) {
			return List.of(new JigsawPieceSummary(piece.getPosition(), piece.getGroundLevelDelta(), piece.getRotation(), StructurePoolElementType.FEATURE, featureElement.feature.unwrapKey().orElseThrow().location(), piece.getGenDepth(), poolElement.getBoundingBox(piece.structureTemplateManager, piece.getPosition(), piece.getRotation()), piece.getJunctions()));
		}
		return List.of();
	}

	public static List<StructurePieceSummary> tryFromPiece(StructurePiece piece) {
		if (piece instanceof PoolElementStructurePiece poolPiece) {
			return tryFromElement(poolPiece.getElement(), poolPiece);
		}
		return List.of();
	}

	@Override
	public void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag nbt) {
		super.addAdditionalSaveData(context, nbt);
		nbt.putLong(KEY_POS, this.pos.asLong());
		nbt.putInt(KEY_DELTA_Y, this.deltaY);
		nbt.putInt(KEY_ROTATION, this.rotation.ordinal());
		String idKey = TYPE_KEYS.inverse().get(elementType);
		nbt.putString(idKey, id.toString());
		if (!junctions.isEmpty()) {
			int[] junctionArray = new int[junctions.size() * 5];
			for (int i = 0; i < junctions.size(); i++) {
				JigsawJunction j = junctions.get(i);
				junctionArray[i * 5] = j.getSourceX();
				junctionArray[i * 5 + 1] = j.getSourceGroundY();
				junctionArray[i * 5 + 2] = j.getSourceZ();
				junctionArray[i * 5 + 3] = j.getDeltaY();
				junctionArray[i * 5 + 4] = j.getDestProjection().ordinal();
			}
			nbt.putIntArray(KEY_JUNCTIONS, junctionArray);
		}
	}

	public BlockPos getPos() {
		return pos;
	}

	public int getDeltaY() {
		return deltaY;
	}

	public Rotation getRotation() {
		return rotation;
	}

	public StructurePoolElementType<?> getElementType() {
		return elementType;
	}

	public ResourceLocation getId() {
		return id;
	}

	public List<JigsawJunction> getJunctions() {
		return junctions;
	}
}
