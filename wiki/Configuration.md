# Configuration

The options of Surveyor are in `config/surveyor.toml`. You can also edit them in game from the mod list (Mods → Surveyor → Config). On a server, the file of the server decides what is shared. Most changes need a restart.

## Systems

| Option | Default | What it does |
|---|---|---|
| `terrain` | `DYNAMIC` | Terrain recording. |
| `structures` | `DYNAMIC` | Structure recording. |
| `landmarks` | `DYNAMIC` | Waypoints and landmarks. |

Each one takes one of these values: `DISABLED` (off), `FROZEN` (Surveyor loads the existing data but never changes it), `DYNAMIC` (on when a map mod asks for it, and always on for servers) or `ENABLED` (always on).

## General

| Option | Default | What it does |
|---|---|---|
| `discoveryMessages` | `false` | Show "Discovered <structure> at <position>" above the hotbar when you find a structure. |
| `debugCommands` | `false` | Turn on the `raw` commands. |
| `lazyClientUpdating` | `true` | On multiplayer clients, record a chunk again only when the amount of air in it changed. Faster, and it rarely misses a change. |
| `asyncChunkSummaries` | `true` | Servers record loaded chunks on a background thread instead of the server thread. The map is the same. Turn it off if you think it causes a problem. |
| `forceUpdateLandmarks` | `true` | Always send landmarks again on join, instead of trusting what the client already has. |
| `debug` | `false` | Log what Surveyor does to `latest.log`, for bug reports. See [Data and Troubleshooting](Data-and-Troubleshooting#debug-mode). |

## `[networking]`

| Option | Default | What it does |
|---|---|---|
| `globalSharing` | `true` | Everyone shares one map. Set it to `false` on public servers. Players then make groups with `/surveyor share`. |
| `terrain` | `GROUP` | How far explored terrain is shared. |
| `structures` | `GROUP` | How far discovered structures are shared. With `NONE`, clients never see structures. |
| `landmarks` | `GROUP` | How far server-wide landmarks are shared. |
| `waypoints` | `GROUP` | How far player waypoints are shared. With `SERVER`, everyone sees the waypoints of everyone, but cannot edit them. With `NONE`, waypoints stay on your computer. |
| `positions` | `SERVER` | Who sees where players are. |
| `terrainTicks` | `20` | Ticks between the terrain batches sent to a player who joins (1–200). A lower value is faster and puts more load on the server. |
| `positionTicks` | `1` | Ticks between checks for player movement (1–200). Surveyor sends positions only when someone moved. |
| `hideHiddenPlayers` | `true` | Do not send the positions of players in spectator mode or with invisibility. Other players see their last visible position. |

The sharing values are `SERVER`, `GROUP`, `SOLO` and `NONE`. See [Playing Together](Playing-Together).

## `[builtins]`

| Option | Default | What it does |
|---|---|---|
| `poiLandmarks` | `["minecraft:lodestone"]` | Points of interest that automatically become landmarks. |
| `poiLandmarksFromWorldgen` | `true` | Also add landmarks for the ones that world generation places (for example lodestones in structures), after their chunk loads. `false` skips them. |
| `netherPortalLandmarks` | `true` | One landmark for each nether portal. |
| `playerDeathWaypoints` | `true` | A waypoint where you died. |
| `allowedBlockEntities` | `["minecraft:banner"]` | Blocks whose data (for example the pattern of a banner) stays on block waypoints. |
| `recordFromMapItems` | `true` | Allow copying a paper map into your exploration. See [Vanilla Maps](Vanilla-Maps). |
| `recordToMapItems` | `EXPLORER` | Copying your exploration onto a paper map: `EXPLORER` (explorer-map look), `COLOR` (full colour) or `NONE`. |
| `disabledRecordIcons` | player and frame icons | Map icons that do not become waypoints when you copy from a paper map. |
