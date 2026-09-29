# Data and Troubleshooting

## Where the map is saved

| Game | Location |
|---|---|
| Singleplayer and servers | `<world>/<dimension>/data/surveyor/` (for the Overworld: `<world>/data/surveyor/`) |
| Multiplayer clients | `.minecraft/data/surveyor/<world>/<dimension>/` |

On multiplayer clients, `<world>` is the world id of the server when the server runs this version of Surveyor. Otherwise it is the world seed. The first time a server sends its id, the client copies the seed folder to the id folder and leaves the seed folder as it was. Each folder has a `servers.txt` with the servers that used it.

| File | Holds |
|---|---|
| `c.X.Z.dat` | terrain, one file per region of 32 × 32 chunks |
| `s.X.Z.dat` and `structures.dat` | structures |
| `landmarks.dat` | waypoints and landmarks |
| `sharing.dat` | sharing groups (servers, when `globalSharing = false`) |
| `world_id.dat` | the id of the world, sent to clients so that they keep the map of each server apart (servers, Overworld folder) |

The exploration of each player is stored with their player data. Surveyor writes files safely: a save that stops halfway never damages the previous copy.

## When something goes wrong

- **One kind of data is broken** (for example an error about structures): make a backup and delete those files. Delete the `s.*.dat` files for structures and the `c.*.dat` files for terrain. Surveyor makes them again as you play.
- **Map data disconnects players**: set that kind of data to `NONE` in `[networking]` (for example `terrain = "NONE"`) and report the problem.
- **The server uses too much memory**: turn off the heaviest system on the server. Usually that is `terrain = "DISABLED"`.
- **Two servers behind a proxy show the same map**: both servers have the same `world_id.dat`. Usually one world folder was copied from the other. Stop one server and delete its `data/surveyor/world_id.dat`. The server makes a new id when it starts. Players keep the copy of the shared map that they already had, and from then on the map of each server stays apart.
- **Nothing shows on the map**: make sure that a map mod is installed and that the systems are not `DISABLED`.

Report problems at [Darkona/surveyor-neoforge](https://github.com/Darkona/surveyor-neoforge/issues) and attach your `logs/latest.log`.

## Debug mode

Debug mode writes what Surveyor does to `logs/latest.log`. Each line starts with `[Surveyor/debug]`.

To turn it on, do one of these:

- Set `debug = true` in `config/surveyor.toml`, or turn on **Debug mode** in the config screen.
- Add the JVM argument `-Dsurveyor.debug=true` to the server or the game. With this argument, debug mode stays on whatever the config says.

Turn it on where the problem happens: on the server for a server problem, on your game for a client problem, or on both.

Events that are not frequent get one line each:

- Terrain and structure regions found when a dimension loads.
- Landmarks added or removed, with their id and owner, and changes ignored because `landmarks = "FROZEN"`.
- POIs that wait for their chunk to finish generating, POIs added later, and POIs skipped because `poiLandmarksFromWorldgen = false`.
- Structures that players discover.
- Share group joins and leaves, and the `ExplorationReset` event of the client.
- The world id: loaded or created on the server, sent to players, received by the client, and the map folder that the client picks (world id, seed, or seed after a failed copy).

Busy activity is added up and written as one line every 100 ticks, only when something happened. For example:

```
[Surveyor/debug] Last 100 ticks: chunks.offThread=41, chunks.inPlace=3, chunks.inPlace.readsLevel=1, chunks.published=41, chunks.pendingMax=12, regions.written=2; sent surveyor:s2c_update_region x4 (payloads 4, players 2)
```

| Counter | Meaning |
|---|---|
| `chunks.offThread` | Chunks recorded on the background thread. |
| `chunks.inPlace` | Chunks recorded on the game thread. The `inPlace.*` counters give the reason: `readsLevel` (the map colour of a block needs the world), `queueFull` (more than 256 chunks wait), `asyncOff` (`asyncChunkSummaries = false`). |
| `chunks.published`, `chunks.stale`, `chunks.failed` | Background results that went into the map, results that a newer copy of the chunk replaced, and results that failed and were done again on the game thread. |
| `chunks.pendingMax` | The largest number of chunks that waited at the same time. |
| `regions.read`, `regions.written`, `regions.unloaded` | Terrain region files read, written and dropped from memory. |
| `structures.added`, `structureRegions.read`, `structureRegions.dropped` | Structures recorded, and structure region files read and dropped from memory. |
| `landmarks.put`, `landmarks.removed` | Landmarks added and removed. |
| `pois.*` | POI landmarks: added, deferred, resolved, waiting again, skipped. |
| `positions.groupsSent`, `positions.hiddenSkipped` | Player position updates sent, and hidden players left out. |
| `palette.fallbacks` | Biomes or blocks not in the registry, recorded as the fallback value. |
| `sent` / `received` | Surveyor packets by type: how many, their payloads, and the players they went to. |

When debug mode is off, its cost is too small to measure. When it is on, it adds lines to the log. Turn it off again when you are done.

**For a bug report**, turn on debug mode and do again the steps that cause the problem. Then attach `logs/latest.log`. If the problem involves both sides, attach the log of the server and the log of the client. Also attach `config/surveyor.toml`.
