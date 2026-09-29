# Surveyor Map Framework for NeoForge

> [!IMPORTANT]
> This is an unofficial fork. It ports [Surveyor Map Framework](https://github.com/sisby-folk/surveyor) by **Sisby folk** ([Modrinth](https://modrinth.com/mod/surveyor)) to NeoForge. The fork has its own maintainer and has no affiliation with the original authors, who get all the credit for the mod. Report problems with this version [here](https://github.com/Darkona/surveyor-neoforge/issues), not to the original project.

Surveyor records the world you explore and shares it. Map mods draw it.

Surveyor is the shared backend of cooperative map mods such as [Antique Atlas](https://github.com/Darkona/antique-atlas-neoforge). It remembers the terrain you explored, the structures you found and every waypoint that you or your friends placed. It keeps all of this in your save and syncs it between the players on a server, so a map mod only has to draw it.

Surveyor has no map screen. Install it together with a map mod that uses it.

![A map mod drawing Surveyor's data](wiki/images/surveyor-map-frontend.png)
*Antique Atlas draws the terrain, waypoints and players that Surveyor recorded.*

Minecraft **1.21.1**, **NeoForge 21.1**. Surveyor runs natively, without Sinytra Connector or Fabric API.

---

## What it does

**For players**
- **Terrain**: records a summary of the ground, water and biomes of every chunk you see, as you move.
- **Structures**: records a structure after you stand in it or look at it.
- **Waypoints**: keeps your waypoints, your friends' waypoints and server-wide landmarks such as spawn or shops.
- **Automatic waypoints**: adds them for nether portals, lodestones and the places where you died.
- **Any map mod**: the data belongs to Surveyor, not to the map, so you can change map mods at any time and lose nothing.
- **Xaero's Minimap**: imports your existing waypoints from a Xaero's Minimap save.

**On a server**
- **Cooperative maps**: by default, everyone shares one map of explored areas and waypoints. You can also put players in groups.
- **Friends on the map**: you see your friends even when they are far away or in another dimension.
- **Your map follows you**: change computers or install the game again, and the server sends your map back.
- **Server landmarks**: operators can place landmarks for everyone.
- **Vanilla maps**: sneak and use a filled map on a cartography table to copy exploration into the map or out of it.

![Surveyor's exploration summary](wiki/images/surveyor-summary.png)
*`/surveyor` shows how much of the world you mapped.*

---

## Installation

1. Install NeoForge 21.1 for Minecraft 1.21.1.
2. Put the Surveyor jar in `mods/`. For multiplayer, put it on the **server and every client**. For singleplayer, your game is enough.
3. Add a map mod that uses Surveyor, for example [Antique Atlas](https://github.com/Darkona/antique-atlas-neoforge).

On a public server, set `globalSharing = false` in `config/surveyor.toml`. Players then share maps in groups instead of all together.

---

## Documentation

The [wiki](https://github.com/Darkona/surveyor-neoforge/wiki) has the rest: [installation](https://github.com/Darkona/surveyor-neoforge/wiki/Installation) · [playing together](https://github.com/Darkona/surveyor-neoforge/wiki/Playing-Together) · [commands](https://github.com/Darkona/surveyor-neoforge/wiki/Commands) · [configuration](https://github.com/Darkona/surveyor-neoforge/wiki/Configuration) · [vanilla maps](https://github.com/Darkona/surveyor-neoforge/wiki/Vanilla-Maps) · [data and troubleshooting](https://github.com/Darkona/surveyor-neoforge/wiki/Data-and-Troubleshooting) · [frontend development](https://github.com/Darkona/surveyor-neoforge/wiki/Frontend-Development)

---

## Differences from the original

This fork keeps the features, mod id, save format and network format of Surveyor. Existing map data and addons such as Antique Atlas keep working.

**Platform**
- Runs natively on NeoForge 21.1, without Sinytra Connector or Fabric API. It replaces the Connector build of the original. Do not install both.
- The configuration file uses the NeoForge format. The `recordIcons` table became the `disabledRecordIcons` list. Icons that are not in the list are recorded, as before.
- The Config button in the mod list opens a config screen with every option.

**Fixes**

These are bugs of the original that this fork fixes. When a fix has an issue in the original project, its commit gives the issue number.

- Saving can no longer lose map data. Before, three things could drop or corrupt a region: a server that stopped right after an autosave, a crash in the middle of a file write, or two overlapping saves of the same file.
- Singleplayer is safe from races between the game and its built-in server. These races could crash the game or drop chunks that were just recorded.
- Friends no longer show as offline after every autosave.
- Surveyor records the chunks you visit as explored even when the terrain system is off, for setups that only use waypoints.
- Surveyor always notifies map mods on the game's own thread, so every addon is safe in singleplayer.
- A region's terrain is unloaded only after all of its chunks are. Before, the check looked at only a quarter of the region. A region could then be saved and dropped while you were still in it, and read back from disk.
- In rare cases, saving a region scrambled part of its terrain data: a value above 32767 overwrote its neighbour. This is fixed.
- A death by a named, enchanted weapon no longer crashes the server or the client. A landmark that Surveyor cannot read or write is skipped and logged, and the rest of the save or packet goes through.
- The chunk scan no longer crashes on biomes that a mod does not register, on ids past the registry size, or on datapack dimensions with an inline dimension type. Surveyor records a dimension again when its height is different from the one you played before in the same session.
- Structures missing from the registry no longer crash the server. Mods that place structures through their own world-generation level no longer crash world generation.
- A connection without Surveyor data, such as a ReplayMod playback, no longer crashes the client. The server no longer disconnects a player when it has a dimension that their client did not list at login.
- When you join or leave a share group, the map drops the explored areas and structures of the old group. Before, they stayed on the map.
- A landmark saved as both present and removed is no longer deleted from the clients that know it. Removing landmarks while the landmark system is frozen no longer throws.
- In singleplayer, map mods can read landmarks and terrain palettes while the built-in server changes them, without crashing.
- Servers behind a proxy that share a seed no longer overwrite each other's maps. The server sends a world id, and the client keeps the map of each server in its own folder, copied one time from the old seed folder. Servers or clients without the world id keep the seed folder.

**New features**
- **Worldgen POIs**: lodestones and other POIs that world generation places no longer make the server finish their chunk on the spot. That caused stalls, worse with C2ME. Surveyor adds their landmark after the chunk loads. The option `builtins.poiLandmarksFromWorldgen` skips them completely.
- **Hidden players**: players in spectator mode or with invisibility no longer show on the maps of other players. Their last visible position stays. Option `networking.hideHiddenPlayers`.
- **Debug mode**: `debug`, or `-Dsurveyor.debug=true`, logs what Surveyor does, for bug reports and test servers.

**For map mods**
- The new client event `ExplorationReset` fires when the player's share group changes and the shared exploration is replaced. A map can then drop areas that the new group did not explore.

**Performance**
- Recording a chunk no longer creates objects for every block that it scans.
- Surveyor packs map data for sending only when a player will receive it. Servers with one player no longer do work per chunk.
- Surveyor sends player positions only when someone moves or turns. Before, it sent them every tick for every player.
- The raycast that discovers the structures you look at runs only when your view changes.
- A request for a group's shared map no longer copies the exploration of every member.
- The check that decides if a region can be unloaded runs on every chunk unload. It no longer creates hundreds of objects.
- Finding the players that receive a chunk no longer builds sets of players for every chunk.
- A background thread records loaded chunks instead of the server thread, with the same result. Option `asyncChunkSummaries`.
- Surveyor reads structure regions when something needs them and drops them from memory when none of their chunks is loaded. Before, all of them stayed in memory from startup.

---

## Credits and license

Surveyor Map Framework is by **Sisby folk**, with contributions by Ampflower, falkreon, jaskarth and Garden System. The original project is [sisby-folk/surveyor](https://github.com/sisby-folk/surveyor). This repository is an unofficial NeoForge fork of it.

Licensed under the **GNU LGPL v3.0 or later**. See [LICENSE](LICENSE). Addons adapted from Surveyor should use a compatible license.
