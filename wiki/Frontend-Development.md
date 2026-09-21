# Writing a map frontend

Build Surveyor from source and publish it locally (`./gradlew publishToMavenLocal`), then depend on it from your NeoForge project:

```groovy
repositories {
	mavenLocal()
}

dependencies {
	implementation "folk.sisby:surveyor:1.2.4+1.21-neoforge"
}
```

#### Initial Setup

Client map mods should always use `SurveyorClientEvents` - this ensures only explored areas will be provided in singleplayer.

Tune into `WorldLoad` and queue up the provided keys for rendering.<br/>
This event will trigger when the client world has access to surveyor data and the player is available.

`terrain` contains all available chunks by region. `WorldTerrainSummary.toKeys()` converts this into ChunkPos.<br/>
`structures` contains all structure starts by key + ChunkPos.<br/>
`landmarks` contains all landmarks (POIs, waypoints, death markers, etc.) by type + BlockPos.

You can get these from the world summary later using `keySet()` methods - check the event implementation.<br/>
Pass in `SurveyorClient.getExploration()` to ensure unexplored areas are hidden.

If you want to save memory, you should save and unload each RegionSummary after its initial bake is complete.

##### Live Updates

Also tune into `TerrainUpdated`, `StructuresAdded`, `LandmarksAdded` to add to your render queues.<br/>
These fire whenever the client player should see something new (usually via exploration).<br/>
They can also fire before `ClientPlayerLoad`, so let any of them create your map data.

Tune into `LandmarksRemoved` as well but without a queue - just remove from your map/queue directly.

This fork adds `ExplorationReset` (`SurveyorClientEvents.Register.explorationReset`): it fires when the client's shared exploration is replaced rather than extended, e.g. when the player's share group changes. Chunks and structures reported before may no longer be explored, so drop what you drew and rebuild it from the world summaries and `SurveyorClient.getExploration()`.

#### Terrain Rendering

First, generate a top layer (with any desired height limits) using `get(ChunkPos).toSingleLayer()`.<br/>
This will produce a raw layer summary of one-dimensional arrays:

* **exists** - True where a floor exists, false otherwise - where false, all other fields are junk.
* **depths** - The distance of the floor below your specified world height. so y = worldHeight - depth.
* **blocks** - The floor block. Indexed per-region via `getBlockPalette(ChunkPos)`.
* **biomes** - The floor biome. Indexed per-region via `getBiomePalette(ChunkPos)`.
* **lightLevels** - The block light level directly above the floor (i.e. the block light for its top face). 0-15.
* **waterLights** - The block light level directly above the water's surface (if there is one). 0-15.
* **waterDepths** - How deep the contiguous water above the floor is.
	* All other liquid surfaces are considered floors, but water is special-cased.
	* The sea floor (e.g. sand) is recorded, and this depth value indicates the water surface instead.
	* This allows maps to show water depth shading, but also hide water completely if desired.

All arrays can be indexed by `x * 16 + z`, where x and z are relative to the chunk.<br/>
Use these arrays to render and store map data for that chunk (pixels, buffers, whichever).<br/>
Remember that you'll be rendering hundreds of thousands of chunks here - optimize this process hard.

#### Structure Rendering

Along with the key and ChunkPos, you can get the type and any tags using `getType(key)` and `getTags(key)`.

You can access a full summary of the structure (e.g. to draw its bounding boxes) using `get(key, ChunkPos)`.<br/>
This includes piece data like boxes, direction, IDs, etc.

#### Landmark Rendering & Management

Landmarks are an arbitrary ID and can be filled with arbitrary bits of [Component Data](https://github.com/sisby-folk/surveyor/blob/1.20/src/main/java/folk/sisby/surveyor/landmark/component/LandmarkComponentTypes.java)!

When rendering them, you should check their components for useful bits like position, name, and color - try have a strategy for rendering everything!

To add a landmark, just use `WorldSummary.of(world).landmarks().put(Landmark.create(owner, id, b -> b.add(..., ...))`.<br/>
Note for addons that this works fine on either side - though clients can only send through landmarks owned by them (aka waypoints).


#### Player Rendering

You can use `SurveyorClient.getFriends()` to get a set of players to draw on the map.

The players are represented abstractly, providing UUID, username, global position, yaw, and online status.
