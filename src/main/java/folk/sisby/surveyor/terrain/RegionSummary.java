package folk.sisby.surveyor.terrain;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.packet.S2CUpdateRegionPacket;
import folk.sisby.surveyor.util.RegionPos;
import folk.sisby.surveyor.util.RegistryPalette;
import folk.sisby.surveyor.util.SafeNbtWriter;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.core.Registry;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class RegionSummary {
	public static final String KEY_BIOMES = "biomes";
	public static final String KEY_BLOCKS = "blocks";
	public static final String KEY_BIOME_WATER = "biomeWater";
	public static final String KEY_BIOME_FOLIAGE = "biomeFoliage";
	public static final String KEY_BIOME_GRASS = "biomeGrass";
	public static final String KEY_BLOCK_COLORS = "blockColors";
	public static final String KEY_CHUNKS = "chunks";

	protected final RegionPos regionPos;
	protected final File saveFile;
	protected final WorldSummary summary;
	protected @Nullable RegistryPalette<Biome> biomePalette;
	protected @Nullable RegistryPalette<Block> blockPalette;
	protected @Nullable ChunkSummary[][] chunks;
	protected @Nullable BitSet bitSet;

	// Fix: region state is guarded by this monitor; the singleplayer client reads it too.
	protected boolean dirty = false;
	private int pendingWrites = 0;
	private boolean unloadAfterWrite = false;

	private RegionSummary(WorldSummary summary, File saveFile, RegionPos regionPos, ChunkSummary[][] chunks, @Nullable BitSet bitSet, @Nullable RegistryPalette<Biome> biomePalette, @Nullable RegistryPalette<Block> blockPalette) {
		this.summary = summary;
		this.biomePalette = biomePalette;
		this.blockPalette = blockPalette;
		this.saveFile = saveFile;
		this.regionPos = regionPos;
		this.chunks = chunks;
		this.bitSet = bitSet;
		if (bitSet == null) readNbt(regionPos, true);
	}

	public static <T, O> List<O> mapIterable(Iterable<T> palette, Function<T, O> mapper) {
		List<O> list = new ArrayList<>();
		for (T value : palette) {
			list.add(mapper.apply(value));
		}
		return list;
	}

	public static RegionSummary fromEmpty(File folder, WorldSummary summary, RegionPos regionPos) {
		return new RegionSummary(summary, new File(folder, "c.%d.%d.dat".formatted(regionPos.x(), regionPos.z())), regionPos, new ChunkSummary[RegionPos.CHUNK_SIZE][RegionPos.CHUNK_SIZE], new BitSet(RegionPos.CHUNK_AREA), new RegistryPalette<>(summary.manager().registryOrThrow(Registries.BIOME)), new RegistryPalette<>(summary.manager().registryOrThrow(Registries.BLOCK)));
	}

	public static RegionSummary fromFile(File file, WorldSummary summary, RegionPos regionPos) {
		return new RegionSummary(summary, file, regionPos, null, null, null, null);
	}

	protected synchronized void readNbt(RegionPos pos, boolean bitsOnly) {
		Registry<Biome> biomeRegistry = summary.manager().registryOrThrow(Registries.BIOME);
		Registry<Block> blockRegistry = summary.manager().registryOrThrow(Registries.BLOCK);
		CompoundTag nbt = new CompoundTag();
		try {
			nbt = NbtIo.readCompressed(saveFile.toPath(), NbtAccounter.unlimitedHeap());
		} catch (IOException | ReportedNbtException e) {
			Surveyor.LOGGER.error("[Surveyor] Error reading region summary file {}.", saveFile.getName(), e);
		}
		CompoundTag chunksCompound = nbt.getCompound(KEY_CHUNKS);
		BitSet oldSet = bitSet;
		bitSet = new BitSet(RegionPos.CHUNK_AREA);
		if (bitsOnly) {
			for (String posKey : chunksCompound.getAllKeys()) {
				int x = RegionPos.regionRelative(Integer.parseInt(posKey.split(",")[0]));
				int z = RegionPos.regionRelative(Integer.parseInt(posKey.split(",")[1]));
				bitSet.set(RegionPos.chunkToBit(x, z));
			}
			if (oldSet != null && oldSet.cardinality() > bitSet.cardinality()) Surveyor.LOGGER.warn("[Surveyor] Reloading region {} caused {} chunks to be dropped.", regionPos, oldSet.cardinality() - bitSet.cardinality());
			return;
		}
		this.biomePalette = new RegistryPalette<>(summary.manager().registryOrThrow(Registries.BIOME));
		this.blockPalette = new RegistryPalette<>(summary.manager().registryOrThrow(Registries.BLOCK));
		this.chunks = new ChunkSummary[RegionPos.CHUNK_SIZE][RegionPos.CHUNK_SIZE];
		ListTag biomeList = nbt.getList(KEY_BIOMES, Tag.TAG_STRING);
		Map<Integer, Integer> biomeRemap = new Int2IntArrayMap(biomeList.size());
		for (int i = 0; i < biomeList.size(); i++) {
			ResourceLocation biomeId = ResourceLocation.tryParse(biomeList.get(i).getAsString());
			Biome biome = biomeRegistry.get(biomeId);
			Biome newBiome = biome == null ? biomeRegistry.get(Biomes.THE_VOID) : biome;
			int newIndex = biomePalette.findOrAdd(newBiome);
			if (biome == null || newIndex != i) {
				if (biome == null) Surveyor.LOGGER.warn("[Surveyor] Remapping biome palette in region {}: {} (#{}) is now {} (#{})", pos, biomeId, i, biomeRegistry.getKey(newBiome), newIndex);
				biomeRemap.put(i, newIndex);
				dirty();
			}
		}
		ListTag blockList = nbt.getList(KEY_BLOCKS, Tag.TAG_STRING);
		Map<Integer, Integer> blockRemap = new Int2IntArrayMap(blockList.size());
		for (int i = 0; i < blockList.size(); i++) {
			ResourceLocation blockId = ResourceLocation.tryParse(blockList.get(i).getAsString());
			Block block = blockRegistry.get(blockId);
			Block newBlock = block == null ? Blocks.AIR : block;
			int newIndex = blockPalette.findOrAdd(newBlock);
			if (block == null || newIndex != i) {
				if (block == null) Surveyor.LOGGER.warn("[Surveyor] Remapping block palette in region {}: {} (#{}) is now {} (#{})", pos, blockList.get(i).getAsString(), i, blockRegistry.getKey(newBlock), newIndex);
				blockRemap.put(i, newIndex);
				dirty();
			}
		}
		if (biomePalette.view().size() == 0 || blockPalette.view().size() == 0) { // abandon ship
			Surveyor.LOGGER.warn("[Surveyor] Palette was empty in region {}, skipping data load!", regionPos);
			return;
		}
		for (String posKey : chunksCompound.getAllKeys()) {
			int x = RegionPos.regionRelative(Integer.parseInt(posKey.split(",")[0]));
			int z = RegionPos.regionRelative(Integer.parseInt(posKey.split(",")[1]));
			ChunkSummary summary = new ChunkSummary(chunksCompound.getCompound(posKey));
			set(x, z, summary);
			if (!biomeRemap.isEmpty() || !blockRemap.isEmpty()) summary.remap(biomeRemap, blockRemap);
		}
		if (oldSet != null && oldSet.cardinality() > bitSet.cardinality()) Surveyor.LOGGER.warn("[Surveyor] Reloading region {} caused {} chunks to be dropped.", regionPos, oldSet.cardinality() - bitSet.cardinality());
	}

	public synchronized boolean contains(ChunkPos pos) {
		if (bitSet == null) readNbt(regionPos, true);
		return bitSet.get(RegionPos.chunkToBit(pos));
	}

	public synchronized ChunkSummary get(ChunkPos pos) {
		if (!contains(pos)) return null;
		return get(RegionPos.regionRelative(pos.x), RegionPos.regionRelative(pos.z));
	}

	protected synchronized @Nullable ChunkSummary get(int x, int z) {
		if (chunks == null) readNbt(regionPos, false);
		return chunks[x][z];
	}

	protected synchronized void set(int x, int z, ChunkSummary summary) {
		if (chunks == null || bitSet == null) readNbt(regionPos, false);
		chunks[x][z] = summary;
		bitSet.set(RegionPos.chunkToBit(x, z), summary != null);
	}

	public synchronized BitSet bitSet() {
		if (bitSet == null) readNbt(regionPos, true);
		return (BitSet) bitSet.clone();
	}

	public synchronized void putChunk(Level world, LevelChunk chunk) {
		if (Surveyor.CONFIG.terrain == SystemMode.FROZEN) return;
		if (world.getHeight() == 0) return;
		if (chunks == null) readNbt(regionPos, false);
		set(RegionPos.regionRelative(chunk.getPos().x), RegionPos.regionRelative(chunk.getPos().z), new ChunkSummary(world, chunk, DimensionSupport.getSummaryLayers(world), biomePalette, blockPalette, !(world instanceof ServerLevel)));
		dirty();
	}

	/**
	 * Publishes a chunk summarised on a worker; light is read now, on the level's thread (sisby-folk/surveyor#148).
	 */
	synchronized void putScanned(Level world, ChunkSummaryJob job) {
		if (Surveyor.CONFIG.terrain == SystemMode.FROZEN) return;
		if (chunks == null) readNbt(regionPos, false);
		ChunkPos pos = job.snapshot.pos;
		set(RegionPos.regionRelative(pos.x), RegionPos.regionRelative(pos.z), job.toSummary(biomePalette, blockPalette, p -> world.getBrightness(LightLayer.BLOCK, p)));
		dirty();
	}

	public boolean isUnloaded(Level world) {
		int chunkX = regionPos.chunkX(), chunkZ = regionPos.chunkZ();
		for (int x = 0; x < RegionPos.CHUNK_SIZE; x++) {
			for (int z = 0; z < RegionPos.CHUNK_SIZE; z++) {
				if (world.hasChunk(chunkX + x, chunkZ + z)) return false;
			}
		}
		return true;
	}

	// Fix: never skip a save, and only unload once the write finished with nothing changed since.
	public void save(boolean unload) {
		CompoundTag nbt;
		synchronized (this) {
			if (!isDirty()) {
				if (unload && pendingWrites == 0) chunks = null;
				else if (unload) unloadAfterWrite = true;
				return;
			}
			nbt = writeNbt();
			dirty = false;
			pendingWrites++;
			unloadAfterWrite = unload;
		}
		SafeNbtWriter.write(saveFile.toPath(), nbt).whenComplete((v, t) -> {
			synchronized (RegionSummary.this) {
				pendingWrites--;
				if (unloadAfterWrite && !dirty && pendingWrites == 0) chunks = null;
			}
		});
	}

	private CompoundTag writeNbt() {
		CompoundTag nbt = new CompoundTag();
		Registry<Biome> biomeRegistry = summary.manager().registryOrThrow(Registries.BIOME);
		Registry<Block> blockRegistry = summary.manager().registryOrThrow(Registries.BLOCK);
		nbt.put(KEY_BIOMES, new ListTag(mapIterable(biomePalette.view(), b -> StringTag.valueOf(biomeRegistry.getKey(b).toString())), Tag.TAG_STRING));
		nbt.put(KEY_BLOCKS, new ListTag(mapIterable(blockPalette.view(), b -> StringTag.valueOf(blockRegistry.getKey(b).toString())), Tag.TAG_STRING));
		nbt.putIntArray(KEY_BIOME_WATER, mapIterable(biomePalette.view(), Biome::getWaterColor));
		nbt.putIntArray(KEY_BIOME_FOLIAGE, mapIterable(biomePalette.view(), Biome::getFoliageColor));
		nbt.putIntArray(KEY_BIOME_GRASS, mapIterable(biomePalette.view(), b -> b.getGrassColor(0, 0)));
		nbt.putIntArray(KEY_BLOCK_COLORS, mapIterable(blockPalette.view(), b -> b.defaultMapColor().col));
		CompoundTag chunksCompound = new CompoundTag();
		regionPos.forXZ((x, z) -> {
			ChunkSummary chunk = get(x, z);
			if (chunk != null) {
				ChunkPos pos = regionPos.toChunk(x, z);
				chunksCompound.put("%s,%s".formatted(pos.x, pos.z), chunk.writeNbt(new CompoundTag()));
			}
		});
		nbt.put(KEY_CHUNKS, chunksCompound);
		return nbt;
	}

	public synchronized BitSet readUpdatePacket(S2CUpdateRegionPacket packet) {
		if (Surveyor.CONFIG.terrain == SystemMode.FROZEN) return new BitSet();
		if (chunks == null) readNbt(regionPos, false);
		if (packet.biomePalette().isEmpty() || packet.blockPalette().isEmpty()) return new BitSet(); // nonsense
		Registry<Biome> biomeRegistry = summary.manager().registryOrThrow(Registries.BIOME);
		Registry<Block> blockRegistry = summary.manager().registryOrThrow(Registries.BLOCK);
		Map<Integer, Integer> biomeRemap = new Int2IntArrayMap();
		for (int i = 0; i < packet.biomePalette().size(); i++) {
			biomeRemap.put(i, biomePalette.findOrAdd(biomeRegistry.byId(packet.biomePalette().get(i))));
		}

		Map<Integer, Integer> blockRemap = new Int2IntArrayMap();
		for (int i = 0; i < packet.blockPalette().size(); i++) {
			blockRemap.put(i, blockPalette.findOrAdd(blockRegistry.byId(packet.blockPalette().get(i))));
		}
		int[] indices = packet.set().stream().toArray();
		for (int i = 0; i < packet.chunks().size(); i++) {
			ChunkSummary summary = packet.chunks().get(i);
			summary.remap(biomeRemap, blockRemap);
			set(RegionPos.bitToX(indices[i]), RegionPos.bitToZ(indices[i]), summary);
		}
		dirty();
		return packet.set();
	}

	public synchronized S2CUpdateRegionPacket createUpdatePacket(ResourceKey<Level> dimension, boolean shared, RegionPos regionPos, BitSet set) {
		if (chunks == null) readNbt(regionPos, false);
		BitSet realSet = ((BitSet) set.clone());
		realSet.and(bitSet);
		return new S2CUpdateRegionPacket(dimension, shared, regionPos, mapIterable(biomePalette, i -> i), mapIterable(blockPalette, i -> i), realSet, realSet.stream().mapToObj(i -> get(RegionPos.bitToX(i), RegionPos.bitToZ(i))).toList());
	}

	public synchronized RegistryPalette<Biome>.ValueView getBiomePalette() {
		if (chunks == null) readNbt(regionPos, false);
		return biomePalette.view();
	}

	public synchronized RegistryPalette<Block>.ValueView getBlockPalette() {
		if (chunks == null) readNbt(regionPos, false);
		return blockPalette.view();
	}

	public synchronized boolean isLoaded() {
		return chunks != null;
	}

	public synchronized boolean isDirty() {
		return dirty && Surveyor.CONFIG.terrain != SystemMode.FROZEN;
	}

	private synchronized void dirty() {
		dirty = true;
	}
}
