package folk.sisby.surveyor.terrain;

import com.mojang.serialization.Lifecycle;
import folk.sisby.surveyor.util.RegistryPalette;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * sisby-folk/surveyor#148: the off-thread summary must match the summary made in place, and both must match the scan
 * as it was before the split.
 */
class ChunkSummaryJobTest {
	private static final int MIN_SECTION = -4;
	private static final int SECTIONS = 24;
	private static final int MIN_Y = MIN_SECTION * 16;
	private static final int MAX_Y = MIN_Y + SECTIONS * 16;
	private static final ChunkScan.BlockLight LIGHT = pos -> Math.floorMod(pos.getX() * 31 + pos.getY() * 17 + pos.getZ() * 7, 16);
	private static final Path SRC = Path.of(System.getProperty("surveyor.projectDir", "."), "src/main/java/folk/sisby/surveyor");

	private static Biome biome(float temperature) {
		return new Biome.BiomeBuilder().hasPrecipitation(false).temperature(temperature).downfall(0.5F)
			.specialEffects(new BiomeSpecialEffects.Builder().fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build())
			.mobSpawnSettings(MobSpawnSettings.EMPTY).generationSettings(BiomeGenerationSettings.EMPTY).build();
	}

	private static MappedRegistry<Biome> biomes() {
		MappedRegistry<Biome> registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
		String[] names = {"plains", "the_void", "river", "desert"};
		for (int i = 0; i < names.length; i++) {
			registry.register(ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace(names[i])), biome(0.1F * i), RegistrationInfo.BUILT_IN);
		}
		registry.freeze();
		return registry;
	}

	@SuppressWarnings("unchecked")
	private static Holder<Biome> holder(MappedRegistry<Biome> registry, int id) {
		return (Holder<Biome>) (Holder<?>) registry.getHolder(id).orElseThrow();
	}

	private static LevelChunkSection[] terrain(MappedRegistry<Biome> biomes, long seed) {
		LevelChunkSection[] sections = new LevelChunkSection[SECTIONS];
		for (int i = 0; i < SECTIONS; i++) {
			PalettedContainer<Holder<Biome>> biomeContainer = new PalettedContainer<>(biomes.asHolderIdMap(), holder(biomes, 0), PalettedContainer.Strategy.SECTION_BIOMES);
			for (int qx = 0; qx < 4; qx++) for (int qy = 0; qy < 4; qy++) for (int qz = 0; qz < 4; qz++) {
				biomeContainer.getAndSet(qx, qy, qz, holder(biomes, (qx + qz + qy + i) % 4 == 1 ? 3 : (qx + qz + i) % 3 == 0 ? 2 : 0));
			}
			sections[i] = new LevelChunkSection(new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES), biomeContainer);
		}
		Random random = new Random(seed);
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int height = 52 + random.nextInt(18);
				for (int y = MIN_Y; y <= height; y++) set(sections, x, y, z, y < height - 3 ? Blocks.STONE.defaultBlockState() : y < height ? Blocks.DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
				// A cave with a lava floor and a glass ceiling patch.
				if (x >= 9 && z >= 9) {
					for (int y = 20; y <= 26; y++) set(sections, x, y, z, Blocks.AIR.defaultBlockState());
					if ((x + z) % 2 == 0) set(sections, x, 19, z, Blocks.LAVA.defaultBlockState());
					if (x == 12) set(sections, x, 26, z, Blocks.GLASS.defaultBlockState());
				}
				if (height < 62) {
					for (int y = height + 1; y <= 62; y++) set(sections, x, y, z, Blocks.WATER.defaultBlockState());
					if (random.nextInt(3) == 0) set(sections, x, height + 1, z, Blocks.SEAGRASS.defaultBlockState());
					if (random.nextInt(5) == 0) set(sections, x, height, z, Blocks.GLASS.defaultBlockState());
				} else {
					switch (random.nextInt(9)) {
						case 0 -> set(sections, x, height + 1, z, Blocks.WHITE_CARPET.defaultBlockState());
						case 1 -> set(sections, x, height + 1, z, Blocks.GLASS.defaultBlockState());
						case 2 -> set(sections, x, height + 1, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1));
						case 3 -> set(sections, x, height + 1, z, Blocks.TORCH.defaultBlockState());
						case 4 -> set(sections, x, height, z, Blocks.LAVA.defaultBlockState());
						case 5 -> set(sections, x, height + 1, z, Blocks.BARRIER.defaultBlockState());
						default -> {
						}
					}
				}
				// A floating island with carpets, in its own layer, and a carpet on a layer boundary.
				if (x >= 3 && x <= 8 && z >= 3 && z <= 8) {
					for (int y = 118; y <= 121; y++) set(sections, x, y, z, Blocks.STONE.defaultBlockState());
					if ((x * z) % 3 == 0) set(sections, x, 122, z, Blocks.RED_CARPET.defaultBlockState());
				}
			}
		}
		return sections;
	}

	private static void set(LevelChunkSection[] sections, int x, int y, int z, BlockState state) {
		sections[SectionPos.blockToSectionCoord(y) - MIN_SECTION].setBlockState(x, y & 15, z, state, false);
	}

	private static SectionSummary[] summaries(LevelChunkSection[] sections) {
		SectionSummary[] summaries = new SectionSummary[sections.length];
		for (int i = 0; i < sections.length; i++) summaries[i] = SectionSummary.ofSection(sections[i]);
		return summaries;
	}

	private static BlockGetter getter(LevelChunkSection[] sections) {
		return new BlockGetter() {
			@Override
			public BlockEntity getBlockEntity(BlockPos pos) {
				return null;
			}

			@Override
			public BlockState getBlockState(BlockPos pos) {
				int i = SectionPos.blockToSectionCoord(pos.getY()) - MIN_SECTION;
				return i < 0 || i >= sections.length ? Blocks.AIR.defaultBlockState() : sections[i].getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
			}

			@Override
			public FluidState getFluidState(BlockPos pos) {
				return getBlockState(pos).getFluidState();
			}

			@Override
			public int getHeight() {
				return MAX_Y - MIN_Y;
			}

			@Override
			public int getMinBuildHeight() {
				return MIN_Y;
			}
		};
	}

	private static int[] layers() {
		DimensionType type = new DimensionType(OptionalLong.empty(), true, false, false, true, 1.0, true, false, MIN_Y, MAX_Y - MIN_Y, MAX_Y - MIN_Y, BlockTags.INFINIBURN_OVERWORLD, ResourceLocation.withDefaultNamespace("overworld"), 0.0F, new DimensionType.MonsterSettings(false, true, ConstantInt.of(0), 0));
		return DimensionSupport.getSummaryLayers(type, false, 63);
	}

	private static RegistryPalette<Biome> biomePalette(MappedRegistry<Biome> biomes) {
		RegistryPalette<Biome> palette = new RegistryPalette<>(biomes);
		palette.findOrAdd(biomes.byId(2)); // a region that already knows some values
		return palette;
	}

	private static RegistryPalette<Block> blockPalette() {
		RegistryPalette<Block> palette = new RegistryPalette<>(BuiltInRegistries.BLOCK);
		palette.findOrAdd(Blocks.SAND);
		palette.findOrAdd(Blocks.GRASS_BLOCK);
		return palette;
	}

	private static IntArrayList ids(RegistryPalette<?> palette) {
		IntArrayList ids = new IntArrayList();
		palette.forEach(ids::add);
		return ids;
	}

	@Test
	@DisplayName("Off-thread, in-place and pre-split summaries of a chunk are identical, palettes included")
	void sameSummary() throws Exception {
		MappedRegistry<Biome> biomes = biomes();
		int[] layerHeights = layers();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			for (long seed = 0; seed < 6; seed++) {
				ChunkPos pos = new ChunkPos((int) seed * 7 - 20, 13 - (int) seed * 5);
				LevelChunkSection[] sections = terrain(biomes, seed);

				RegistryPalette<Biome> oracleBiomes = biomePalette(biomes);
				RegistryPalette<Block> oracleBlocks = blockPalette();
				CompoundTag expected = oracle(summaries(sections), MIN_SECTION, pos, MIN_Y, MAX_Y, layerHeights, getter(sections), LIGHT, oracleBiomes, oracleBlocks);

				RegistryPalette<Biome> syncBiomes = biomePalette(biomes);
				RegistryPalette<Block> syncBlocks = blockPalette();
				ChunkScan.Floors floors = ChunkScan.scan(summaries(sections), MIN_SECTION, pos.getMinBlockX(), pos.getMinBlockZ(), MIN_Y, MAX_Y, layerHeights, getter(sections), syncBiomes::findOrAdd, syncBlocks::findOrAdd);
				ChunkSummary sync = new ChunkSummary(null, layerHeights, ChunkScan.layers(floors, pos.getMinBlockX(), pos.getMinBlockZ(), layerHeights, LIGHT, null, null));

				ChunkSnapshot snapshot = ChunkSnapshot.of(sections, pos, MIN_SECTION, MIN_Y, MAX_Y, layerHeights, null);
				assertNotNull(snapshot, "vanilla blocks never need the level");
				// The live chunk changes after the snapshot: the summary must not see it.
				for (int x = 0; x < 16; x++) set(sections, x, 200, x, Blocks.GOLD_BLOCK.defaultBlockState());
				set(sections, 0, 60, 0, Blocks.AIR.defaultBlockState());
				ConcurrentLinkedQueue<ChunkSummaryJob> done = new ConcurrentLinkedQueue<>();
				ChunkSummaryJob job = new ChunkSummaryJob(snapshot, done::add);
				CompletableFuture.runAsync(job, pool);
				job.finish();
				assertTrue(job.error() == null, () -> "worker failed: " + job.error());
				assertEquals(1, done.size(), "a job reports once, whoever ran it");
				RegistryPalette<Biome> asyncBiomes = biomePalette(biomes);
				RegistryPalette<Block> asyncBlocks = blockPalette();
				ChunkSummary async = job.toSummary(asyncBiomes, asyncBlocks, LIGHT);

				assertEquals(expected, sync.writeNbt(new CompoundTag()), "in place, seed " + seed);
				assertEquals(expected, async.writeNbt(new CompoundTag()), "off-thread, seed " + seed);
				assertEquals(ids(oracleBiomes), ids(syncBiomes));
				assertEquals(ids(oracleBiomes), ids(asyncBiomes), "biome palette order");
				assertEquals(ids(oracleBlocks), ids(syncBlocks));
				assertEquals(ids(oracleBlocks), ids(asyncBlocks), "block palette order");
				assertTrue(ids(oracleBlocks).size() > 6, "the test chunk should exercise several blocks");
			}
		} finally {
			pool.shutdown();
		}
	}

	static class LevelColoredBlock extends Block {
		LevelColoredBlock(Properties properties) {
			super(properties);
		}

		@Override
		public MapColor getMapColor(BlockState state, BlockGetter level, BlockPos pos, MapColor defaultColor) {
			return level.getBlockState(pos.below()).getMapColor(level, pos.below());
		}
	}

	@Test
	@DisplayName("Blocks whose map colour reads the level are summarised in place; no registered block does")
	void levelColoredBlocks() {
		assertTrue(ChunkSnapshot.readsLevel(LevelColoredBlock.class));
		assertFalse(ChunkSnapshot.readsLevel(Block.class));
		for (Block block : BuiltInRegistries.BLOCK) {
			assertFalse(ChunkSnapshot.readsLevel(block.getClass()), () -> BuiltInRegistries.BLOCK.getKey(block) + " reads the level for its map colour");
		}
	}

	@Test
	@DisplayName("Pending summaries are published before a chunk is sent, before saving, and count as unsaved")
	void pendingIsPublished() throws IOException {
		String player = Files.readString(SRC.resolve("PlayerSummary.java"));
		String addChunk = player.substring(player.indexOf("public void addChunk(ResourceKey<Level> dimension, ChunkPos pos, boolean updateClient) {"));
		assertTrue(addChunk.indexOf("terrain.finishPending(") < addChunk.indexOf("S2CUpdateRegionPacket.of("), "finish the chunk before sending it");
		String terrain = Files.readString(SRC.resolve("terrain/WorldTerrain.java"));
		String save = terrain.substring(terrain.indexOf("public int save("));
		assertTrue(save.indexOf("finishPending(") < save.indexOf("summary.save("), "publish pending chunks before saving");
		assertTrue(terrain.contains("return !pending.isEmpty() ||"), "pending chunks make the terrain dirty");
		String tick = terrain.substring(terrain.indexOf("public void serverTick("));
		assertTrue(tick.indexOf("publishFinished(") < tick.indexOf("queuedUpdates.isEmpty()"), "publish every tick, not only when updates are queued");
		String region = Files.readString(SRC.resolve("terrain/RegionSummary.java"));
		assertTrue(region.contains("synchronized void putScanned("), "publish under the region's monitor");
	}

	// The scan as it was before sisby-folk/surveyor#148, with the level replaced by its parts.
	static CompoundTag oracle(SectionSummary[] sections, int minSection, ChunkPos chunkPos, int minBuildHeight, int maxBuildHeight, int[] layerHeights, BlockGetter world, ChunkScan.BlockLight brightness, RegistryPalette<Biome> biomePalette, RegistryPalette<Block> blockPalette) {
		LayerSummary.FloorSummary[][] layerFloors = new LayerSummary.FloorSummary[layerHeights.length - 1][256];
		int chunkX = chunkPos.getMinBlockX();
		int chunkZ = chunkPos.getMinBlockZ();
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
						int sectionIndex = (SectionPos.blockToSectionCoord(y) - minSection);
						SectionSummary section = sections[sectionIndex];
						if (section == null) {
							int sectionBottom = SectionPos.sectionToBlockCoord((sectionIndex + minSection));
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
							if (walkspaceHeight >= ChunkSummary.MINIMUM_AIR_DEPTH && state.getMapColor(world, pos) != MapColor.NONE) {
								carpetY = y;
								carpetBlock = state.getBlock();
							}
						} else if (fluid.isSame(Fluids.WATER) || fluid.isSame(Fluids.FLOWING_WATER)) { // keep walkspace when traversing water
							waterDepth++;
						} else { // Blocks Movement or Has Non-Water Fluid.
							if (foundFloor == null) {
								if (carpetY == y + 1) {
									int carpetLight = brightness.get(pos.setY(carpetY));
									int waterLight = waterDepth == 0 ? 0 : brightness.get(pos.setY(y + 1 + waterDepth));
									pos.setY(y);
									foundFloor = new LayerSummary.FloorSummary(carpetY, biomePalette.findOrAdd(section.getBiomeEntry(x, carpetY, z, minBuildHeight, maxBuildHeight).value()), blockPalette.findOrAdd(carpetBlock), carpetLight, waterDepth, waterLight);
									if (carpetY > layerHeights[layerIndex]) { // Actually a floor for the layer above
										if (layerFloors[layerIndex - 1][x * 16 + z] == null) layerFloors[layerIndex - 1][x * 16 + z] = foundFloor;
										foundFloor = null;
									}
									// Carpeted glass needs to reset walkspaces
									walkspaceHeight = 0;
									waterDepth = 0;
								} else if (walkspaceHeight >= ChunkSummary.MINIMUM_AIR_DEPTH && (mapColor = state.getMapColor(world, pos)) != MapColor.NONE) {
									int biome = biomePalette.findOrAdd(section.getBiomeEntry(x, y, z, minBuildHeight, maxBuildHeight).value());
									int block = blockPalette.findOrAdd(state.getBlock());
									int light = brightness.get(pos.setY(y + 1));
									int waterLight = waterDepth == 0 ? 0 : brightness.get(pos.setY(y + 1 + waterDepth));
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
		CompoundTag layersCompound = new CompoundTag();
		for (int i = 0; i < layerFloors.length; i++) {
			LayerSummary layer = LayerSummary.fromSummaries(layerFloors[i], layerHeights[i]);
			CompoundTag layerCompound = new CompoundTag();
			if (layer != null) layer.writeNbt(layerCompound);
			layersCompound.put(String.valueOf(layerHeights[i]), layerCompound);
		}
		CompoundTag nbt = new CompoundTag();
		nbt.put(ChunkSummary.KEY_LAYERS, layersCompound);
		return nbt;
	}
}
