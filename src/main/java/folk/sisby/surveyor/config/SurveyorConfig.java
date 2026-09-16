package folk.sisby.surveyor.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.neoforged.neoforge.common.ModConfigSpec;

@SuppressWarnings("CanBeFinal")
public class SurveyorConfig {
	public SystemMode terrain = SystemMode.DYNAMIC;
	public SystemMode structures = SystemMode.DYNAMIC;
	public SystemMode landmarks = SystemMode.DYNAMIC;
	public boolean discoveryMessages = false;
	public boolean debugCommands = false;
	public boolean lazyClientUpdating = true;
	public boolean forceUpdateLandmarks = true;
	public Networking networking = new Networking();
	public Builtins builtins = new Builtins();

	public static final class Networking {
		public boolean globalSharing = true;
		public NetworkMode terrain = NetworkMode.GROUP;
		public NetworkMode structures = NetworkMode.GROUP;
		public NetworkMode landmarks = NetworkMode.GROUP;
		public NetworkMode waypoints = NetworkMode.GROUP;
		public NetworkMode positions = NetworkMode.SERVER;
		public int terrainTicks = 20;
		public int positionTicks = 1;
	}

	public static final class Builtins {
		public List<String> allowedBlockEntities = List.of("minecraft:banner");
		public List<String> poiLandmarks = List.of("minecraft:lodestone");
		public boolean netherPortalLandmarks = true;
		public boolean playerDeathWaypoints = true;
		public boolean recordFromMapItems = true;
		public Set<String> disabledRecordIcons = Set.of("minecraft:player", "minecraft:player_off_map", "minecraft:player_off_limits", "minecraft:frame");
		public RecordStyle recordToMapItems = RecordStyle.EXPLORER;

		public Set<Holder<MapDecorationType>> recordIcons() {
			return StreamSupport.stream(BuiltInRegistries.MAP_DECORATION_TYPE.asHolderIdMap().spliterator(), false)
				.filter(t -> !disabledRecordIcons.contains(t.getRegisteredName()))
				.collect(Collectors.toSet());
		}

		public enum RecordStyle {
			NONE,
			EXPLORER,
			COLOR
		}
	}

	private static final String SYSTEM_MODE_COMMENT = "DISABLED prevents loading, FROZEN loads but prevents updates, DYNAMIC loads with addons or on servers, ENABLED always loads";
	private static final String NETWORK_MODE_COMMENT = "SERVER sends server-known data, GROUP sends group-known data, SOLO sends player-known data, NONE sends no data";

	public static final ModConfigSpec SPEC;
	private static final ModConfigSpec.EnumValue<SystemMode> TERRAIN;
	private static final ModConfigSpec.EnumValue<SystemMode> STRUCTURES;
	private static final ModConfigSpec.EnumValue<SystemMode> LANDMARKS;
	private static final ModConfigSpec.BooleanValue DISCOVERY_MESSAGES;
	private static final ModConfigSpec.BooleanValue DEBUG_COMMANDS;
	private static final ModConfigSpec.BooleanValue LAZY_CLIENT_UPDATING;
	private static final ModConfigSpec.BooleanValue FORCE_UPDATE_LANDMARKS;
	private static final ModConfigSpec.BooleanValue NET_GLOBAL_SHARING;
	private static final ModConfigSpec.EnumValue<NetworkMode> NET_TERRAIN;
	private static final ModConfigSpec.EnumValue<NetworkMode> NET_STRUCTURES;
	private static final ModConfigSpec.EnumValue<NetworkMode> NET_LANDMARKS;
	private static final ModConfigSpec.EnumValue<NetworkMode> NET_WAYPOINTS;
	private static final ModConfigSpec.EnumValue<NetworkMode> NET_POSITIONS;
	private static final ModConfigSpec.IntValue NET_TERRAIN_TICKS;
	private static final ModConfigSpec.IntValue NET_POSITION_TICKS;
	private static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_BLOCK_ENTITIES;
	private static final ModConfigSpec.ConfigValue<List<? extends String>> POI_LANDMARKS;
	private static final ModConfigSpec.BooleanValue NETHER_PORTAL_LANDMARKS;
	private static final ModConfigSpec.BooleanValue PLAYER_DEATH_WAYPOINTS;
	private static final ModConfigSpec.BooleanValue RECORD_FROM_MAP_ITEMS;
	private static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_RECORD_ICONS;
	private static final ModConfigSpec.EnumValue<Builtins.RecordStyle> RECORD_TO_MAP_ITEMS;

