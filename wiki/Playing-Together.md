# Playing Together

When the server has Surveyor, the map is **cooperative**.

## One shared map (default)

With `globalSharing = true` (the default), all players are in one big group:

- Terrain that anyone explores shows on the map of everyone.
- Structures and waypoints are shared.
- Everyone sees where everyone else is, also players in other dimensions and, faded, players who are offline.

This works well for friends who play together. **On a public server, turn it off** with `globalSharing = false` in `config/surveyor.toml` on the server.

## Groups

With `globalSharing = false`, each player starts alone:

- `/surveyor share <player>`: join the group of that player, or bring them into yours.
- `/surveyor unshare`: leave your group.

From then on, the whole group shares everything that a member explored.

## What is shared

The `[networking]` section of the configuration sets how far each kind of data travels:

| Value | Meaning |
|---|---|
| `SERVER` | everyone on the server gets it |
| `GROUP` | your group gets it (default for map data) |
| `SOLO` | only you. The server keeps it as a backup |
| `NONE` | never sent |

Each of these has its own setting: `terrain`, `structures`, `landmarks` (server-wide landmarks), `waypoints` (made by players) and `positions` (where players are). See [Configuration](Configuration).

## Who sees whom on the map

The `positions` option sets where players show:

- `SERVER` (default): everyone sees every player, anywhere in the world.
- `GROUP`: you see only the players in your group. Use it with `globalSharing = false`, so that players make groups with `/surveyor share`.
- `SOLO` or `NONE`: nobody sees other players.

While `hideHiddenPlayers = true` (default), other players never see players in spectator mode or with invisibility. Their friends see where they were last visible.

## Your map follows you

The server keeps the exploration of each player. If you change computers or install the game again, the server sends your map back when you join.
