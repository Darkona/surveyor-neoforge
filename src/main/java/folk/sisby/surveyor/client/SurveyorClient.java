package folk.sisby.surveyor.client;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.mojang.authlib.GameProfile;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.ServerSummary;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.packet.C2SKnownLandmarksPacket;
import folk.sisby.surveyor.packet.C2SKnownStructuresPacket;
import folk.sisby.surveyor.packet.C2SKnownTerrainPacket;
import folk.sisby.surveyor.structure.WorldStructures;
import folk.sisby.surveyor.terrain.WorldTerrain;
import folk.sisby.surveyor.util.RegionPos;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import folk.sisby.surveyor.GroupExploration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mod(value = Surveyor.ID, dist = Dist.CLIENT)
public class SurveyorClient {
	public static final String SERVERS_FILE_NAME = "servers.txt";
	private static final Multimap<ResourceKey<Level>, LevelChunk> LOADING_CHUNKS = HashMultimap.create();

	public static void clearLoadingChunks() {
		LOADING_CHUNKS.clear();
	}

	public static File getSavePath(long biomeSeed) {
		String saveFolder = String.valueOf(biomeSeed);
		Path savePath = FMLPaths.GAMEDIR.get().resolve(Surveyor.DATA_SUBFOLDER).resolve(Surveyor.ID).resolve(saveFolder);
		savePath.toFile().mkdirs();
		File serversFile = savePath.resolve(SERVERS_FILE_NAME).toFile();
		try {
			ServerData info = Minecraft.getInstance().getCurrentServer();
			if (info != null && (!serversFile.exists() || !FileUtils.readFileToString(serversFile, StandardCharsets.UTF_8).contains(info.name + "\n" + info.ip))) {
				FileUtils.writeStringToFile(serversFile, info.name + "\n" + info.ip + "\n", StandardCharsets.UTF_8, true);
			}
		} catch (IOException e) {
			Surveyor.LOGGER.error("[Surveyor] Error writing servers file for save {}.", savePath, e);
		}
		return savePath.toFile();
	}

	public static File getWorldSavePath(ResourceKey<Level> dimension, long biomeSeed) {
		String dimNamespace = dimension.location().getNamespace();
		String dimPath = dimension.location().getPath();
		return getSavePath(biomeSeed).toPath().resolve(dimNamespace).resolve(dimPath).toFile();
	}

	public static @Nullable File getXaerosSavePath(ResourceKey<Level> dimension) {
		File baseFolder = FMLPaths.GAMEDIR.get().resolve("xaero").resolve("minimap").toFile();
		if (!baseFolder.exists()) return null;
		String id = null;
		try {
			id = Minecraft.getInstance().getCurrentServer() != null ? Minecraft.getInstance().getCurrentServer().ip : Minecraft.getInstance().getSingleplayerServer().getWorldPath(LevelResource.ROOT).getParent().toFile().getName();
			String sanitized = (Minecraft.getInstance().getCurrentServer() != null ? "Multiplayer_" : "") + (id.contains(":") ? id.substring(0, id.indexOf(":")) : id).replace("_", "%us%").replace("\\", "%bs%").replace("/", "%fs%").replace(":", "§").replace("[", "%lb%").replace("]", "%rb%");
			File saveFolder = baseFolder.toPath().resolve(sanitized).toFile();
			if (!saveFolder.exists()) return null;
			String sanitizedDim = dimension == Level.OVERWORLD ? "dim%0" : dimension == Level.NETHER ? "dim%-1" : dimension == Level.END ? "dim%1" : "dim%" + dimension.toString().replace(":", "$").replace('/', '%');
			File dimFolder = saveFolder.toPath().resolve(sanitizedDim).toFile();
			if (!dimFolder.exists() || dimFolder.toPath().resolve(".surveyor_migrated").toFile().exists()) return null;
			return dimFolder;
		} catch (Exception e) {
			Surveyor.LOGGER.error("[Surveyor] Error fetching xaeros data for {} {}", id,  dimension, e);
		}
		return null;
	}

	public static boolean serverSupported() {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		return connection != null && connection.hasChannel(C2SKnownTerrainPacket.ID);
	}

	public static Map<UUID, PlayerSummary> getFriends() {
		if (Minecraft.getInstance().hasSingleplayerServer()) {
			MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
			return ServerSummary.of(server).getGroupSummaries(getClientUuid());
		} else {
			ClientPacketListener handler = Minecraft.getInstance().getConnection();
			if (handler == null) return new HashMap<>();
			ClientSummary clientSummary = ClientSummary.of(handler);
			return clientSummary.players(clientSummary.shared.sharedPlayers());
		}
	}

	public static SurveyorExploration getExploration() {
		if (Minecraft.getInstance().hasSingleplayerServer()) {
			return SurveyorExploration.ofShared(getClientUuid(), Minecraft.getInstance().getSingleplayerServer());
		} else {
			ClientSummary summary = ClientSummary.of(Minecraft.getInstance().getConnection());
			return new GroupExploration(List.of(summary.personal, summary.shared));
		}
	}

