# Commands

Usually a map mod is all you need. These commands do the rest, and they work also without a map mod.

`<dimension>` is a dimension id such as `minecraft:overworld`. `<id>` is a waypoint or landmark id. The list commands show it.

## Everyone

| Command | What it does |
|---|---|
| `/surveyor` | Your exploration summary: chunks explored, structures and landmarks found, waypoints made. |
| `/surveyor share <player>` | Join a sharing group with that player. Only when `globalSharing = false`. |
| `/surveyor unshare` | Leave your sharing group. Only when `globalSharing = false`. |
| `/waypoints` | List the waypoints that you can see. |
| `/waypoints new block <pos> [<dimension>]` | Make a waypoint from the block at `<pos>`, with the name and colour of the block. |
| `/waypoints view <dimension> <id>` | Show the details of one waypoint. |
| `/waypoints remove <dimension> <id>` | Delete one of your waypoints. |
| `/landmarks` | List the server-wide landmarks. |
| `/landmarks view <dimension> <id>` | Show the details of one landmark. |

## Operators

| Command | What it does |
|---|---|
| `/landmarks new block <pos> [<dimension>]` | Make a server-wide landmark from a block: spawn, shops, warp hubs and similar. |
| `/landmarks new id <dimension> <id> <pos> <icon> <name> <lore>` | Make a landmark with its own id, item icon, name and description. |
| `/landmarks remove <dimension> <id>` | Delete a landmark. |
| `/landmarks append <id> surveyor:color <color>` / `/landmarks trim <id> <component>` | Add or remove a detail of a landmark, for example its colour. |
| `/waypoints new block <pos> <dimension> <owner>` | Make a waypoint for another player (by UUID). The same `append` / `trim` work for waypoints. |

With `debugCommands = true`, `/waypoints raw` and `/landmarks raw` print the raw data of a waypoint.

## Servers without Surveyor

On a server without Surveyor, you still get your own waypoints with the client-side command `/waypointsc`: `/waypointsc`, `/waypointsc new block <pos>`, `/waypointsc view <id>`, `/waypointsc remove <id>`.
