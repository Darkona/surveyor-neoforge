package folk.sisby.surveyor.structure;

import folk.sisby.surveyor.util.RegionPos;
import folk.sisby.surveyor.util.SafeNbtWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * sisby-folk/surveyor#115: structure regions are indexed at load, read on demand and dropped when idle, and the file
 * stays what the original mod reads.
 */
class RegionStructureSummaryTest {
	private static final RegionPos REGION = new RegionPos(-1, 2);
	private static final ResourceKey<Structure> VILLAGE = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("village_plains"));
	private static final ResourceKey<Structure> SHIPWRECK = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("shipwreck"));
	private static final ResourceKey<Structure> UNKNOWN = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse("gone:structure"));

	@TempDir
	Path dir;

	private static StructureStartSummary village(int seed) {
		CompoundTag nbt = new CompoundTag();
		nbt.putInt("seed", seed);
		return new StructureStartSummary(List.of(
			new JigsawPieceSummary(new BlockPos(seed, 64, -seed), 1, Rotation.CLOCKWISE_90, StructurePoolElementType.SINGLE, ResourceLocation.withDefaultNamespace("village/plains/streets/straight_01"), 2, new BoundingBox(seed, 60, -seed, seed + 16, 70, -seed + 4), List.of(
				new JigsawJunction(seed, 63, -seed, 0, StructureTemplatePool.Projection.TERRAIN_MATCHING),
				new JigsawJunction(seed + 16, 63, -seed, 1, StructureTemplatePool.Projection.RIGID)
			)),
			new StructurePieceSummary(StructurePieceType.SHIPWRECK_PIECE, 0, new BoundingBox(0, 0, 0, 5, 5, 5), nbt)
		));
	}

	private static CompoundTag read(File file) throws IOException {
		return NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
	}

	private static ChunkPos chunk(int x, int z) {
		return REGION.toChunk(x, z);
	}

	@Test
	@DisplayName("Indexed regions answer keys without reading pieces, and read them when asked for")
	void lazyLoad() throws IOException {
		File file = dir.resolve("s.-1.2.dat").toFile();
		RegionStructureSummary written = new RegionStructureSummary(file, REGION);
		written.put(VILLAGE, chunk(1, 2), village(1));
		written.put(VILLAGE, chunk(3, 4), village(3));
		written.put(SHIPWRECK, chunk(5, 6), village(5));
		assertTrue(written.save(file, false));
		SafeNbtWriter.flush();
		CompoundTag onDisk = read(file);
		assertEquals(RegionStructureSummary.readNbt(onDisk).writeNbt(new CompoundTag()), onDisk, "the original's full read round-trips the file");

		RegionStructureSummary region = RegionStructureSummary.index(file, REGION, onDisk);
		assertFalse(region.isLoaded());
		assertEquals(3, region.keySet().size());
		assertTrue(region.contains(VILLAGE, chunk(3, 4)));
		assertFalse(region.contains(SHIPWRECK, chunk(3, 4)));
		assertFalse(region.isLoaded(), "keys and contains don't read pieces");
		assertNull(region.get(SHIPWRECK, chunk(1, 2)));
		assertFalse(region.isLoaded(), "a missing start doesn't read the file");

		StructureStartSummary start = region.get(VILLAGE, chunk(1, 2));
		assertNotNull(start);
		assertTrue(region.isLoaded());
		assertEquals(village(1).getChildren().get(0).toNbt(), start.getChildren().get(0).toNbt(), "pieces and junctions survive");
		assertEquals(2, ((JigsawPieceSummary) start.getChildren().get(0)).getJunctions().size());

		region.unload();
		assertFalse(region.isLoaded());
		assertNotNull(region.get(SHIPWRECK, chunk(5, 6)), "an unloaded region reads its pieces again");
	}

	@Test
	@DisplayName("Adding to an unloaded region keeps its other starts; dirty regions unload once written")
	void putKeepsOthers() throws IOException {
		File file = dir.resolve("s.-1.2.dat").toFile();
		RegionStructureSummary written = new RegionStructureSummary(file, REGION);
		written.put(VILLAGE, chunk(1, 2), village(1));
		written.put(SHIPWRECK, chunk(5, 6), village(5));
		written.save(file, false);
		SafeNbtWriter.flush();

		RegionStructureSummary region = RegionStructureSummary.index(file, REGION, read(file));
		region.put(VILLAGE, chunk(7, 8), village(7));
		region.unload();
		assertTrue(region.isLoaded(), "unsaved changes stay in memory");
		assertTrue(region.save(file, true));
		SafeNbtWriter.flush();
		assertFalse(region.isLoaded(), "unloaded after the write");
		RegionStructureSummary reread = RegionStructureSummary.readNbt(read(file));
		assertEquals(3, reread.keySet().size(), "no start is lost by adding while unloaded");
		assertNotNull(region.get(VILLAGE, chunk(7, 8)));
	}

	@Test
	@DisplayName("Structures without a saved type are dropped from the index, and from the file on the next save")
	void retainKeys() throws IOException {
		File file = dir.resolve("s.-1.2.dat").toFile();
		RegionStructureSummary written = new RegionStructureSummary(file, REGION);
		written.put(VILLAGE, chunk(1, 2), village(1));
		written.put(UNKNOWN, chunk(2, 2), village(2));
		written.save(file, false);
		SafeNbtWriter.flush();

		RegionStructureSummary region = RegionStructureSummary.index(file, REGION, read(file));
		region.retainKeys(key -> !key.equals(UNKNOWN));
		assertFalse(region.contains(UNKNOWN, chunk(2, 2)));
		region.put(VILLAGE, chunk(3, 3), village(3));
		region.save(file, false);
		SafeNbtWriter.flush();
		CompoundTag structures = read(file).getCompound(RegionStructureSummary.KEY_STRUCTURES);
		assertFalse(structures.contains(UNKNOWN.location().toString()));
		assertEquals(2, structures.getCompound(VILLAGE.location().toString()).getCompound(RegionStructureSummary.KEY_STARTS).size());
	}

	@Test
	@DisplayName("A region that can't be read is never overwritten")
	void unreadableNotOverwritten() throws IOException {
		File file = dir.resolve("s.-1.2.dat").toFile();
		RegionStructureSummary written = new RegionStructureSummary(file, REGION);
		written.put(VILLAGE, chunk(1, 2), village(1));
		written.save(file, false);
		SafeNbtWriter.flush();
		RegionStructureSummary region = RegionStructureSummary.index(file, REGION, read(file));
		byte[] garbage = {1, 2, 3};
		Files.write(file.toPath(), garbage);
		region.put(VILLAGE, chunk(3, 3), village(3));
		assertFalse(region.save(file, false));
		SafeNbtWriter.flush();
		assertEquals(3, Files.size(file.toPath()));
	}

	@Test
	@DisplayName("Loading the world indexes structure regions instead of reading every piece")
	void loadIndexes() throws IOException {
		String src = Files.readString(Path.of(System.getProperty("surveyor.projectDir", "."), "src/main/java/folk/sisby/surveyor/structure/WorldStructures.java"));
		String load = src.substring(src.indexOf("public static WorldStructures load("));
		load = load.substring(0, load.indexOf("\n\t}"));
		assertTrue(load.contains("RegionStructureSummary.index("));
		assertFalse(load.contains("RegionStructureSummary.readNbt("));
	}
}