	public static SurveyorExploration getPersonalExploration() {
		if (Minecraft.getInstance().hasSingleplayerServer()) {
			return SurveyorExploration.of(getClientUuid(), Minecraft.getInstance().getSingleplayerServer());
		} else {
			return ClientSummary.of(Minecraft.getInstance().getConnection()).personal;
		}
	}

	public static ClientExploration getSharedExploration() {
		if (Minecraft.getInstance().hasSingleplayerServer()) {
			throw new IllegalStateException("You can't edit shared exploration in singleplayer!");
		} else {
			return ClientSummary.of(Minecraft.getInstance().getConnection()).shared;
		}
	}

	public static UUID getClientUuid() { // UUID needs to always match what the server is using.
		if (Minecraft.getInstance().hasSingleplayerServer()) return ServerSummary.HOST;
		GameProfile profile = ((SurveyorNetworkHandler) Minecraft.getInstance().getConnection()).getLocalGameProfile();
		return profile.getId();
	}

	public static ServerLevel stealServerWorld(ResourceKey<Level> dimension) {
		MinecraftServer integratedServer = Minecraft.getInstance().getSingleplayerServer();
		if (integratedServer == null) return null;
		return integratedServer.getLevel(dimension);
	}

	public static Map<ResourceKey<Level>, WorldSummary> getSummaries(ClientPacketListener handler) {
		// Fix: toMap rejects the null summary of a connection without a ClientSummary (e.g. a ReplayMod playback).
		Map<ResourceKey<Level>, WorldSummary> summaries = new HashMap<>();
		for (ResourceKey<Level> dimension : handler.levels()) {
			WorldSummary summary = getSummary(dimension, handler);
			if (summary != null) summaries.put(dimension, summary);
		}
		return summaries;
	}

	public static WorldSummary getSummary(ResourceKey<Level> dimension, ClientPacketListener handler) {
		if (Minecraft.getInstance().hasSingleplayerServer()) {
			return WorldSummary.of(SurveyorClient.stealServerWorld(dimension));
		} else {
			ClientSummary summary = ClientSummary.of(handler);
			return summary == null ? null : summary.getWorld(dimension);
		}
	}

