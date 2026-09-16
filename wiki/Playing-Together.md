# Playing Together

With Surveyor on the server, the map is **cooperative**.

## One shared map (default)

With `globalSharing = true` (the default), every player is in one big group:

- terrain explored by anyone appears on everyone's map;
- structures and waypoints are shared;
- everyone sees where everyone else is — also players in other dimensions and, faded, players who are offline.

This suits friends playing together. **On a public server, turn it off** (`globalSharing = false` in `config/surveyor.toml` on the server).

## Groups

With `globalSharing = false`, each player starts alone:

- `/surveyor share <player>` — join that player's group (or bring them into yours);
- `/surveyor unshare` — leave your group.

Everything a group member explored is shared with the whole group from then on.

## Choosing what is shared

The `[networking]` section of the configuration decides, for each kind of data, how far it travels:

| Value | Meaning |
|---|---|
| `SERVER` | everyone on the server gets it |
| `GROUP` | your group gets it (default for map data) |
| `SOLO` | only you — kept on the server as a backup |
| `NONE` | never sent |

This is set separately for `terrain`, `structures`, `landmarks` (server-wide landmarks), `waypoints` (player-made) and `positions` (where players are). See [Configuration](Configuration).

## Your map follows you

The server keeps each player's exploration. Change computers or reinstall the game, and your map is sent back to you when you join.
