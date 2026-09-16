package folk.sisby.surveyor;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.util.MapUtil;
import folk.sisby.surveyor.util.RegionPos;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;

public class SurveyorEvents {
	private static final Map<ResourceLocation, TerrainUpdated> terrainUpdated = new HashMap<>();
	private static final Map<ResourceLocation, StructuresAdded> structuresAdded = new HashMap<>();
	private static final Map<ResourceLocation, LandmarksAdded> landmarksAdded = new HashMap<>();
	private static final Map<ResourceLocation, LandmarksRemoved> landmarksRemoved = new HashMap<>();

	@FunctionalInterface
	public interface TerrainUpdated {
		void onTerrainUpdated(WorldSummary summary, Map<RegionPos, BitSet> chunks);
	}

	@FunctionalInterface
	public interface StructuresAdded {
		void onStructuresAdded(WorldSummary summary, Multimap<ResourceKey<Structure>, ChunkPos> starts);
	}

	@FunctionalInterface
	public interface LandmarksAdded {
		void onLandmarksAdded(WorldSummary summary, Multimap<UUID, ResourceLocation> landmarks);
	}

	@FunctionalInterface
	public interface LandmarksRemoved {
		void onLandmarksRemoved(WorldSummary summary, Multimap<UUID, ResourceLocation> landmarks);
	}

	public static class Invoke {
		public static void terrainUpdated(WorldSummary summary, Map<RegionPos, BitSet> chunks) {
			if (terrainUpdated.isEmpty() || chunks.isEmpty()) return;
			terrainUpdated.forEach((id, handler) -> handler.onTerrainUpdated(summary, chunks));
		}

		public static void terrainUpdated(WorldSummary summary, ChunkPos pos) {
			terrainUpdated(summary, Map.of(RegionPos.of(pos), RegionPos.chunkToBitSet(pos)));
		}

		public static void structuresAdded(WorldSummary summary, Multimap<ResourceKey<Structure>, ChunkPos> starts) {
			if (structuresAdded.isEmpty() || starts.isEmpty()) return;
			structuresAdded.forEach((id, handler) -> handler.onStructuresAdded(summary, starts));
		}

		public static void structuresAdded(WorldSummary summary, ResourceKey<Structure> key, ChunkPos pos) {
			structuresAdded(summary, MapUtil.asMultiMap(Map.of(key, List.of(pos))));
		}

		public static void landmarksAdded(WorldSummary summary, Multimap<UUID, ResourceLocation> landmarks) {
			if (landmarksAdded.isEmpty() || landmarks.isEmpty()) return;
			landmarksAdded.forEach((id, handler) -> handler.onLandmarksAdded(summary, landmarks));
		}

		public static void landmarksRemoved(WorldSummary summary, Multimap<UUID, ResourceLocation> landmarks) {
			if (landmarksRemoved.isEmpty() || landmarks.isEmpty()) return;
			landmarksRemoved.forEach((id, handler) -> handler.onLandmarksRemoved(summary, landmarks));
		}
	}

	public static class Register {
		public static void terrainUpdated(ResourceLocation id, TerrainUpdated handler) {
			terrainUpdated.put(id, handler);
		}

		public static void structuresAdded(ResourceLocation id, StructuresAdded handler) {
			structuresAdded.put(id, handler);
		}

		public static void landmarksAdded(ResourceLocation id, LandmarksAdded handler) {
			landmarksAdded.put(id, handler);
		}

		public static void landmarksRemoved(ResourceLocation id, LandmarksRemoved handler) {
			landmarksRemoved.put(id, handler);
		}
	}
}