	public static boolean canModify(UUID landmarkOwner) {
		if (Minecraft.getInstance().hasSingleplayerServer()) return Surveyor.canModify(landmarkOwner, Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayer(Minecraft.getInstance().player.getGameProfile().getId()));
		return landmarkOwner.equals(SurveyorClient.getClientUuid()) || !landmarkOwner.equals(WorldLandmarks.GLOBAL) && (Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.SERVER) || (Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.GROUP) && SurveyorClient.getSharedExploration().groupPlayers().contains(landmarkOwner)));
	}

	public static @Nullable WorldSummary tryGetSummary(ResourceKey<Level> dimension) {
		if (Minecraft.getInstance().hasSingleplayerServer()) {
			return WorldSummary.of(SurveyorClient.stealServerWorld(dimension));
		} else {
			ClientPacketListener handler = Minecraft.getInstance().getConnection();
			return handler == null ? null : getSummary(dimension, handler);
		}
	}

	public static @Nullable Level getWorld(ResourceKey<Level> dimension) {
		ClientLevel world = Minecraft.getInstance().level;
		return world != null && world.dimension().equals(dimension) ? world : null;
	}

	public static void handleInitialLoad(ClientPacketListener handler) {
		SurveyorExploration exploration = getExploration();
		for (WorldSummary summary : SurveyorClient.getSummaries(handler).values()) {
			WorldTerrain terrain = summary.terrain();
			if (terrain != null) SurveyorClientEvents.Invoke.terrainUpdated(summary, terrain.bitSet(exploration));
			WorldStructures structures = summary.structures();
			if (structures != null) SurveyorClientEvents.Invoke.structuresAdded(summary, structures.keySet(exploration));
			WorldLandmarks landmarks = summary.landmarks();
			if (landmarks != null) {
				landmarks.tryMigrateXaeros(false);
				SurveyorClientEvents.Invoke.landmarksAdded(summary, landmarks.keySet(exploration));
			}
		}
	}

	public static void sendKnownData(ClientPacketListener handler) {
		Table<ResourceKey<Level>, RegionPos, BitSet> chunks = HashBasedTable.create();
		Map<ResourceKey<Level>, Multimap<ResourceKey<Structure>, ChunkPos>> starts = new HashMap<>();
		Map<ResourceKey<Level>, Multimap<UUID, ResourceLocation>> landmarkKeys = new HashMap<>();
		boolean hasTerrain = false;
		boolean hasStructures = false;
		boolean hasLandmarks = false;
		for (WorldSummary summary : SurveyorClient.getSummaries(handler).values()) {
			WorldTerrain terrain = summary.terrain();
			if (terrain != null && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SOLO)) {
				chunks.row(summary.dimension()).putAll(terrain.bitSet(null));
				hasTerrain = true;
			}
			WorldStructures structures = summary.structures();
			if (structures != null && Surveyor.CONFIG.networking.structures.atLeast(NetworkMode.SOLO)) {
				starts.put(summary.dimension(), structures.keySet(null));
				hasStructures = true;
			}
			WorldLandmarks landmarks = summary.landmarks();
			if (landmarks != null && Surveyor.CONFIG.networking.landmarks.atLeast(NetworkMode.SOLO)) {
				landmarkKeys.put(summary.dimension(), landmarks.keySet(null));
				hasLandmarks = true;
			}
		}
		if (hasTerrain) new C2SKnownTerrainPacket(chunks).send();
		if (hasStructures) new C2SKnownStructuresPacket(starts).send();
		if (hasLandmarks) new C2SKnownLandmarksPacket(landmarkKeys).send();
	}

	public SurveyorClient(ModContainer container) {
		// Fix: the mod list's Config button was disabled; NeoForge builds the screen from the config spec.
		container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
		SurveyorClientNetworking.init();
		NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, e -> SurveyorClientCommands.registerCommands(e.getDispatcher(), e.getBuildContext()));
		NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, e -> {
			if (e.getLevel() instanceof ClientLevel world && e.getChunk() instanceof LevelChunk chunk && !Minecraft.getInstance().isLocalServer()) LOADING_CHUNKS.put(world.dimension(), chunk);
		});
		NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class, e -> {
			if (e.getLevel() instanceof ClientLevel world && e.getChunk() instanceof LevelChunk chunk && !Minecraft.getInstance().isLocalServer()) WorldTerrain.onChunkUnload(world, chunk);
		});
		NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, e -> {
			if (e.getLevel() instanceof ClientLevel world) onClientWorldTick(world);
		});
		SurveyorEvents.Register.landmarksAdded(Surveyor.id("client"), ((summary, landmarks) -> {
			SurveyorExploration exploration = getExploration();
			if (exploration != null) SurveyorClientEvents.Invoke.landmarksAdded(summary, exploration.limit(summary.dimension(), summary.landmarks(), HashMultimap.create(landmarks)));
		}));
		SurveyorEvents.Register.landmarksRemoved(Surveyor.id("client"), SurveyorClientEvents.Invoke::landmarksRemoved);
		Surveyor.LOGGER.info("[Surveyor Client] is not a map mod either");
	}

	private static void onClientWorldTick(ClientLevel world) {
		if (LOADING_CHUNKS.isEmpty()) return;
		if (Minecraft.getInstance().levelRenderer.countRenderedSections() <= 10 || !Minecraft.getInstance().levelRenderer.hasRenderedAllSections()) return;
		for (LevelChunk chunk : new HashSet<>(LOADING_CHUNKS.get(world.dimension()))) {
			WorldTerrain.onChunkLoad(world, chunk);
			ClientSummary.of(Minecraft.getInstance().getConnection()).personal.addChunk(world.dimension(), chunk.getPos(), false);
			LOADING_CHUNKS.remove(world.dimension(), chunk);
		}
	}

	public record ClientExploration(Set<UUID> groupPlayers, Table<ResourceKey<Level>, RegionPos, BitSet> chunks, Table<ResourceKey<Level>, ResourceKey<Structure>, LongSet> starts) implements SurveyorExploration {
		@Override
		public Set<UUID> sharedPlayers() {
			Set<UUID> sharedPlayers = new HashSet<>();
			sharedPlayers.add(getClientUuid());
			sharedPlayers.addAll(groupPlayers);
			return sharedPlayers;
		}

		@Override
		public boolean personal() {
			return true;
		}

		@Override
		public void addStructure(ResourceKey<Level> dimension, ResourceKey<Structure> structureKey, ChunkPos pos) {
			SurveyorExploration.super.addStructure(dimension, structureKey, pos);
			WorldSummary summary = tryGetSummary(dimension);
			if (summary != null) updateClientForAddStructure(summary, structureKey, pos);
		}

		@Override
		public void mergeRegion(ResourceKey<Level> dimension, RegionPos regionPos, BitSet chunks, boolean updateClient) {
			SurveyorExploration.super.mergeRegion(dimension, regionPos, chunks, updateClient);
			WorldSummary summary = tryGetSummary(dimension);
			if (summary != null) updateClientForMergeRegion(summary, regionPos, chunks);
		}

		@Override
		public void addChunk(ResourceKey<Level> dimension, ChunkPos pos, boolean updateClient) {
			SurveyorExploration.super.addChunk(dimension, pos, updateClient);
			WorldSummary summary = tryGetSummary(dimension);
			if (summary != null) updateClientForAddChunk(summary, pos);
		}
	}
}
