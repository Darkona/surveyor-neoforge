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

## Debug mode

Debug mode writes what Surveyor does to `logs/latest.log`. Each line starts with `[Surveyor/debug]`.

To turn it on, do one of these:

- Set `debug = true` in `config/surveyor.toml`, or turn on **Debug mode** in the config screen.
- Add the JVM argument `-Dsurveyor.debug=true` to the server or the game. This keeps debug mode on whatever the config says.

Turn it on where the problem happens: on the server for a server problem, on your game for a client problem, or on both.

Events that are not frequent get one line each:

- terrain and structure regions found when a dimension loads;
- landmarks added or removed, with their id and owner, and changes ignored because `landmarks = "FROZEN"`;
- POIs that wait for their chunk to finish generating, are added later, or are skipped because `poiLandmarksFromWorldgen = false`;
- structures that players discover;
- share group joins and leaves, and the client's `ExplorationReset` event;
- the world id: loaded or created on the server, sent to players, received by the client, and the map folder the client picks (world id, seed, or seed after a failed copy).

Busy activity is summed and written as one line every 100 ticks, only when something happened. For example:

```
[Surveyor/debug] Last 100 ticks: chunks.offThread=41, chunks.inPlace=3, chunks.inPlace.readsLevel=1, chunks.published=41, chunks.pendingMax=12, regions.written=2; sent surveyor:s2c_update_region x4 (payloads 4, players 2)
```

| Counter | Meaning |
|---|---|
| `chunks.offThread` | Chunks recorded on the background thread. |
| `chunks.inPlace` | Chunks recorded on the game thread. The `inPlace.*` counters tell why: `readsLevel` (a block's map colour needs the world), `queueFull` (more than 256 chunks waiting), `asyncOff` (`asyncChunkSummaries = false`). |
| `chunks.published`, `chunks.stale`, `chunks.failed` | Background results added to the map, replaced by a newer copy of the chunk, or failed and done again on the game thread. |
| `chunks.pendingMax` | The most chunks waiting at the same time. |
| `regions.read`, `regions.written`, `regions.unloaded` | Terrain region files read, written and dropped from memory. |
| `structures.added`, `structureRegions.read`, `structureRegions.dropped` | Structures recorded, and structure region files read and dropped from memory. |
| `landmarks.put`, `landmarks.removed` | Landmarks added and removed. |
| `pois.*` | POI landmarks: added, deferred, resolved, waiting again, skipped. |
| `positions.groupsSent`, `positions.hiddenSkipped` | Player position updates sent, and hidden players left out. |
| `palette.fallbacks` | Biomes or blocks not in the registry, recorded as the fallback value. |
| `sent` / `received` | Surveyor packets by type: how many, their payloads, and the players they went to. |

Debug mode off costs nothing that you can measure. On, it adds some lines to the log; turn it off again when you are done.

**For a bug report**, turn on debug mode, do the steps that cause the problem again, and attach `logs/latest.log`. Attach the server's log and the client's log if the problem involves both. Also attach `config/surveyor.toml`.
