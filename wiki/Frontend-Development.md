# Writing a map frontend

Build Surveyor from source and publish it to your local Maven repository (`./gradlew publishToMavenLocal`). Then add it as a dependency of your NeoForge project:

```groovy
repositories {
	mavenLocal()
}

dependencies {
	implementation "folk.sisby:surveyor:1.2.4+1.21-neoforge"
}
```

#### Initial Setup

Client map mods should always use `SurveyorClientEvents`. With these events, singleplayer gives you only the explored areas.

Listen to `WorldLoad` and put the keys it gives you in your render queue. The event fires when the client world has access to Surveyor data and the player is available.

- `terrain` has all available chunks, by region. `WorldTerrainSummary.toKeys()` turns it into ChunkPos values.
- `structures` has all structure starts, by key and ChunkPos.
- `landmarks` has all landmarks (POIs, waypoints, death markers and others), by type and BlockPos.

Later, you can get the same data from the world summary with the `keySet()` methods. The event implementation shows how. Pass in `SurveyorClient.getExploration()` so that unexplored areas stay hidden.

To save memory, save and unload each RegionSummary after its first bake is complete.

##### Live Updates

Also listen to `TerrainUpdated`, `StructuresAdded` and `LandmarksAdded`, and add what they give you to your render queues. They fire when the client player should see something new, usually because of exploration. They can also fire before `ClientPlayerLoad`, so let any of them create your map data.

Listen to `LandmarksRemoved` too, but without a queue. Remove the landmarks from your map or queue directly.

This fork adds `ExplorationReset` (`SurveyorClientEvents.Register.explorationReset`). It fires when the shared exploration of the client is replaced instead of extended, for example when the share group of the player changes. Chunks and structures that you got before may not be explored anymore. Drop what you drew and build it again from the world summaries and `SurveyorClient.getExploration()`.

#### Terrain Rendering

First, make a top layer with `get(ChunkPos).toSingleLayer()`, with any height limits you want. The result is a raw layer summary of one-dimensional arrays:

* **exists**: true where a floor exists, false otherwise. Where it is false, all other fields are junk.
* **depths**: the distance of the floor below the world height that you gave, so y = worldHeight - depth.
* **blocks**: the floor block. Indexed per region through `getBlockPalette(ChunkPos)`.
* **biomes**: the floor biome. Indexed per region through `getBiomePalette(ChunkPos)`.
* **lightLevels**: the block light level directly above the floor, that is, the block light of its top face. 0-15.
* **waterLights**: the block light level directly above the surface of the water, if there is water. 0-15.
* **waterDepths**: the depth of the continuous water above the floor.
	* Surfaces of all other liquids count as floors. Water is a special case.
	* Surveyor records the sea floor (for example sand), and this depth value gives the water surface.
	* With this, maps can shade water by depth, or hide water completely.

Index all arrays with `x * 16 + z`, where x and z are relative to the chunk. Use these arrays to render and store the map data of that chunk, as pixels, buffers or anything else. You will render hundreds of thousands of chunks, so make this step as fast as you can.

#### Structure Rendering

With the key and the ChunkPos, `getType(key)` and `getTags(key)` give the type and the tags of a structure.

`get(key, ChunkPos)` gives a full summary of the structure, for example to draw its bounding boxes. It includes piece data such as boxes, direction and IDs.

#### Landmark Rendering & Management

A landmark has an arbitrary ID and can hold arbitrary bits of [Component Data](https://github.com/sisby-folk/surveyor/blob/1.20/src/main/java/folk/sisby/surveyor/landmark/component/LandmarkComponentTypes.java).

When you render landmarks, check their components for useful data such as position, name and color. Have a plan to render every landmark.

To add a landmark, use `WorldSummary.of(world).landmarks().put(Landmark.create(owner, id, b -> b.add(..., ...))`. For addons: this works on either side, but clients can only send landmarks that they own, that is, waypoints.

#### Player Rendering

`SurveyorClient.getFriends()` gives a set of players to draw on the map.

Each player is an abstract entry with a UUID, username, global position, yaw and online status.
