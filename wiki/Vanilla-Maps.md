# Vanilla Maps

Surveyor can exchange exploration with ordinary paper maps. **Sneak and use a filled map on a cartography table.**

- **From the map:** the area drawn on the map becomes explored for you, and the icons of the map (banners, markers) become waypoints. An explorer map counts its whole area.
- **To the map:** Surveyor draws your explored terrain on the map, in explorer-map style or in full colour.

Both happen at the same time. The cartography table plays its sound when it worked. Surveyor only reads a locked map and never draws on it.

Options (see [Configuration](Configuration)): `recordFromMapItems`, `recordToMapItems`, `disabledRecordIcons`.

Data decides which blocks do this: the block tags `surveyor:record_from_map` and `surveyor:record_to_map`. Both contain the cartography table. A datapack can add more blocks, such as a lectern.
