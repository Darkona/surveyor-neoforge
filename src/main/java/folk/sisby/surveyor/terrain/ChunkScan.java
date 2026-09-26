package folk.sisby.surveyor.terrain;

import java.util.BitSet;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;

/**
 * The chunk scan behind {@link ChunkSummary}, split from what it reads from the level (sisby-folk/surveyor#148):
 * block states and biomes come from {@link SectionSummary}s, map colours from any {@link BlockGetter}, and block light
 * isn't read at all: each floor keeps the height where its light must be read, so light can be read later on the
 * level's thread. Palette indices come from the given functions, called in the same order as the original scan.
 */
final class ChunkScan {
	static final int COLUMNS = 256;

	private ChunkScan() {
	}

	/**
	 * Floors found per layer, as parallel arrays indexed by {@code layer * 256 + x * 16 + z}.
	 */
	static final class Floors {
		final int layers;
		final BitSet found;
		final int[] y;
		final int[] biome;
		final int[] block;
		final int[] lightY;
		final int[] fluidDepth;

		Floors(int layers) {
			this.layers = layers;
			int size = layers * COLUMNS;
			this.found = new BitSet(size);
			this.y = new int[size];
			this.biome = new int[size];
			this.block = new int[size];
			this.lightY = new int[size];
			this.fluidDepth = new int[size];
		}

		void set(int index, int y, int biome, int block, int lightY, int fluidDepth) {
			found.set(index);
			this.y[index] = y;
			this.biome[index] = biome;
			this.block[index] = block;
			this.lightY[index] = lightY;
			this.fluidDepth[index] = fluidDepth;
		}

	}

	@FunctionalInterface
	interface BlockLight {
		int get(BlockPos pos);
	}

	static Floors scan(SectionSummary[] sections, int minSection, int chunkX, int chunkZ, int minBuildHeight, int maxBuildHeight, int[] layerHeights, BlockGetter level, ToIntFunction<Biome> biomes, ToIntFunction<Block> blocks) {
		int layerCount = layerHeights.length - 1;
		Floors floors = new Floors(layerCount);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int column = x * 16 + z;
				int blockX = chunkX + x;
				int blockZ = chunkZ + z;
				int walkspaceHeight = 2; // Start at 2 to allow finding floors at the height limit.
				int waterDepth = 0;
				Block carpetBlock = null;
				int carpetY = Integer.MAX_VALUE;
				for (int layerIndex = 0; layerIndex < layerCount; layerIndex++) {
					// The floor found so far in this layer.
					boolean found = false;
					int foundY = 0, foundBiome = 0, foundBlock = 0, foundLightY = 0, foundDepth = 0;
					for (int y = layerHeights[layerIndex]; y > layerHeights[layerIndex + 1]; y--) {
						int sectionIndex = SectionPos.blockToSectionCoord(y) - minSection;
						SectionSummary section = sections[sectionIndex];
						if (section == null) {
							int sectionBottom = SectionPos.sectionToBlockCoord(sectionIndex + minSection);
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
							if (walkspaceHeight >= ChunkSummary.MINIMUM_AIR_DEPTH && state.getMapColor(level, pos) != MapColor.NONE) {
								carpetY = y;
								carpetBlock = state.getBlock();
							}
						} else if (fluid.isSame(Fluids.WATER) || fluid.isSame(Fluids.FLOWING_WATER)) { // keep walkspace when traversing water
							waterDepth++;
						} else { // Blocks Movement or Has Non-Water Fluid.
							if (!found) {
								if (carpetY == y + 1) {
									// Light is read at the carpet itself, and above the water over it.
									found = true;
									foundY = carpetY;
									foundBiome = biomes.applyAsInt(section.getBiomeEntry(x, carpetY, z, minBuildHeight, maxBuildHeight).value());
									foundBlock = blocks.applyAsInt(carpetBlock);
									foundLightY = carpetY;
									foundDepth = waterDepth;
									if (carpetY > layerHeights[layerIndex]) { // Actually a floor for the layer above
										int above = (layerIndex - 1) * COLUMNS + column;
										if (!floors.found.get(above)) floors.set(above, foundY, foundBiome, foundBlock, foundLightY, foundDepth);
										found = false;
									}
									// Carpeted glass needs to reset walkspaces
									walkspaceHeight = 0;
									waterDepth = 0;
								} else if (walkspaceHeight >= ChunkSummary.MINIMUM_AIR_DEPTH && (mapColor = state.getMapColor(level, pos)) != MapColor.NONE) {
									found = true;
									foundY = y;
									foundBiome = biomes.applyAsInt(section.getBiomeEntry(x, y, z, minBuildHeight, maxBuildHeight).value());
									foundBlock = blocks.applyAsInt(state.getBlock());
									foundLightY = y + 1;
									foundDepth = waterDepth;
								}
							}
							if (mapColor == null) mapColor = state.getMapColor(level, pos);
							if (mapColor != MapColor.NONE) { // Don't reset walkspace for glass/barriers/etc.
								walkspaceHeight = 0;
								waterDepth = 0; // Prevents a glass block on the ocean floor from hiding all the water
							}
						}
					}
					int index = layerIndex * COLUMNS + column;
					if (found) {
						floors.set(index, foundY, foundBiome, foundBlock, foundLightY, foundDepth);
					} else {
						floors.found.clear(index);
					}
				}
			}
		}
		return floors;
	}

	/**
	 * Builds the layers from the floors, reading light where each floor asked for it and mapping palette indices.
	 */
	static LayerSummary[] layers(Floors floors, int chunkX, int chunkZ, int[] layerHeights, BlockLight light, int[] biomeRemap, int[] blockRemap) {
		LayerSummary[] layers = new LayerSummary[floors.layers];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		LayerSummary.FloorSummary[] layer = new LayerSummary.FloorSummary[COLUMNS];
		for (int layerIndex = 0; layerIndex < floors.layers; layerIndex++) {
			for (int column = 0; column < COLUMNS; column++) {
				int i = layerIndex * COLUMNS + column;
				if (!floors.found.get(i)) {
					layer[column] = null;
					continue;
				}
				pos.set(chunkX + (column >> 4), floors.lightY[i], chunkZ + (column & 15));
				int lightLevel = light.get(pos);
				int waterLight = floors.fluidDepth[i] == 0 ? 0 : light.get(pos.setY(floors.lightY[i] + floors.fluidDepth[i]));
				int biome = biomeRemap == null ? floors.biome[i] : biomeRemap[floors.biome[i]];
				int block = blockRemap == null ? floors.block[i] : blockRemap[floors.block[i]];
				layer[column] = new LayerSummary.FloorSummary(floors.y[i], biome, block, lightLevel, floors.fluidDepth[i], waterLight);
			}
			layers[layerIndex] = LayerSummary.fromSummaries(layer, layerHeights[layerIndex]);
		}
		return layers;
	}
}
