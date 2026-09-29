# Changelog

Changes of this NeoForge fork, by date and feature. Links such as `surveyor#150` go to the issues of the original projects, [Surveyor](https://github.com/sisby-folk/surveyor/issues) and [Antique Atlas](https://github.com/sisby-folk/antique-atlas/issues); the bugs they describe also existed here until the date listed. "Closes" means this fork fully handles that bug or feature request. "Partly addresses", "Hardens against" and "Addresses" mean it handles only the part the entry describes, or removes a likely cause of a crash whose report is not complete enough to confirm the fix. "Answers" means the issue is a question, answered in the wiki.

## 2026-09-29

### Troubleshooting
- New option `debug` (or the JVM argument `-Dsurveyor.debug=true`): logs what Surveyor does to `latest.log` with the prefix `[Surveyor/debug]`, for bug reports and test servers. Rare events get a line each (landmarks, POIs, structure discoveries, share groups, the world id and map folder); busy activity (chunk summaries, region files, packets, positions) is summed up every 100 ticks. Off, it costs nothing.

### Tests
- New coverage: chunk scan allocation, a config-screen label for every option, event-driven position sync, the `poiLandmarksFromWorldgen` option, and debug mode.

## 2026-09-28

### NeoForge port
- Surveyor 1.2.4 runs natively on NeoForge 21.1 (Minecraft 1.21.1): no Sinytra Connector or Fabric API. It replaces the original's Connector build. Same features, mod id, save format and network format as the original.
- The configuration file uses NeoForge's format. The `recordIcons` table became the `disabledRecordIcons` list.

### Configuration
- The mod list's Config button opens a config screen with every option; it used to be disabled. The same bug as [antique-atlas#390](https://github.com/sisby-folk/antique-atlas/issues/390) and [antique-atlas#372](https://github.com/sisby-folk/antique-atlas/issues/372), which Antique Atlas for NeoForge closes on its side.

### Saving
- Saving can no longer lose map data: a server stopping right after an autosave, a crash while writing a file, or two overlapping saves of one file could drop or corrupt a region.
- A region's terrain is only unloaded once all of its chunks are; only a quarter of the region was checked before.
- Saving a region no longer scrambles terrain data when a value is above 32767.

### Landmarks and waypoints
- Dying to a named, enchanted weapon (Apotheosis, PvP) no longer crashes the server tick, nor the client when it disconnects. Landmarks are saved and synced with registries, and the grave no longer keeps the weapon's item tooltip. Closes [surveyor#150](https://github.com/sisby-folk/surveyor/issues/150) and [surveyor#144](https://github.com/sisby-folk/surveyor/issues/144).
- A landmark that can't be read or written is left out and logged, instead of failing the whole save or packet. One bad marker used to disconnect a player on every rejoin. The network format is unchanged. Closes [surveyor#149](https://github.com/sisby-folk/surveyor/issues/149).
- Lodestones and other tracked POIs placed by world generation no longer make the server finish generating their chunk on the spot (stalls, worse with C2ME). Their landmark is added once the chunk has fully loaded. New option `builtins.poiLandmarksFromWorldgen` to skip them entirely. Addresses [surveyor#136](https://github.com/sisby-folk/surveyor/issues/136). Partly addresses [surveyor#145](https://github.com/sisby-folk/surveyor/issues/145) and [surveyor#147](https://github.com/sisby-folk/surveyor/issues/147): the stall, not the deadlock they report on 26.1, which can't happen on 1.21.1.
- A landmark saved as both present and removed is no longer deleted from the clients that know it.
- Removing landmarks while the landmark system is frozen no longer throws an error.

### Terrain
- Biomes that a mod doesn't register (End's Phantasm) and ids past the registry size no longer crash the chunk scan every time the chunk loads; they are recorded as the void biome or the default block. Closes [surveyor#121](https://github.com/sisby-folk/surveyor/issues/121). Hardens against [surveyor#135](https://github.com/sisby-folk/surveyor/issues/135), [surveyor#141](https://github.com/sisby-folk/surveyor/issues/141) and [surveyor#142](https://github.com/sisby-folk/surveyor/issues/142).
- Chunks you visit are recorded as explored even when the terrain system is turned off.
- After joining a server or opening a world whose dimension has a different height than the one played before in that game session, chunks there are recorded again instead of failing on every chunk load.
- Datapack dimensions that define their dimension type inline are recorded instead of failing on every chunk load.

### Structures
- A structure missing from the registry is skipped and logged instead of crashing. Hardens against [surveyor#142](https://github.com/sisby-folk/surveyor/issues/142).
- A structure missing from the registry no longer crashes the server when a player walks into or looks at it.
- Mods that place structures through their own world-generation level no longer crash world generation.

### Multiplayer and threads
- Singleplayer is safe from races between the game and its built-in server, which could crash the game or drop freshly recorded chunks.
- Friends are no longer shown as offline after every autosave.
- Map mods are always notified on the game's own thread.
- A connection without Surveyor data, such as a ReplayMod playback, no longer crashes the client. Closes [surveyor#151](https://github.com/sisby-folk/surveyor/issues/151).
- Joining or leaving a share group drops the old group's explored areas and structures; they used to stay on the map.
- A player is no longer disconnected when the server has a dimension their client didn't list at login, such as a dimension created while playing.
- Singleplayer: map mods reading landmarks and terrain palettes while the built-in server changes them can no longer crash.
- Maps of servers behind a proxy (Velocity, BungeeCord) that share a seed no longer overwrite each other. The server keeps a world id in `data/surveyor/world_id.dat` and sends it when a player joins; the client keeps that server's map in `data/surveyor/<world id>/`, copied once from the old seed folder, which is left in place. Servers and clients without this keep using the seed folder, and the network stays compatible with the original. Closes [surveyor#131](https://github.com/sisby-folk/surveyor/issues/131).

### Player positions
- Players in spectator mode or with invisibility no longer show up on other players' maps; they keep their last visible position. Option `networking.hideHiddenPlayers`. Closes [surveyor#106](https://github.com/sisby-folk/surveyor/issues/106).
- The wiki explains who sees whom on the map: seeing players anywhere in the world is the `networking.positions` option. Answers [antique-atlas#393](https://github.com/sisby-folk/antique-atlas/issues/393).

### For map mods
- New client event `ExplorationReset`, fired when the player's share group changes and the shared exploration is replaced: map mods can drop areas the new group hasn't explored. With the matching change in Antique Atlas for NeoForge, closes [antique-atlas#343](https://github.com/sisby-folk/antique-atlas/issues/343).

### Performance
- Recording a chunk no longer creates objects for every block it scans.
- Map data is only packed for sending when a player will receive it.
- Player positions are only sent when someone moves or turns.
- The structure-discovery raycast only runs when your view changes.
- A group's shared map is a live view instead of a copy of every member's exploration.
- The region unload check, run on every chunk unload, no longer creates hundreds of objects.
- Working out who receives a sent chunk no longer builds sets of players for every chunk.
- Loaded chunks are recorded on a background thread instead of the server thread, which removes the tick spikes of several players exploring at once. The recorded map is the same. Option `asyncChunkSummaries` turns it off. Closes [surveyor#148](https://github.com/sisby-folk/surveyor/issues/148).
- Structures are no longer all kept in memory from startup: a structure region is read when something asks for it, and dropped again once none of its chunks is loaded. Same files as before. Closes [surveyor#115](https://github.com/sisby-folk/surveyor/issues/115).

### Tests
- Regression tests for region coordinates and encodings, packed terrain values, the terrain palette, landmark saving and packets, bitset helpers, save ordering, group exploration, and source guards for thread and save rules.
- Tests run inside FML, so they can use items, components and registries.
