# Changelog

Changes of this NeoForge fork. Issue numbers such as `surveyor#150` refer to the
[original project's issues](https://github.com/sisby-folk/surveyor/issues); the bugs they describe also existed in this
fork until the listed commit.

## Unreleased

### Fixed
- Dying to a named, enchanted weapon (Apotheosis, PvP) no longer crashes the server tick, nor the client when it
  disconnects. Afterwards, landmarks are saved normally again. Landmarks are now saved and synced with registries, and
  the grave no longer keeps the weapon's item tooltip. (surveyor#150, surveyor#144 — `f7182a9`)
- A landmark that can't be read or written is left out and logged, instead of failing the whole save or packet. One
  bad marker used to disconnect a player on every rejoin. The network format is unchanged. (surveyor#149 — `f7182a9`)

### Development
- Tests run inside FML, so they can use items, components and registries. (`b7772e7`)

## 1.2.4+1.21-neoforge

First release of the NeoForge port of Surveyor 1.2.4. Same features, mod id, save format and network format as the
original.

### Platform
- Runs natively on NeoForge 21.1 (Minecraft 1.21.1): no Sinytra Connector or Fabric API. It replaces the original's
  Connector build. (`dbd3a95`)
- The configuration file uses NeoForge's format. The `recordIcons` table became the `disabledRecordIcons` list.

### Fixed (bugs of the original)
- Saving can no longer lose map data: a server stopping right after an autosave, a crash while writing a file, or two
  overlapping saves of one file could drop or corrupt a region. (`dbd3a95`)
- Singleplayer is safe from races between the game and its built-in server, which could crash the game or drop freshly
  recorded chunks. (`dbd3a95`)
- Friends are no longer shown as offline after every autosave. (`dbd3a95`)
- Chunks you visit are recorded as explored even when the terrain system is turned off. (`dbd3a95`)
- Map mods are always notified on the game's own thread. (`dbd3a95`)
- A region's terrain is only unloaded once all of its chunks are; only a quarter of the region was checked before.
  (`a2d75a8`)
- Saving a region no longer scrambles terrain data when a value is above 32767. (`a2d75a8`)

### Performance
- Recording a chunk no longer creates objects for every block it scans. (`dbd3a95`)
- Map data is only packed for sending when a player will receive it. (`dbd3a95`)
- Player positions are only sent when someone moves or turns. (`dbd3a95`)
- The structure-discovery raycast only runs when your view changes. (`dbd3a95`)
- A group's shared map is a live view instead of a copy of every member's exploration. (`dbd3a95`)
- The region unload check, run on every chunk unload, no longer creates hundreds of objects. (`a2d75a8`)

### Development
- Regression tests: region coordinates and encodings, packed terrain values, bitset helpers, save ordering, group
  exploration, and source guards for thread and save rules. (`dbd3a95`, `a2d75a8`)
