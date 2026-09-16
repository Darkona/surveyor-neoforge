# Vanilla Maps

Surveyor can exchange exploration with ordinary paper maps. **Sneak and use a filled map on a cartography table.**

- **From the map:** the area drawn on the map becomes explored for you (an explorer map counts its whole area), and the map's icons — banners, markers — become waypoints.
- **To the map:** your explored terrain is drawn onto the map, in explorer-map style or full colour.

Both happen at once; you hear the cartography table when it worked. A locked map is only read, never drawn on.

Options (see [Configuration](Configuration)): `recordFromMapItems`, `recordToMapItems`, `disabledRecordIcons`.

Which blocks do this is data-driven: the block tags `surveyor:record_from_map` and `surveyor:record_to_map` (both contain the cartography table). A datapack can add more blocks, such as a lectern.
