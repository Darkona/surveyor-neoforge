package folk.sisby.surveyor;

import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.config.SurveyorConfig;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.PoiLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.structure.StructureStartSummary;
import folk.sisby.surveyor.structure.WorldStructures;
import folk.sisby.surveyor.terrain.WorldTerrain;
import folk.sisby.surveyor.util.RaycastUtil;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.commons.lang3.text.WordUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.UUID;

@Mod(Surveyor.ID)
public class Surveyor {
	public static final String ID = "surveyor";
	public static final Logger LOGGER = LoggerFactory.getLogger(ID);
	public static final String DATA_SUBFOLDER = "data";
	public static final SurveyorConfig CONFIG = new SurveyorConfig();

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(ID, path);
	}

	public static File getSavePath(ResourceKey<Level> dimension, MinecraftServer server) {
		return DimensionType.getStorageFolder(dimension, server.getWorldPath(LevelResource.ROOT)).resolve(DATA_SUBFOLDER).resolve(Surveyor.ID).toFile();
	}

	public static void checkStructureExploration(ServerLevel world, ServerPlayer player, BlockPos pos) {
		if (!world.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return;
		WorldStructures structures = WorldStructures.of(world);
		if (structures == null) return;
		Registry<Structure> structureRegistry = world.registryAccess().registryOrThrow(Registries.STRUCTURE);
		SurveyorExploration exploration = SurveyorExploration.of(player);
		Map<Structure, LongSet> structureReferences = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.STRUCTURE_REFERENCES).getAllReferences();
		if (!structureReferences.isEmpty()) {
			for (Structure structure : structureReferences.keySet()) {
				// Fix: a structure missing from the registry crashed the server tick every 8 ticks near it (surveyor#142).
				ResourceKey<Structure> structureKey = structureRegistry.getResourceKey(structure).orElse(null);
				if (structureKey == null) continue;
				for (LongIterator it = structureReferences.get(structure).iterator(); it.hasNext(); ) {
					ChunkPos startPos = new ChunkPos(it.nextLong());
					if (exploration.exploredStructure(world.dimension(), structureKey, startPos)) continue;
					StructureStartSummary start = structures.get(structureKey, startPos);
					if (start == null) continue;
					if (insideInflated(start.getBoundingBox(), pos)) {
						for (StructurePiece piece : start.getChildren()) {
							if (insideInflated(piece.getBoundingBox(), pos)) {
								exploration.addStructure(world.dimension(), structureKey, startPos);
								if (SurveyorDebug.on) SurveyorDebug.log("{} discovered {} (start chunk {}) in {}", player.getGameProfile().getName(), structureKey.location(), startPos, world.dimension().location());
								if (CONFIG.discoveryMessages) {
									player.sendSystemMessage(Component.literal("Discovered ").append(Component.literal(WordUtils.capitalize(structureKey.location().getPath().replace("_", " "))).withStyle(ChatFormatting.GREEN)).append(Component.literal(" at ")).append(Component.literal("[%s,%s]".formatted(startPos.x << 4, startPos.z << 4)).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GRAY), true);
								}
								break;
							}
						}
					}
				}
			}
		}
	}

	private static boolean insideInflated(BoundingBox box, BlockPos pos) {
		return pos.getX() >= box.minX() - 2 && pos.getX() <= box.maxX() + 2
			&& pos.getY() >= box.minY() - 2 && pos.getY() <= box.maxY() + 2
			&& pos.getZ() >= box.minZ() - 2 && pos.getZ() <= box.maxZ() + 2;
	}

	public static boolean canModify(UUID landmarkOwner, @Nullable ServerPlayer player) {
		return player == null || player.hasPermissions(2) || landmarkOwner.equals(Surveyor.getUuid(player)) || !landmarkOwner.equals(WorldLandmarks.GLOBAL) && (Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.SERVER) || (Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.GROUP) && ServerSummary.of(player.getServer()).getGroup(Surveyor.getUuid(player)).contains(landmarkOwner)));
	}

	public static UUID getUuid(ServerPlayer player) {
		return player.getServer() != null && player.getServer().isSingleplayerOwner(player.getGameProfile()) ? ServerSummary.HOST : player.getUUID();
	}

	public static ServerPlayer getPlayer(MinecraftServer server, UUID uuid) {
		return server.getPlayerList().getPlayer(uuid == ServerSummary.HOST && server.getSingleplayerProfile() != null ? server.getSingleplayerProfile().getId() : uuid);
	}

	static SurveyorExploration explorationForMode(NetworkMode mode, ServerPlayer player) {
		return mode.atLeast(NetworkMode.SERVER) ? null : mode.atLeast(NetworkMode.GROUP) ? SurveyorExploration.ofShared(player) : mode.atLeast(NetworkMode.SOLO) ? SurveyorExploration.of(player) : PlayerSummary.OfflinePlayerSummary.OfflinePlayerExploration.empty(player.getUUID());
	}

	public Surveyor(IEventBus modBus, ModContainer container) {
		container.registerConfig(ModConfig.Type.COMMON, SurveyorConfig.SPEC, "surveyor.toml");
		modBus.addListener(ModConfigEvent.Loading.class, e -> onConfig(e.getConfig()));
		modBus.addListener(ModConfigEvent.Reloading.class, e -> onConfig(e.getConfig()));
		modBus.addListener(SurveyorNetworking::register);
		modBus.addListener(SurveyorNetworking::registerTasks);
		LandmarkComponentTypes.touch();
		SurveyorGameEvents.register();
		NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, e -> SurveyorCommands.registerCommands(e.getDispatcher(), e.getBuildContext(), e.getCommandSelection()));
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
			if (e.getEntity() instanceof ServerPlayer player) ServerSummary.onPlayerJoin(player, player.server);
		});
		NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, e -> {
			if (e.getLevel() instanceof ServerLevel world && e.getChunk() instanceof LevelChunk chunk) {
				WorldTerrain.onChunkLoad(world, chunk);
				WorldStructures.onChunkLoad(world, chunk);
				PoiLandmarks.onChunkLoad(world, chunk.getPos());
			}
		});
		NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class, e -> {
			if (e.getLevel() instanceof ServerLevel world && e.getChunk() instanceof LevelChunk chunk) WorldTerrain.onChunkUnload(world, chunk);
		});
		NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, e -> {
			if (e.getLevel() instanceof ServerLevel world) PoiLandmarks.onLevelUnload(world);
		});
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> {
			ServerSummary.onTick(e.getServer());
			if (SurveyorDebug.on) SurveyorDebug.tick(e.getServer().getTickCount());
		});
		NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, e -> {
			if (e.getLevel() instanceof ServerLevel world) onWorldTick(world);
		});
		LOGGER.info("[Surveyor] is not a map mod");
	}

	private static void onConfig(ModConfig config) {
		if (config.getSpec() == SurveyorConfig.SPEC) CONFIG.bake();
	}

	private static void onWorldTick(ServerLevel world) {
		WorldTerrain.onTick(world);
		WorldStructures.onTick(world);
		PoiLandmarks.onTick(world);
		if ((world.getGameTime() & 7) != 0) return;
		for (ServerPlayer player : world.players()) {
			checkStructureExploration(world, player, player.blockPosition());
			PlayerSummary summary = PlayerSummary.of(player);
			long viewHit = summary instanceof PlayerSummary.ServerPlayerEntitySummary serverSummary
				? serverSummary.viewHit(() -> BlockPos.containing(RaycastUtil.playerViewRaycast(player, summary.viewDistance()).getLocation()))
				: BlockPos.containing(RaycastUtil.playerViewRaycast(player, summary.viewDistance()).getLocation()).asLong();
			checkStructureExploration(world, player, BlockPos.of(viewHit));
		}
	}
}
