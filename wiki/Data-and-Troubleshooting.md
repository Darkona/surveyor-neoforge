# Data and Troubleshooting

## Where the map is saved

| Game | Location |
|---|---|
| Singleplayer and servers | `<world>/<dimension>/data/surveyor/` (the Overworld: `<world>/data/surveyor/`) |
| Multiplayer clients | `.minecraft/data/surveyor/<world>/<dimension>/` |

| File | Holds |
|---|---|
| `c.X.Z.dat` | terrain, one file per region of 32 × 32 chunks |
| `s.X.Z.dat` and `structures.dat` | structures |
| `landmarks.dat` | waypoints and landmarks |
| `sharing.dat` | sharing groups (servers, when `globalSharing = false`) |

Each player's exploration is stored with their player data. Files are written safely: an interrupted save never damages the previous copy.

## When something goes wrong

- **One kind of data is broken** (for example an error about structures): back up and delete those files — the `s.*.dat` files for structures, `c.*.dat` for terrain. They are rebuilt as you play.
- **Players get disconnected by map data**: set that kind of data to `NONE` in `[networking]` (for example `terrain = "NONE"`) and report the problem.
- **The server uses too much memory**: turn off the heaviest system on the server, usually `terrain = "DISABLED"`.
- **Nothing appears on the map**: make sure a map mod is installed, and that the systems aren't `DISABLED`.

Report problems at [Darkona/surveyor-neoforge](https://github.com/Darkona/surveyor-neoforge/issues) with your `logs/latest.log`.
