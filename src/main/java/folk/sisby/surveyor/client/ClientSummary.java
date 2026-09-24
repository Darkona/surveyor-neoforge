package folk.sisby.surveyor.client;

import folk.sisby.surveyor.util.SafeNbtWriter;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Sets;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.WorldSummary;
import net.minecraft.ReportedException;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class ClientSummary {
	public static final String KEY_SHARED = "shared";
	private final String saveFolder;
	private final ClientPacketListener handler;
	private final Map<UUID, PlayerSummary> players;
	private final Map<ResourceKey<Level>, WorldSummary> worlds;
	public final SurveyorClient.ClientExploration personal;
	public final SurveyorClient.ClientExploration shared;
	public final File saveFile;

	public ClientSummary(long biomeSeed, ClientPacketListener handler) {
		this(biomeSeed, null, handler);
	}

	/**
	 * @param worldId the id the server sent during configuration, or null for servers that don't (sisby-folk/surveyor#131).
	 */
	public ClientSummary(long biomeSeed, @Nullable UUID worldId, ClientPacketListener handler) {
		this.saveFolder = SurveyorClient.resolveSaveFolder(biomeSeed, worldId);
		this.handler = handler;
		this.players = new HashMap<>();
		this.worlds = new ConcurrentHashMap<>();
		this.personal = new SurveyorClient.ClientExploration(new HashSet<>(), HashBasedTable.create(), HashBasedTable.create());
		this.shared = new SurveyorClient.ClientExploration(new HashSet<>(), HashBasedTable.create(), HashBasedTable.create());
		this.saveFile = SurveyorClient.getSavePath(saveFolder).toPath().resolve(SurveyorClient.getClientUuid().toString() + ".dat").toFile();
	}

	/**
	 * The folder under {@code data/surveyor} holding this server's map.
	 */
	public String saveFolder() {
		return saveFolder;
	}

	public static ClientSummary of(ClientPacketListener handler) {
		return ((SurveyorNetworkHandler) handler).surveyor$getSummary();
	}

	public void mergeSummaries(Map<UUID, PlayerSummary> summaries) {
		players.putAll(summaries);
	}

	public void matchSummaries(Map<UUID, PlayerSummary> summaries) {
		players.clear();
		players.putAll(summaries);
	}

	private final Map<Player, PlayerSummary.PlayerEntitySummary> entitySummaries = new WeakHashMap<>();

	public Map<UUID, PlayerSummary> players(Set<UUID> group) {
		Map<UUID, PlayerSummary> outMap = new HashMap<>();
		for (UUID uuid : Sets.union(group, players.keySet())) {
			Player player = handler.getLevel().getPlayerByUUID(uuid);
			if (player != null) {
				PlayerSummary.PlayerEntitySummary summary = entitySummaries.get(player);
				if (summary == null) {
					summary = new PlayerSummary.PlayerEntitySummary(player);
					entitySummaries.put(player, summary);
				}
				outMap.put(uuid, summary);
			} else {
				outMap.put(uuid, players.get(uuid));
			}
		}
		return outMap;
	}

	public WorldSummary getWorld(ResourceKey<Level> dimension) {
		return worlds.computeIfAbsent(dimension, k -> new WorldSummary(null, dimension, handler.registryAccess(), SurveyorClient.getWorldSavePath(dimension, saveFolder)));
	}

	public void connect() {
		CompoundTag explorationNbt = new CompoundTag();
		if (saveFile.exists()) {
			try {
				explorationNbt = NbtIo.readCompressed(saveFile.toPath(), NbtAccounter.unlimitedHeap());
			} catch (IOException | ReportedException e) {
				Surveyor.LOGGER.error("[Surveyor] Error loading client exploration file.", e);
			}
		}
		this.personal.read(explorationNbt);
		this.shared.read(explorationNbt.getCompound(KEY_SHARED));
		SurveyorClient.getSummaries(handler); // load it up!
		SurveyorClient.handleInitialLoad(handler); // event it up!
		SurveyorClient.sendKnownData(handler); // network it up!
	}

	public void disconnect() {
		SurveyorClient.clearLoadingChunks();
		for (WorldSummary summary : worlds.values()) {
			summary.save(null, SurveyorClient.getWorldSavePath(summary.dimension(), saveFolder), false);
		}
		CompoundTag nbt = personal.write(new CompoundTag());
		CompoundTag sharedNbt = shared.write(new CompoundTag());
		nbt.put(KEY_SHARED, sharedNbt);
		SafeNbtWriter.write(saveFile.toPath(), nbt);
		SafeNbtWriter.flush();
	}

	public void leaveWorld(ResourceKey<Level> dimension) {
		if (worlds.containsKey(dimension)) worlds.get(dimension).save(null, SurveyorClient.getWorldSavePath(dimension, saveFolder), false);
	}
}
