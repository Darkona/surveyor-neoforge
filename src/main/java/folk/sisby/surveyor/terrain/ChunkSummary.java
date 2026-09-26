package folk.sisby.surveyor.terrain;

import folk.sisby.surveyor.util.ChunkUtil;
import folk.sisby.surveyor.util.RegistryPalette;
import folk.sisby.surveyor.util.uints.UInts;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public class ChunkSummary {
	public static final int MINIMUM_AIR_DEPTH = 2;
	public static final String KEY_AIR_COUNT = "air";
	public static final String KEY_LAYERS = "layers";

	protected final Integer airCount;
	protected final TreeMap<Integer, @Nullable LayerSummary> layers = new TreeMap<>();

	public ChunkSummary(Level world, LevelChunk chunk, int[] layerHeights, RegistryPalette<Biome> biomePalette, RegistryPalette<Block> blockPalette, boolean countAir) {
		this.airCount = countAir ? ChunkUtil.airCount(chunk) : null;
		LevelChunkSection[] rawSections = chunk.getSections();
		SectionSummary[] sections = new SectionSummary[rawSections.length];
		for (int i = 0; i < rawSections.length; i++) {
			sections[i] = SectionSummary.ofSection(rawSections[i]);
		}
		int chunkX = chunk.getPos().getMinBlockX();
		int chunkZ = chunk.getPos().getMinBlockZ();
		ChunkScan.Floors floors = ChunkScan.scan(sections, chunk.getMinSection(), chunkX, chunkZ, world.getMinBuildHeight(), world.getMaxBuildHeight(), layerHeights, world, biomePalette::findOrAdd, blockPalette::findOrAdd);
		putLayers(layerHeights, ChunkScan.layers(floors, chunkX, chunkZ, layerHeights, pos -> world.getBrightness(LightLayer.BLOCK, pos), null, null));
	}

	/**
	 * A summary scanned from a snapshot, with light read and palette indices mapped now (sisby-folk/surveyor#148).
	 */
	ChunkSummary(@Nullable Integer airCount, int[] layerHeights, LayerSummary[] layers) {
		this.airCount = airCount;
		putLayers(layerHeights, layers);
	}

	private void putLayers(int[] layerHeights, LayerSummary[] layers) {
		for (int i = 0; i < layers.length; i++) {
			this.layers.put(layerHeights[i], layers[i]);
		}
	}

	public ChunkSummary(CompoundTag nbt) {
		this.airCount = nbt.contains(KEY_AIR_COUNT) ? nbt.getInt(KEY_AIR_COUNT) : null;
		CompoundTag layersCompound = nbt.getCompound(KEY_LAYERS);
		for (String key : layersCompound.getAllKeys()) {
			int layerY = Integer.parseInt(key);
			layers.put(layerY, LayerSummary.fromNbt(layersCompound.getCompound(key)));
		}
	}

	public ChunkSummary(FriendlyByteBuf buf) {
		layers.putAll(buf.readMap(FriendlyByteBuf::readVarInt, (b) -> {
			if (b.readByte() == 0) {
				return null;
			} else {
				return LayerSummary.fromBuf(buf);
			}
		}));
		this.airCount = -1;
	}

	public CompoundTag writeNbt(CompoundTag nbt) {
		if (this.airCount != null) nbt.putInt(KEY_AIR_COUNT, this.airCount);
		CompoundTag layersCompound = new CompoundTag();
		layers.forEach((layerY, layerSummary) -> {
			CompoundTag layerCompound = new CompoundTag();
			if (layerSummary != null) layerSummary.writeNbt(layerCompound);
			layersCompound.put(String.valueOf(layerY), layerCompound);
		});
		nbt.put(KEY_LAYERS, layersCompound);
		return nbt;
	}

	public void writeBuf(FriendlyByteBuf buf) {
		buf.writeMap(layers, FriendlyByteBuf::writeVarInt, (b, summary) -> {
			if (summary == null) {
				b.writeByte(0);
			} else {
				b.writeByte(1);
				summary.writeBuf(buf);
			}
		});
	}

	public void remap(Map<Integer, Integer> biomeRemap, Map<Integer, Integer> blockRemap) {
		Map<Integer, LayerSummary> newLayers = new HashMap<>();
		layers.forEach((y, layer) -> newLayers.put(y, layer == null ? null : new LayerSummary(layer.found, layer.depth, UInts.remap(layer.biome, biomeRemap::get, LayerSummary.BIOME_DEFAULT, layer.found.cardinality()), UInts.remap(layer.block, blockRemap::get, LayerSummary.BLOCK_DEFAULT, layer.found.cardinality()), layer.light, layer.water, layer.glint)));
		layers.clear();
		layers.putAll(newLayers);
	}

	public Integer getAirCount() {
		return airCount;
	}

	/**
	 * Gets an uncompressed layer of the topmost floor found for each X,Z column within the specified range.
	 *
	 * @param minY        the minimum (inclusive) height of floors to include in the layer.
	 * @param maxY        the maximum (inclusive) height of floors to include in the layer.
	 * @param worldHeight the maximum height of the world - or any layer height > maxY to be reused in LayerSummary#getHeight() later.
	 * @return A layer summary of top floors.
	 */
	public @Nullable LayerSummary.Raw toSingleLayer(Integer minY, Integer maxY, int worldHeight) {
		LayerSummary.Raw outRaw = new LayerSummary.Raw(new BitSet(256), new int[256], new int[256], new int[256], new int[256], new int[256], new int[256]);
		layers.descendingMap().forEach((y, layer) -> {
			if (layer != null) {
				layer.fillEmptyFloors(
					worldHeight - y,
					maxY == null ? Integer.MIN_VALUE : y - maxY,
					minY == null ? Integer.MAX_VALUE : y - minY,
					outRaw
				);
			}
		});
		return outRaw.exists().cardinality() == 0 ? null : outRaw;
	}

	public @Nullable LayerSummary.Raw toSingleLayerBelow(Integer minY, int[] depthsAbove, int worldHeight) {
		LayerSummary.Raw outRaw = new LayerSummary.Raw(new BitSet(256), new int[256], new int[256], new int[256], new int[256], new int[256], new int[256]);
		layers.descendingMap().forEach((y, layer) -> {
			if (layer != null) {
				layer.fillEmptyFloorsUnder(
					worldHeight - y,
					depthsAbove,
					minY == null ? Integer.MAX_VALUE : y - minY,
					outRaw
				);
			}
		});
		return outRaw.exists().cardinality() == 0 ? null : outRaw;
	}
}
