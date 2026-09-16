# Commands

Most of the time a map mod is all you need. These commands cover the rest — and work even without a map mod.

`<dimension>` is a dimension id such as `minecraft:overworld`; `<id>` is a waypoint or landmark id, shown by the list commands.

## Everyone

| Command | What it does |
|---|---|
| `/surveyor` | Your exploration summary: chunks explored, structures and landmarks found, waypoints made. |
| `/surveyor share <player>` | Join a sharing group with that player. Only when `globalSharing = false`. |
| `/surveyor unshare` | Leave your sharing group. Only when `globalSharing = false`. |
| `/waypoints` | List the waypoints you can see. |
| `/waypoints new block <pos> [<dimension>]` | Make a waypoint from the block at `<pos>` (named and coloured after the block). |
| `/waypoints view <dimension> <id>` | Show one waypoint in detail. |
| `/waypoints remove <dimension> <id>` | Delete one of your waypoints. |
| `/landmarks` | List the server-wide landmarks. |
| `/landmarks view <dimension> <id>` | Show one landmark in detail. |

## Operators

| Command | What it does |
|---|---|
| `/landmarks new block <pos> [<dimension>]` | Make a server-wide landmark from a block — spawn, shops, warp hubs… |
| `/landmarks new id <dimension> <id> <pos> <icon> <name> <lore>` | Make a landmark with its own id, item icon, name and description. |
| `/landmarks remove <dimension> <id>` | Delete a landmark. |
| `/landmarks append <id> surveyor:color <color>` / `/landmarks trim <id> <component>` | Add or remove a detail of a landmark (for example its colour). |
| `/waypoints new block <pos> <dimension> <owner>` | Make a waypoint for another player (by UUID). The same `append` / `trim` work for waypoints. |

With `debugCommands = true`, `/waypoints raw` and `/landmarks raw` print a waypoint's raw data.

## Servers without Surveyor

If you play on a server that doesn't have Surveyor, you still get your own waypoints with `/waypointsc` (client side): `/waypointsc`, `/waypointsc new block <pos>`, `/waypointsc view <id>`, `/waypointsc remove <id>`.
