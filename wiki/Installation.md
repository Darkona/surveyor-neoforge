# Installation

Surveyor runs on **Minecraft 1.21.1** with **NeoForge 21.1**. It needs nothing else — no Sinytra Connector, no Fabric API.

| Setup | Surveyor on | Map mod on |
|---|---|---|
| Singleplayer | your game | your game |
| Multiplayer, shared maps | the server **and** every player | each player who wants a map |
| Multiplayer, server without Surveyor | your game | your game — you map on your own, nothing is shared |

- Players without Surveyor can still join a server that has it; they just don't receive map data.
- Surveyor on its own does nothing visible: install a map mod that uses it, such as [Antique Atlas](https://github.com/Darkona/antique-atlas-neoforge).
- When you first play with a map mod, existing Xaero's Minimap waypoints for that world are imported automatically.

## What Surveyor records

- **Terrain** — for every chunk you have seen: the ground, water depth, biome and light on each layer of the chunk.
- **Structures** — villages, temples, strongholds and so on, once you have stood inside one or looked at it.
- **Landmarks** — waypoints placed by players, server-wide landmarks placed by operators, and automatic ones: nether portals, lodestones and the places where players died.

A single exploration is kept per player. On a server it is also shared with other players — see [Playing Together](Playing-Together).
