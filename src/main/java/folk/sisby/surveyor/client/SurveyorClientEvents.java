package folk.sisby.surveyor.client;

import net.minecraft.client.Minecraft;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import folk.sisby.surveyor.WorldSummary;
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

public class SurveyorClientEvents {
	private static final Map<ResourceLocation, TerrainUpdated> terrainUpdated = new HashMap<>();
	private static final Map<ResourceLocation, StructuresAdded> structuresAdded = new HashMap<>();
	private static final Map<ResourceLocation, LandmarksAdded> landmarksAdded = new HashMap<>();
	private static final Map<ResourceLocation, LandmarksRemoved> landmarksRemoved = new HashMap<>();
	private static final Map<ResourceLocation, ExplorationReset> explorationReset = new HashMap<>();

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

	/**
	 * The client's shared exploration was replaced rather than extended (e.g. the player's share group changed): chunks and
	 * structures reported before may no longer be explored. Map mods should drop what they drew and redraw from
	 * {@link WorldSummary} data and {@link SurveyorClient#getExploration()}. Addition of this fork.
	 */
	@FunctionalInterface
	public interface ExplorationReset {
		void onExplorationReset();
	}

	// Fix: addon callbacks run on the client thread, also when called from the integrated server.
	public static class Invoke {
		public static void terrainUpdated(WorldSummary summary, Map<RegionPos, BitSet> chunks) {
			if (terrainUpdated.isEmpty() || chunks.isEmpty()) return;
			if (onClientThread()) {
				terrainUpdated.forEach((id, handler) -> handler.onTerrainUpdated(summary, chunks));
			} else {
				Map<RegionPos, BitSet> snapshot = new HashMap<>(chunks.size());
				chunks.forEach((region, bits) -> snapshot.put(region, (BitSet) bits.clone()));
				Minecraft.getInstance().execute(() -> terrainUpdated.forEach((id, handler) -> handler.onTerrainUpdated(summary, snapshot)));
			}
		}

		public static void terrainUpdated(WorldSummary summary, ChunkPos pos) {
			terrainUpdated(summary, Map.of(RegionPos.of(pos), RegionPos.chunkToBitSet(pos)));
		}

		public static void structuresAdded(WorldSummary summary, Multimap<ResourceKey<Structure>, ChunkPos> starts) {
			if (structuresAdded.isEmpty() || starts.isEmpty()) return;
			Multimap<ResourceKey<Structure>, ChunkPos> args = onClientThread() ? starts : ImmutableMultimap.copyOf(starts);
			runOnClient(() -> structuresAdded.forEach((id, handler) -> handler.onStructuresAdded(summary, args)));
		}

		public static void structuresAdded(WorldSummary summary, ResourceKey<Structure> key, ChunkPos pos) {
			structuresAdded(summary, MapUtil.asMultiMap(Map.of(key, List.of(pos))));
		}

		public static void landmarksAdded(WorldSummary summary, Multimap<UUID, ResourceLocation> landmarks) {
			if (landmarksAdded.isEmpty() || landmarks.isEmpty()) return;
			Multimap<UUID, ResourceLocation> args = onClientThread() ? landmarks : ImmutableMultimap.copyOf(landmarks);
			runOnClient(() -> landmarksAdded.forEach((id, handler) -> handler.onLandmarksAdded(summary, args)));
		}

		public static void landmarksRemoved(WorldSummary summary, Multimap<UUID, ResourceLocation> landmarks) {
			if (landmarksRemoved.isEmpty() || landmarks.isEmpty()) return;
			Multimap<UUID, ResourceLocation> args = onClientThread() ? landmarks : ImmutableMultimap.copyOf(landmarks);
			runOnClient(() -> landmarksRemoved.forEach((id, handler) -> handler.onLandmarksRemoved(summary, args)));
		}

		public static void explorationReset() {
			if (explorationReset.isEmpty()) return;
			runOnClient(() -> explorationReset.forEach((id, handler) -> handler.onExplorationReset()));
		}

		private static boolean onClientThread() {
			return Minecraft.getInstance().isSameThread();
		}

		private static void runOnClient(Runnable task) {
			if (onClientThread()) {
				task.run();
			} else {
				Minecraft.getInstance().execute(task);
			}
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

		public static void explorationReset(ResourceLocation id, ExplorationReset handler) {
			explorationReset.put(id, handler);
		}
	}
}