	static {
		SurveyorConfig d = new SurveyorConfig();
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();

		TERRAIN = b.comment("Terrain system - records layers of blocks and biomes for maps to render", SYSTEM_MODE_COMMENT).defineEnum("terrain", d.terrain);
		STRUCTURES = b.comment("Structure system - records structure identifiers and piece data for specialized maps and utilities to render", SYSTEM_MODE_COMMENT).defineEnum("structures", d.structures);
		LANDMARKS = b.comment("Landmark system - a generic record of both player-owned waypoints and server-owned POIs, accessible via API", SYSTEM_MODE_COMMENT).defineEnum("landmarks", d.landmarks);
		DISCOVERY_MESSAGES = b.comment("Logs structure discovery to the action bar.", "E.g. 'Discovered Village Plains at [91, 63, -54]'").define("discoveryMessages", d.discoveryMessages);
		DEBUG_COMMANDS = b.comment("Force-enables the following commands.", "waypoints/landmarks raw | prints the raw SNBT of a landmark").define("debugCommands", d.debugCommands);
		LAZY_CLIENT_UPDATING = b.comment("Ignores chunk changes that don't affect the amount of air in the chunk", "Saves on performance, a little inaccurate sometimes.").define("lazyClientUpdating", d.lazyClientUpdating);
		FORCE_UPDATE_LANDMARKS = b.comment("Ignores known landmarks when syncing landmarks to the client", "A temporary fix until landmarks have some kind of revision counter").define("forceUpdateLandmarks", d.forceUpdateLandmarks);

		b.push("networking");
		NET_GLOBAL_SHARING = b.comment("[Server] Whether to place every player in a single share group", "Disables /surveyor share and /surveyor unshare").define("globalSharing", d.networking.globalSharing);
		NET_TERRAIN = b.comment("How much terrain data to send to clients", NETWORK_MODE_COMMENT).defineEnum("terrain", d.networking.terrain);
		NET_STRUCTURES = b.comment("How much structure data to send to clients", NETWORK_MODE_COMMENT, "When NONE, clients will never see structures").defineEnum("structures", d.networking.structures);
		NET_LANDMARKS = b.comment("Which landmarks to sync between client and server", "SERVER sync server-known landmarks, GROUP sends group-known landmarks, SOLO sends player-known landmarks, NONE sends no landmarks").defineEnum("landmarks", d.networking.landmarks);
		NET_WAYPOINTS = b.comment(
			"Which waypoints (player-created landmarks) to sync between client and server",
			"When SERVER, players can see (but not edit) all waypoints, including potentially offensive names",
			"When GROUP, players can see (but not edit) waypoints created by players in their share group",
			"When SOLO, player-created waypoints will be stored on the server as a backup",
			"When NONE, waypoint data will never be synced (e.g. for privacy)"
		).defineEnum("waypoints", d.networking.waypoints);
		NET_POSITIONS = b.comment(
			"[Server] How much player position data to send to clients",
			"SERVER sends all players positions, GROUP sends just group players, SOLO sends nothing, NONE sends nothing",
			"Players will only see the offline positions of their group members, or players who disconnected while they were online."
		).defineEnum("positions", d.networking.positions);
		NET_TERRAIN_TICKS = b.comment("[Server] Ticks per terrain region load for batch update - lower is more frequent").defineInRange("terrainTicks", d.networking.terrainTicks, 1, 200);
		NET_POSITION_TICKS = b.comment("[Server] Ticks per position update - lower is more frequent").defineInRange("positionTicks", d.networking.positionTicks, 1, 200);
		b.pop();

		b.push("builtins");
		ALLOWED_BLOCK_ENTITIES = b.comment("Which block entities to preserve data for when creating block landmarks.").defineListAllowEmpty("allowedBlockEntities", d.builtins.allowedBlockEntities, () -> "", o -> o instanceof String);
		POI_LANDMARKS = b.comment("Which points of interest to automatically add block landmarks for.").defineListAllowEmpty("poiLandmarks", d.builtins.poiLandmarks, () -> "", o -> o instanceof String);
		NETHER_PORTAL_LANDMARKS = b.comment("Whether to automatically add specialised nether portal POI landmarks.", "Creates one landmark for each nether portal, instead of one per portal block.").define("netherPortalLandmarks", d.builtins.netherPortalLandmarks);
		PLAYER_DEATH_WAYPOINTS = b.comment("Whether to automatically add player death waypoints").define("playerDeathWaypoints", d.builtins.playerDeathWaypoints);
		RECORD_FROM_MAP_ITEMS = b.comment("Allows recording terrain and waypoints from map items by sneak+using them at a cartography table.", "Viable blocks configured via #surveyor:record_from_map").define("recordFromMapItems", d.builtins.recordFromMapItems);
		DISABLED_RECORD_ICONS = b.comment("Map decoration types that are NOT recorded as landmarks when recording from map items.").defineListAllowEmpty("disabledRecordIcons", List.copyOf(d.builtins.disabledRecordIcons), () -> "", o -> o instanceof String);
		RECORD_TO_MAP_ITEMS = b.comment("Allows recording terrain to map items by sneak+using them at a cartography table.", "Viable blocks configured via #surveyor:record_to_map").defineEnum("recordToMapItems", d.builtins.recordToMapItems);
		b.pop();

		SPEC = b.build();
	}

	public void bake() {
		terrain = TERRAIN.get();
		structures = STRUCTURES.get();
		landmarks = LANDMARKS.get();
		discoveryMessages = DISCOVERY_MESSAGES.get();
		debugCommands = DEBUG_COMMANDS.get();
		lazyClientUpdating = LAZY_CLIENT_UPDATING.get();
		forceUpdateLandmarks = FORCE_UPDATE_LANDMARKS.get();
		networking.globalSharing = NET_GLOBAL_SHARING.get();
		networking.terrain = NET_TERRAIN.get();
		networking.structures = NET_STRUCTURES.get();
		networking.landmarks = NET_LANDMARKS.get();
		networking.waypoints = NET_WAYPOINTS.get();
		networking.positions = NET_POSITIONS.get();
		networking.terrainTicks = NET_TERRAIN_TICKS.get();
		networking.positionTicks = NET_POSITION_TICKS.get();
		builtins.allowedBlockEntities = List.copyOf(ALLOWED_BLOCK_ENTITIES.get());
		builtins.poiLandmarks = List.copyOf(POI_LANDMARKS.get());
		builtins.netherPortalLandmarks = NETHER_PORTAL_LANDMARKS.get();
		builtins.playerDeathWaypoints = PLAYER_DEATH_WAYPOINTS.get();
		builtins.recordFromMapItems = RECORD_FROM_MAP_ITEMS.get();
		builtins.disabledRecordIcons = new HashSet<>(DISABLED_RECORD_ICONS.get());
		builtins.recordToMapItems = RECORD_TO_MAP_ITEMS.get();
	}
}
