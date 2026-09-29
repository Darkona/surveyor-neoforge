# Installation

Surveyor runs on **Minecraft 1.21.1** with **NeoForge 21.1**. It needs nothing else: no Sinytra Connector and no Fabric API.

| Setup | Surveyor on | Map mod on |
|---|---|---|
| Singleplayer | your game | your game |
| Multiplayer, shared maps | the server **and** every player | each player who wants a map |
| Multiplayer, server without Surveyor | your game | your game. You map on your own and nothing is shared |

- Players without Surveyor can still join a server that has it. They do not receive map data.
- Surveyor alone shows nothing. Install a map mod that uses it, such as [Antique Atlas](https://github.com/Darkona/antique-atlas-neoforge).
- The first time you play a world with a map mod, Surveyor imports the Xaero's Minimap waypoints of that world.

## What Surveyor records

- **Terrain**: for every chunk you saw, the ground, water depth, biome and light on each layer of the chunk.
- **Structures**: villages, temples, strongholds and others, after you stand inside one or look at it.
- **Landmarks**: waypoints that players place, server-wide landmarks that operators place, and automatic ones for nether portals, lodestones and the places where players died.

Surveyor keeps one exploration per player. On a server, it also shares that exploration with other players. See [Playing Together](Playing-Together).
