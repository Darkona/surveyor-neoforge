# Configuration

Surveyor's options live in `config/surveyor.toml`; you can also edit them in game from the mod list (Mods → Surveyor → Config). On a server, the server's file decides what is shared. Most changes need a restart.

## Systems

| Option | Default | What it does |
|---|---|---|
| `terrain` | `DYNAMIC` | Terrain recording. |
| `structures` | `DYNAMIC` | Structure recording. |
| `landmarks` | `DYNAMIC` | Waypoints and landmarks. |

Each takes `DISABLED` (off), `FROZEN` (existing data is loaded but never changes), `DYNAMIC` (on when a map mod asks for it, and always on servers) or `ENABLED` (always on).

## General

| Option | Default | What it does |
|---|---|---|
| `discoveryMessages` | `false` | Show "Discovered <structure> at <position>" above the hotbar when you find a structure. |
| `debugCommands` | `false` | Enable the `raw` commands. |
| `lazyClientUpdating` | `true` | On multiplayer clients, only re-record a chunk when the amount of air in it changed. Faster; rarely misses a change. |
| `asyncChunkSummaries` | `true` | Servers record loaded chunks on a background thread instead of the server thread. Same map; turn it off if you suspect it. |
| `forceUpdateLandmarks` | `true` | Always resend landmarks when joining, instead of trusting what the client already has. |

## `[networking]`

| Option | Default | What it does |
|---|---|---|
| `globalSharing` | `true` | Everyone shares one map. Set `false` on public servers; players then form groups with `/surveyor share`. |
| `terrain` | `GROUP` | How far explored terrain is shared. |
| `structures` | `GROUP` | How far discovered structures are shared (`NONE`: clients never see structures). |
| `landmarks` | `GROUP` | How far server-wide landmarks are shared. |
| `waypoints` | `GROUP` | How far player waypoints are shared. `SERVER`: everyone sees everyone's (they can't edit them). `NONE`: waypoints stay on your computer. |
| `positions` | `SERVER` | Who sees where players are. |
| `terrainTicks` | `20` | Ticks between terrain batches sent to a joining player (1–200). Lower is faster, busier. |
| `positionTicks` | `1` | Ticks between checks for player movement (1–200). Positions are only sent when someone moved. |
| `hideHiddenPlayers` | `true` | Don't send the positions of players in spectator mode or with invisibility; others see their last visible position. |

Sharing values: `SERVER`, `GROUP`, `SOLO`, `NONE` — see [Playing Together](Playing-Together).

## `[builtins]`

| Option | Default | What it does |
|---|---|---|
| `poiLandmarks` | `["minecraft:lodestone"]` | Points of interest that automatically become landmarks. |
| `poiLandmarksFromWorldgen` | `true` | Also add landmarks for those placed by world generation (e.g. lodestones in structures), once their chunk has loaded. `false` skips them. |
| `netherPortalLandmarks` | `true` | One landmark per nether portal. |
| `playerDeathWaypoints` | `true` | A waypoint where you died. |
| `allowedBlockEntities` | `["minecraft:banner"]` | Blocks whose data (e.g. a banner's pattern) is kept on block waypoints. |
| `recordFromMapItems` | `true` | Allow copying a paper map into your exploration — see [Vanilla Maps](Vanilla-Maps). |
| `recordToMapItems` | `EXPLORER` | Copying your exploration onto a paper map: `EXPLORER` (explorer-map look), `COLOR` (full colour) or `NONE`. |
| `disabledRecordIcons` | player and frame icons | Map icons that are not turned into waypoints when copying from a paper map. |
