# Data and Troubleshooting

## Where the map is saved

| Game | Location |
|---|---|
| Singleplayer and servers | `<world>/<dimension>/data/surveyor/` (the Overworld: `<world>/data/surveyor/`) |
| Multiplayer clients | `.minecraft/data/surveyor/<world>/<dimension>/` |

On multiplayer clients, `<world>` is the server's world id when the server runs this version of Surveyor, and the world seed otherwise. The first time a server sends its id, the client copies the seed folder to the id folder; the seed folder stays as it was. A `servers.txt` in each folder lists the servers that used it.

| File | Holds |
|---|---|
| `c.X.Z.dat` | terrain, one file per region of 32 × 32 chunks |
| `s.X.Z.dat` and `structures.dat` | structures |
| `landmarks.dat` | waypoints and landmarks |
| `sharing.dat` | sharing groups (servers, when `globalSharing = false`) |
| `world_id.dat` | the world's id, sent to clients so they keep each server's map apart (servers, Overworld folder) |

Each player's exploration is stored with their player data. Files are written safely: an interrupted save never damages the previous copy.

## When something goes wrong

- **One kind of data is broken** (for example an error about structures): back up and delete those files — the `s.*.dat` files for structures, `c.*.dat` for terrain. They are rebuilt as you play.
- **Players get disconnected by map data**: set that kind of data to `NONE` in `[networking]` (for example `terrain = "NONE"`) and report the problem.
- **The server uses too much memory**: turn off the heaviest system on the server, usually `terrain = "DISABLED"`.
- **Two servers behind a proxy show the same map**: both servers have the same `world_id.dat`, usually because one world folder was copied from the other. Stop one server and delete its `data/surveyor/world_id.dat`; a new id is made on start. Players keep the copy of the shared map they already had, and each server's map stays apart from then on.
- **Nothing appears on the map**: make sure a map mod is installed, and that the systems aren't `DISABLED`.

Report problems at [Darkona/surveyor-neoforge](https://github.com/Darkona/surveyor-neoforge/issues) with your `logs/latest.log`.
