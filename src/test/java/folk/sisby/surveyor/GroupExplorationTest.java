package folk.sisby.surveyor;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.sun.management.ThreadMXBean;
import folk.sisby.surveyor.util.RegionPos;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.management.ManagementFactory;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupExplorationTest {
	private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
	private static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_nether"));
	private static final ResourceKey<Structure> VILLAGE = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("village"));
	private static final ResourceKey<Structure> TEMPLE = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("temple"));

	private List<SurveyorExploration> members;
	private SurveyorExploration merged;
	private GroupExploration view;

	@BeforeEach
	void explore() {
		Random random = new Random(42);
		members = new java.util.ArrayList<>();
		for (int m = 0; m < 3; m++) {
			var exploration = new PlayerSummary.OfflinePlayerSummary.OfflinePlayerExploration(new HashSet<>(Set.of(UUID.randomUUID())), HashBasedTable.create(), HashBasedTable.create(), true);
			for (int r = 0; r < 6; r++) {
				BitSet bits = new BitSet(RegionPos.CHUNK_AREA);
				for (int b = 0; b < 300; b++) bits.set(random.nextInt(RegionPos.CHUNK_AREA));
				exploration.chunks().put(OVERWORLD, new RegionPos(random.nextInt(4) - 2, random.nextInt(4) - 2), bits);
			}
			LongOpenHashSet starts = new LongOpenHashSet();
			for (int s = 0; s < 20; s++) starts.add(ChunkPos.asLong(random.nextInt(128) - 64, random.nextInt(128) - 64));
			exploration.starts().put(OVERWORLD, m == 0 ? TEMPLE : VILLAGE, starts);
			members.add(exploration);
		}
		merged = PlayerSummary.OfflinePlayerSummary.OfflinePlayerExploration.ofMerged(new HashSet<>(members));
		view = new GroupExploration(members);
	}

	@Test
	@DisplayName("explored chunks and structures match the merged copy")
	void queriesMatchMergedCopy() {
		for (int x = -80; x < 80; x++) {
			for (int z = -80; z < 80; z++) {
				ChunkPos pos = new ChunkPos(x, z);
				assertEquals(merged.exploredChunk(OVERWORLD, pos), view.exploredChunk(OVERWORLD, pos), pos::toString);
				assertEquals(merged.exploredChunk(NETHER, pos), view.exploredChunk(NETHER, pos));
				assertEquals(merged.exploredStructure(OVERWORLD, VILLAGE, pos), view.exploredStructure(OVERWORLD, VILLAGE, pos));
				assertEquals(merged.exploredStructure(OVERWORLD, TEMPLE, pos), view.exploredStructure(OVERWORLD, TEMPLE, pos));
			}
		}
		assertEquals(merged.sharedPlayers(), view.sharedPlayers());
		assertEquals(merged.personal(), view.personal());
	}

	@Test
	@DisplayName("limit() over terrain and structures matches the merged copy")
	void limitsMatchMergedCopy() {
		for (ResourceKey<Level> dimension : List.of(OVERWORLD, NETHER)) {
			Map<RegionPos, BitSet> a = allTerrain(), b = allTerrain();
			assertEquals(merged.limit(dimension, a), view.limit(dimension, b));
			Multimap<ResourceKey<Structure>, ChunkPos> sa = allStarts(), sb = allStarts();
			assertEquals(merged.limit(dimension, sa), view.limit(dimension, sb));
		}
		assertEquals(merged.chunks(), view.chunks(), "the materialised tables equal the old merge");
	}

	@Test
	@DisplayName("getting the group exploration costs O(members), not O(explored regions)")
	void groupViewDoesNotCopy() {
		double copySmall = allocatedPerCall(() -> PlayerSummary.OfflinePlayerSummary.OfflinePlayerExploration.ofMerged(new HashSet<>(members)));
		double liveSmall = allocatedPerCall(() -> new GroupExploration(members));
		growExploration(members, 200);
		double copyLarge = allocatedPerCall(() -> PlayerSummary.OfflinePlayerSummary.OfflinePlayerExploration.ofMerged(new HashSet<>(members)));
		double liveLarge = allocatedPerCall(() -> new GroupExploration(members));
		assertTrue(copyLarge > copySmall * 10, "the old merge copies every explored region (" + copySmall + " → " + copyLarge + " bytes)");
		assertTrue(liveLarge <= liveSmall + 64, "the group view grows with the explored area: " + liveSmall + " → " + liveLarge + " bytes");
		assertTrue(liveLarge < 1024, "the group view allocates " + liveLarge + " bytes");
	}

	@Test
	@DisplayName("group queries allocate no more than the merged copy's")
	void queriesAllocateLikeTheCopy() {
		Runnable viewPass = () -> {
			for (int x = -64; x < 64; x++) for (int z = -64; z < 64; z++) view.exploredChunk(OVERWORLD, new ChunkPos(x, z));
		};
		Runnable copyPass = () -> {
			for (int x = -64; x < 64; x++) for (int z = -64; z < 64; z++) merged.exploredChunk(OVERWORLD, new ChunkPos(x, z));
		};
		double viewBytes = allocatedPerCall(viewPass), copyBytes = allocatedPerCall(copyPass);
		assertTrue(viewBytes <= copyBytes * 1.05 + 1024, "the view's queries allocate " + viewBytes + " bytes per pass, the merged copy's " + copyBytes);
	}

	private static void growExploration(List<SurveyorExploration> members, int regions) {
		Random random = new Random(7);
		for (SurveyorExploration member : members) {
			for (int r = 0; r < regions; r++) {
				BitSet bits = new BitSet(RegionPos.CHUNK_AREA);
				bits.set(0, RegionPos.CHUNK_AREA, true);
				member.chunks().put(OVERWORLD, new RegionPos(100 + random.nextInt(1000), 100 + random.nextInt(1000)), bits);
			}
		}
	}

	private static double allocatedPerCall(Runnable pass) {
		ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
		long thread = Thread.currentThread().threadId();
		for (int i = 0; i < 20; i++) pass.run();
		long before = threads.getThreadAllocatedBytes(thread);
		for (int i = 0; i < 10; i++) pass.run();
		return (threads.getThreadAllocatedBytes(thread) - before) / 10.0;
	}

	private static Map<RegionPos, BitSet> allTerrain() {
		Map<RegionPos, BitSet> map = new HashMap<>();
		for (int x = -3; x < 3; x++) for (int z = -3; z < 3; z++) {
			BitSet all = new BitSet(RegionPos.CHUNK_AREA);
			all.set(0, RegionPos.CHUNK_AREA);
			map.put(new RegionPos(x, z), all);
		}
		return map;
	}

	private static Multimap<ResourceKey<Structure>, ChunkPos> allStarts() {
		Multimap<ResourceKey<Structure>, ChunkPos> map = HashMultimap.create();
		for (int x = -64; x < 64; x += 2) for (int z = -64; z < 64; z += 2) {
			map.put(VILLAGE, new ChunkPos(x, z));
			map.put(TEMPLE, new ChunkPos(x, z));
		}
		return map;
	}
}
