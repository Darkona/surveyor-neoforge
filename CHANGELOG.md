# Changelog

All changes of this NeoForge fork. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and each change has the date it was made.

Links such as `surveyor#150` go to the issues of the original projects, [Surveyor](https://github.com/sisby-folk/surveyor/issues) and [Antique Atlas](https://github.com/sisby-folk/antique-atlas/issues). The bugs they describe also existed in this fork until the date of the entry.

"Closes" means that this fork fully handles the bug or feature request. "Partly addresses", "Hardens against" and "Addresses" mean that it handles only the part that the entry describes, or that it removes a likely cause of a crash whose report does not have enough detail to confirm the fix. "Answers" means that the issue is a question, and the wiki answers it.

## [1.2.4+1.21-neoforge] - 2026-09-29

### Added
- 2026-09-29: New option `debug`, or the JVM argument `-Dsurveyor.debug=true`. It logs what Surveyor does to `latest.log` with the prefix `[Surveyor/debug]`, for bug reports and test servers. Rare events get one line each: landmarks, POIs, structure discoveries, share groups, the world id and the map folder. Busy activity (chunk summaries, region files, packets, positions) is added up every 100 ticks. When it is off, it costs nothing.
- 2026-09-29: Test coverage for debug mode.
- 2026-09-26: Test coverage for chunk scan allocation.
- 2026-09-26: Test coverage for a config-screen label for every option, event-driven position sync and the `poiLandmarksFromWorldgen` option.
- 2026-09-21: Players in spectator mode or with invisibility no longer show on the maps of other players. Their last visible position stays. Option `networking.hideHiddenPlayers`. Closes [surveyor#106](https://github.com/sisby-folk/surveyor/issues/106).
- 2026-09-21: The wiki explains who sees whom on the map. The `networking.positions` option shows players anywhere in the world. Answers [antique-atlas#393](https://github.com/sisby-folk/antique-atlas/issues/393).
- 2026-09-21: New client event `ExplorationReset` for map mods. It fires when the share group of the player changes and the shared exploration is replaced, so map mods can drop areas that the new group did not explore. Together with the matching change in Antique Atlas for NeoForge, closes [antique-atlas#343](https://github.com/sisby-folk/antique-atlas/issues/343).
- 2026-09-19: Regression tests for landmark saving and packets.
- 2026-09-19: Regression tests for the terrain palette.
- 2026-09-18: Regression tests for region coordinates and encodings, packed terrain values and bitset helpers.
- 2026-09-18: Tests run inside FML, so they can use items, components and registries.
- 2026-09-16: Surveyor 1.2.4 runs natively on NeoForge 21.1 (Minecraft 1.21.1), without Sinytra Connector or Fabric API. It replaces the Connector build of the original. Features, mod id, save format and network format are the same as in the original.
- 2026-09-16: Regression tests for save ordering and group exploration, and source guards for thread and save rules.

### Changed
- 2026-09-26: Structures no longer all stay in memory from startup. Surveyor reads a structure region when something asks for it, and drops it again when none of its chunks is loaded. The files are the same as before. Closes [surveyor#115](https://github.com/sisby-folk/surveyor/issues/115).
- 2026-09-25: A background thread records loaded chunks instead of the server thread. This removes the tick spikes when several players explore at the same time. The recorded map is the same. The option `asyncChunkSummaries` turns it off. Closes [surveyor#148](https://github.com/sisby-folk/surveyor/issues/148).
- 2026-09-24: Finding the players that receive a sent chunk no longer builds sets of players for every chunk.
- 2026-09-18: The region unload check runs on every chunk unload. It no longer creates hundreds of objects.
- 2026-09-16: The configuration file uses the NeoForge format. The `recordIcons` table became the `disabledRecordIcons` list.
- 2026-09-16: Recording a chunk no longer creates objects for every block that it scans.
- 2026-09-16: Map data is packed for sending only when a player will receive it.
- 2026-09-16: Player positions are sent only when someone moves or turns.
- 2026-09-16: The structure-discovery raycast runs only when your view changes.
- 2026-09-16: The shared map of a group is a live view instead of a copy of the exploration of every member.

### Fixed
- 2026-09-24: Servers behind a proxy (Velocity, BungeeCord) that share a seed no longer overwrite each other's maps. The server keeps a world id in `data/surveyor/world_id.dat` and sends it when a player joins. The client keeps the map of that server in `data/surveyor/<world id>/`, copied one time from the old seed folder, which stays in place. Servers and clients without the world id keep the seed folder, and the network stays compatible with the original. Closes [surveyor#131](https://github.com/sisby-folk/surveyor/issues/131).
- 2026-09-23: In singleplayer, map mods can read landmarks while the built-in server changes them, without crashing.
- 2026-09-23: In singleplayer, map mods can read terrain palettes while the built-in server changes them, without crashing.
- 2026-09-22: Surveyor records chunks again after you join a server or open a world whose dimension has a different height than the one you played before in the same game session. Before, every chunk load failed there.
- 2026-09-22: Datapack dimensions that define their dimension type inline are recorded. Before, every chunk load failed there.
- 2026-09-22: A landmark saved as both present and removed is no longer deleted from the clients that know it.
- 2026-09-22: Removing landmarks while the landmark system is frozen no longer throws an error.
- 2026-09-21: When you join or leave a share group, the map drops the explored areas and structures of the old group. Before, they stayed on the map.
- 2026-09-21: A structure missing from the registry no longer crashes the server when a player walks into it or looks at it.
- 2026-09-21: The server no longer disconnects a player when it has a dimension that their client did not list at login, such as a dimension created during play.
- 2026-09-21: Mods that place structures through their own world-generation level no longer crash world generation.
- 2026-09-20: Lodestones and other tracked POIs that world generation places no longer make the server finish their chunk on the spot. That caused stalls, worse with C2ME. Surveyor adds their landmark after the chunk fully loads. The new option `builtins.poiLandmarksFromWorldgen` skips them completely. Addresses [surveyor#136](https://github.com/sisby-folk/surveyor/issues/136). Partly addresses [surveyor#145](https://github.com/sisby-folk/surveyor/issues/145) and [surveyor#147](https://github.com/sisby-folk/surveyor/issues/147): it fixes the stall, but not the deadlock that they report on 26.1, which cannot happen on 1.21.1.
- 2026-09-20: The Config button in the mod list opens a config screen with every option. Before, the button was disabled. This is the same bug as [antique-atlas#390](https://github.com/sisby-folk/antique-atlas/issues/390) and [antique-atlas#372](https://github.com/sisby-folk/antique-atlas/issues/372), which Antique Atlas for NeoForge closes on its side.
- 2026-09-19: A death by a named, enchanted weapon (Apotheosis, PvP) no longer crashes the server tick, or the client when it disconnects. Landmarks are saved and synced with registries, and the grave no longer keeps the item tooltip of the weapon. Closes [surveyor#150](https://github.com/sisby-folk/surveyor/issues/150) and [surveyor#144](https://github.com/sisby-folk/surveyor/issues/144).
- 2026-09-19: A landmark that Surveyor cannot read or write is left out and logged, and the rest of the save or packet goes through. Before, one bad marker disconnected a player on every rejoin. The network format is the same. Closes [surveyor#149](https://github.com/sisby-folk/surveyor/issues/149).
- 2026-09-19: Biomes that a mod does not register (End's Phantasm) and ids past the registry size no longer crash the chunk scan every time the chunk loads. Surveyor records them as the void biome or the default block. Closes [surveyor#121](https://github.com/sisby-folk/surveyor/issues/121). Hardens against [surveyor#135](https://github.com/sisby-folk/surveyor/issues/135), [surveyor#141](https://github.com/sisby-folk/surveyor/issues/141) and [surveyor#142](https://github.com/sisby-folk/surveyor/issues/142).
- 2026-09-19: A structure missing from the registry is skipped and logged instead of a crash. Hardens against [surveyor#142](https://github.com/sisby-folk/surveyor/issues/142).
- 2026-09-19: A connection without Surveyor data, such as a ReplayMod playback, no longer crashes the client. Closes [surveyor#151](https://github.com/sisby-folk/surveyor/issues/151).
- 2026-09-18: The terrain of a region is unloaded only after all of its chunks are. Before, the check looked at only a quarter of the region.
- 2026-09-18: Saving a region no longer scrambles terrain data when a value is above 32767.
- 2026-09-16: Saving can no longer lose map data. Before, a server that stopped right after an autosave, a crash during a file write, or two overlapping saves of one file could drop or corrupt a region.
- 2026-09-16: Singleplayer is safe from races between the game and its built-in server. These races could crash the game or drop chunks that were just recorded.
- 2026-09-16: Friends no longer show as offline after every autosave.
- 2026-09-16: Chunks you visit are recorded as explored even when the terrain system is off.
- 2026-09-16: Map mods are always notified on the game's own thread.
