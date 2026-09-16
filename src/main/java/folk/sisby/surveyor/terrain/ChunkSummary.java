package folk.sisby.surveyor.terrain;

import folk.sisby.surveyor.util.ChunkUtil;
import folk.sisby.surveyor.util.RegistryPalette;
import folk.sisby.surveyor.util.uints.UInts;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;

public class ChunkSummary {
	public static final int MINIMUM_AIR_DEPTH = 2;
	public static final String KEY_AIR_COUNT = "air";
	public static final String KEY_LAYERS = "layers";

	protected final Integer airCount;
	protected final TreeMap<Integer, @Nullable LayerSummary> layers = new TreeMap<>();

	public ChunkSummary(Level world, LevelChunk chunk, int[] layerHeights, RegistryPalette<Biome> biomePalette, RegistryPalette<Block> blockPalette, boolean countAir) {
		this.airCount = countAir ? ChunkUtil.airCount(chunk) : null;
		LayerSummary.FloorSummary[][] layerFloors = new LayerSummary.FloorSummary[layerHeights.length - 1][256];
		LevelChunkSection[] rawSections = chunk.getSections();
		SectionSummary[] sections = new SectionSummary[rawSections.length];
		for (int i = 0; i < rawSections.length; i++) {
			sections[i] = SectionSummary.ofSection(rawSections[i]);
		}
		int chunkX = chunk.getPos().getMinBlockX();
		int chunkZ = chunk.getPos().getMinBlockZ();
		int minBuildHeight = world.getMinBuildHeight();
		int maxBuildHeight = world.getMaxBuildHeight();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int blockX = chunkX + x;
				int blockZ = chunkZ + z;
				int walkspaceHeight = 2; // Start at 2 to allow finding floors at the height limit.
				int waterDepth = 0;
				Block carpetBlock = null;
				int carpetY = Integer.MAX_VALUE;
				for (int layerIndex = 0; layerIndex < layerHeights.length - 1; layerIndex++) {
					LayerSummary.FloorSummary foundFloor = null;
					for (int y = layerHeights[layerIndex]; y > layerHeights[layerIndex + 1]; y--) {
						int sectionIndex = chunk.getSectionIndex(y);
						SectionSummary section = sections[sectionIndex];
						if (section == null) {
							int sectionBottom = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex));
							walkspaceHeight += (y - sectionBottom + 1);
							waterDepth = 0;
							y = sectionBottom;
							continue;
						}
						pos.set(blockX, y, blockZ);
						BlockState state = section.getBlockState(x, y, z);
						Fluid fluid = state.getFluidState().getType();
						MapColor mapColor = null;

						if (!state.blocksMotion() && fluid.isSame(Fluids.EMPTY)) {
							walkspaceHeight++;
							waterDepth = 0;
							if (walkspaceHeight >= MINIMUM_AIR_DEPTH && state.getMapColor(world, pos) != MapColor.NONE) {
								carpetY = y;
								carpetBlock = state.getBlock();
							}
						} else if (fluid.isSame(Fluids.WATER) || fluid.isSame(Fluids.FLOWING_WATER)) { // keep walkspace when traversing water
							waterDepth++;
						} else { // Blocks Movement or Has Non-Water Fluid.
							if (foundFloor == null) {
								if (carpetY == y + 1) {
									int carpetLight = world.getBrightness(LightLayer.BLOCK, pos.setY(carpetY));
									int waterLight = waterDepth == 0 ? 0 : world.getBrightness(LightLayer.BLOCK, pos.setY(y + 1 + waterDepth));
									pos.setY(y);
									foundFloor = new LayerSummary.FloorSummary(carpetY, biomePalette.findOrAdd(section.getBiomeEntry(x, carpetY, z, minBuildHeight, maxBuildHeight).value()), blockPalette.findOrAdd(carpetBlock), carpetLight, waterDepth, waterLight);
									if (carpetY > layerHeights[layerIndex]) { // Actually a floor for the layer above
										if (layerFloors[layerIndex - 1][x * 16 + z] == null) layerFloors[layerIndex - 1][x * 16 + z] = foundFloor;
										foundFloor = null;
									}
									// Carpeted glass needs to reset walkspaces
									walkspaceHeight = 0;
									waterDepth = 0;
								} else if (walkspaceHeight >= MINIMUM_AIR_DEPTH && (mapColor = state.getMapColor(world, pos)) != MapColor.NONE) {
									int biome = biomePalette.findOrAdd(section.getBiomeEntry(x, y, z, minBuildHeight, maxBuildHeight).value());
									int block = blockPalette.findOrAdd(state.getBlock());
									int light = world.getBrightness(LightLayer.BLOCK, pos.setY(y + 1));
									int waterLight = waterDepth == 0 ? 0 : world.getBrightness(LightLayer.BLOCK, pos.setY(y + 1 + waterDepth));
									pos.setY(y);
									foundFloor = new LayerSummary.FloorSummary(y, biome, block, light, waterDepth, waterLight);
								}
							}
							if (mapColor == null) mapColor = state.getMapColor(world, pos);
							if (mapColor != MapColor.NONE) { // Don't reset walkspace for glass/barriers/etc.
								walkspaceHeight = 0;
								waterDepth = 0; // Prevents a glass block on the ocean floor from hiding all the water
							}
						}
					}
					layerFloors[layerIndex][x * 16 + z] = foundFloor;
				}
			}
		}
		for (int i = 0; i < layerFloors.length; i++) {
			this.layers.put(layerHeights[i], LayerSummary.fromSummaries(layerFloors[i], layerHeights[i]));
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
