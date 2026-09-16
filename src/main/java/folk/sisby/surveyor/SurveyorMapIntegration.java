package folk.sisby.surveyor;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import folk.sisby.surveyor.config.SurveyorConfig;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.terrain.LayerSummary;
import folk.sisby.surveyor.terrain.WorldTerrain;
import org.apache.commons.lang3.text.WordUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class SurveyorMapIntegration {
	public static final TagKey<Block> RECORD_FROM_MAP = TagKey.create(Registries.BLOCK, Surveyor.id("record_from_map"));
	public static final TagKey<Block> RECORD_TO_MAP = TagKey.create(Registries.BLOCK, Surveyor.id("record_to_map"));

	public static final Map<Holder<MapDecorationType>, Item> VANILLA_ICON_STACKS = Map.ofEntries(
		Map.entry(MapDecorationTypes.PLAYER, Items.PLAYER_HEAD),
		Map.entry(MapDecorationTypes.FRAME, Items.ITEM_FRAME),
		Map.entry(MapDecorationTypes.RED_MARKER, Items.RED_DYE),
		Map.entry(MapDecorationTypes.BLUE_MARKER, Items.BLUE_DYE),
		Map.entry(MapDecorationTypes.TARGET_X, Items.NETHER_STAR),
		Map.entry(MapDecorationTypes.TARGET_POINT, Items.REDSTONE),
		Map.entry(MapDecorationTypes.PLAYER_OFF_MAP, Items.PLAYER_HEAD),
		Map.entry(MapDecorationTypes.PLAYER_OFF_LIMITS, Items.PLAYER_HEAD),
		Map.entry(MapDecorationTypes.WOODLAND_MANSION, Items.TOTEM_OF_UNDYING),
		Map.entry(MapDecorationTypes.OCEAN_MONUMENT, Items.TRIDENT),
		Map.entry(MapDecorationTypes.WHITE_BANNER, Items.WHITE_BANNER),
		Map.entry(MapDecorationTypes.ORANGE_BANNER, Items.ORANGE_BANNER),
		Map.entry(MapDecorationTypes.MAGENTA_BANNER, Items.MAGENTA_BANNER),
		Map.entry(MapDecorationTypes.LIGHT_BLUE_BANNER, Items.LIGHT_BLUE_BANNER),
		Map.entry(MapDecorationTypes.YELLOW_BANNER, Items.YELLOW_BANNER),
		Map.entry(MapDecorationTypes.LIME_BANNER, Items.LIME_BANNER),
		Map.entry(MapDecorationTypes.PINK_BANNER, Items.PINK_BANNER),
		Map.entry(MapDecorationTypes.GRAY_BANNER, Items.GRAY_BANNER),
		Map.entry(MapDecorationTypes.LIGHT_GRAY_BANNER, Items.LIGHT_GRAY_BANNER),
		Map.entry(MapDecorationTypes.CYAN_BANNER, Items.CYAN_BANNER),
		Map.entry(MapDecorationTypes.PURPLE_BANNER, Items.PURPLE_BANNER),
		Map.entry(MapDecorationTypes.BLUE_BANNER, Items.BLUE_BANNER),
		Map.entry(MapDecorationTypes.BROWN_BANNER, Items.BROWN_BANNER),
		Map.entry(MapDecorationTypes.GREEN_BANNER, Items.GREEN_BANNER),
		Map.entry(MapDecorationTypes.RED_BANNER, Items.RED_BANNER),
		Map.entry(MapDecorationTypes.BLACK_BANNER, Items.BLACK_BANNER),
		Map.entry(MapDecorationTypes.RED_X, Items.CHEST),
		Map.entry(MapDecorationTypes.DESERT_VILLAGE, Items.SANDSTONE),
		Map.entry(MapDecorationTypes.PLAINS_VILLAGE, Items.OAK_LOG),
		Map.entry(MapDecorationTypes.SAVANNA_VILLAGE, Items.ACACIA_LOG),
		Map.entry(MapDecorationTypes.SNOWY_VILLAGE, Items.SNOW_BLOCK),
		Map.entry(MapDecorationTypes.TAIGA_VILLAGE, Items.SPRUCE_LOG),
		Map.entry(MapDecorationTypes.JUNGLE_TEMPLE, Items.MOSSY_COBBLESTONE),
		Map.entry(MapDecorationTypes.SWAMP_HUT, Items.BREWING_STAND),
		Map.entry(MapDecorationTypes.TRIAL_CHAMBERS, Items.TRIAL_SPAWNER)
	);

	public static void applyMapData(ServerPlayer player, MapItemSavedData mapState) {
		ServerSummary summary = ServerSummary.of(player.getServer());
		SurveyorExploration exploration = summary.getSharingExploration(Surveyor.getUuid(player), Surveyor.CONFIG.networking.terrain, true);
		if (Surveyor.CONFIG.builtins.recordToMapItems == SurveyorConfig.Builtins.RecordStyle.COLOR) {
			fillMap(summary, exploration, mapState);
		} else {
			fillExplorationMap(summary, exploration, mapState);
		}
	}

	public static void recordMapData(ServerPlayer player, MapItemSavedData mapState) {
		Set<ChunkPos> exploredChunks = new HashSet<>();
		Set<Landmark> exploredWaypoints = new HashSet<>();
		int blocksPerPixel = 1 << mapState.scale;
		int originX = mapState.centerX - 64 * blocksPerPixel;
		int originZ = mapState.centerZ - 64 * blocksPerPixel;
		int chunkWidth = blocksPerPixel * 128 / 16;
		SurveyorExploration exploration = SurveyorExploration.of(player);
		WorldTerrain terrain = ServerSummary.of(player.getServer()).getWorld(mapState.dimension).terrain();
		boolean explorer = mapState.unlimitedTracking;
		if (terrain != null) {
			for (int x = 0; x < chunkWidth; x++) {
				for (int z = 0; z < chunkWidth; z++) {
					if (explorer || mapState.colors[x * 16 / blocksPerPixel + 128 * z * 16 / blocksPerPixel] != MapColor.NONE.id) {
						ChunkPos chunkPos = new ChunkPos(new BlockPos(originX + x * 16, 0, originZ + z * 16));
						if (!exploration.exploredChunk(mapState.dimension, chunkPos)) {
							exploredChunks.add(chunkPos);
						}
					}
				}
			}
			exploredChunks.forEach(c -> exploration.addChunk(mapState.dimension, c, true));
		}
		Set<Holder<MapDecorationType>> recordIcons = Surveyor.CONFIG.builtins.recordIcons();
		WorldLandmarks landmarks = ServerSummary.of(player.getServer()).getWorld(mapState.dimension).landmarks();
		UUID owner = Surveyor.getUuid(player);
		if (landmarks != null) {
			for (MapDecoration icon : mapState.getDecorations()) {
				if (recordIcons.contains(icon.type())) {
					int x = Mth.floor(((((double) icon.x()) - 0.5) / 2.0) * blocksPerPixel + mapState.centerX);
					int z = Mth.floor(((((double) icon.y()) - 0.5) / 2.0) * blocksPerPixel + mapState.centerZ);
					ResourceLocation id = ResourceLocation.fromNamespaceAndPath("minecraft", "map/%s/%d/%d".formatted(icon.getSpriteLocation().getPath().toLowerCase(), x, z));
					if (!landmarks.contains(owner, id)) {
						Item item = VANILLA_ICON_STACKS.getOrDefault(icon.type(), Items.STICK);
						exploredWaypoints.add(Landmark.create(owner, id, b -> b
							.add(LandmarkComponentTypes.POS, new BlockPos(x, 0, z))
							.add(LandmarkComponentTypes.NAME, icon.name().orElse(item instanceof BannerItem ? item.getDescription() : Component.literal(WordUtils.capitalizeFully(icon.type().getRegisteredName().replace("minecraft:", "").replace(":", " ").replace('_', ' ')))))
							.add(LandmarkComponentTypes.STACK, item.getDefaultInstance().copy())
						));
					}
				}
			}
			exploredWaypoints.forEach(landmarks::put);
		}
		if (!exploredChunks.isEmpty() || !exploredWaypoints.isEmpty()) {
			player.displayClientMessage(Component.empty()
					.append(Component.literal("Recorded ").withStyle(ChatFormatting.GREEN))
					.append(exploredChunks.isEmpty() ? Component.empty() : Component.literal("%d".formatted(exploredChunks.size())))
					.append(exploredChunks.isEmpty() ? Component.empty() : Component.literal(" new chunks").withStyle(ChatFormatting.GREEN))
					.append(exploredChunks.isEmpty() || exploredWaypoints.isEmpty() ? Component.empty() : Component.literal(" and ").withStyle(ChatFormatting.GREEN))
					.append(exploredWaypoints.isEmpty() ? Component.empty() : Component.literal("%d".formatted(exploredWaypoints.size())))
					.append(exploredWaypoints.isEmpty() ? Component.empty() : Component.literal(" new waypoints").withStyle(ChatFormatting.GREEN))
					.append(Component.literal("!").withStyle(ChatFormatting.GREEN))
				, true);
		}
	}


	public static void fillExplorationMap(ServerSummary summary, SurveyorExploration exploration, MapItemSavedData state) {
		WorldTerrain terrain = summary.getWorld(state.dimension).terrain();
		if (terrain == null) return;
		int blocksPerPixel = 1 << state.scale;
		Boolean[] biomeIsAquatic = new Boolean[16384];
		int offsetX = state.centerX / blocksPerPixel - 64;
		int offsetZ = state.centerZ / blocksPerPixel - 64;
		Map<ChunkPos, LayerSummary.Raw> chunks = new HashMap<>();
		Registry<Biome> biomeRegistry = summary.getServer().registryAccess().registryOrThrow(Registries.BIOME);

		for (int pixelX = 0; pixelX < 128; pixelX++) {
			for (int pixelZ = 0; pixelZ < 128; pixelZ++) {
				BlockPos blockPos = new BlockPos((offsetX + pixelZ) * blocksPerPixel, 0, (offsetZ + pixelX) * blocksPerPixel);
				ChunkPos chunkPos = new ChunkPos(blockPos);
				if (!terrain.contains(chunkPos) || (exploration != null && !exploration.exploredChunk(state.dimension, chunkPos))) continue;
				LayerSummary.Raw chunk = chunks.computeIfAbsent(chunkPos, k -> terrain.get(k).toSingleLayer(null, null, 999));
				if (chunk == null) continue;
				Holder<Biome> biome = biomeRegistry.wrapAsHolder(terrain.getBiomePalette(chunkPos).byId(chunk.biomes()[16 * (blockPos.getX() - chunkPos.getMinBlockX()) + blockPos.getZ() - chunkPos.getMinBlockZ()]));
				biomeIsAquatic[pixelX * 128 + pixelZ] = biome.is(BiomeTags.WATER_ON_MAP_OUTLINES);
			}
		}

		for (int pixelX = 1; pixelX < 127; pixelX++) {
			for (int pixelZ = 1; pixelZ < 127; pixelZ++) {
				if (state.colors[pixelX + pixelZ * 128] != MapColor.NONE.id) continue; // avoid overwriting real maps with explorer ones
				if (biomeIsAquatic[(pixelZ - 1) * 128 + pixelX - 1] == null
					|| biomeIsAquatic[(pixelZ - 1) * 128 + pixelX] == null
					|| biomeIsAquatic[(pixelZ - 1) * 128 + pixelX + 1] == null
					|| biomeIsAquatic[(pixelZ) * 128 + pixelX - 1] == null
					|| biomeIsAquatic[(pixelZ) * 128 + pixelX] == null
					|| biomeIsAquatic[(pixelZ) * 128 + pixelX + 1] == null
					|| biomeIsAquatic[(pixelZ + 1) * 128 + pixelX - 1] == null
					|| biomeIsAquatic[(pixelZ + 1) * 128 + pixelX] == null
					|| biomeIsAquatic[(pixelZ + 1) * 128 + pixelX + 1] == null
				) continue;
				int noise = 0;

				for (int xNoise = -1; xNoise < 2; xNoise++) {
					for (int zNoise = -1; zNoise < 2; zNoise++) {
						if ((xNoise != 0 || zNoise != 0) && biomeIsAquatic[(pixelZ + zNoise) * 128 + pixelX + xNoise]) {
							noise++;
						}
					}
				}

				MapColor.Brightness brightness = MapColor.Brightness.LOWEST;
				MapColor mapColor = MapColor.NONE;
				if (biomeIsAquatic[pixelZ * 128 + pixelX]) {
					mapColor = MapColor.COLOR_ORANGE;
					if (noise > 7 && pixelZ % 2 == 0) {
						brightness = switch ((pixelX + (int) (Mth.sin(pixelZ + 0.0F) * 7.0F)) / 8 % 5) {
							case 0, 4 -> MapColor.Brightness.LOW;
							case 1, 3 -> MapColor.Brightness.NORMAL;
							case 2 -> MapColor.Brightness.HIGH;
							default -> brightness;
						};
					} else if (noise > 7) {
						mapColor = MapColor.NONE;
					} else if (noise > 5) {
						brightness = MapColor.Brightness.NORMAL;
					} else if (noise > 3) {
						brightness = MapColor.Brightness.LOW;
					} else if (noise > 1) {
						brightness = MapColor.Brightness.LOW;
					}
				} else if (noise > 0) {
					mapColor = MapColor.COLOR_BROWN;
					if (noise > 3) {
						brightness = MapColor.Brightness.NORMAL;
					}
				}

				if (mapColor != MapColor.NONE) {
					state.setColor(pixelX, pixelZ, mapColor.getPackedId(brightness));
				}
			}
		}
	}

	public static void fillMap(ServerSummary summary, SurveyorExploration exploration, MapItemSavedData state) {
		WorldSummary worldSummary = summary.getWorld(state.dimension);
		WorldTerrain terrain = worldSummary.terrain();
		DimensionType dim = worldSummary.world().dimensionType();
		if (terrain == null) return;
		int blocksPerPixel = 1 << state.scale;
		Map<ChunkPos, LayerSummary.Raw> chunks = new HashMap<>();

		BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

		for (int pixelX = 0; pixelX < 128; pixelX++) {
			double prevHeight = 0.0;
			for (int pixelZ = 0; pixelZ < 128; pixelZ++) {
				int blockX = (state.centerX / blocksPerPixel + pixelX - 64) * blocksPerPixel;
				int blockZ = (state.centerZ / blocksPerPixel + pixelZ - 64) * blocksPerPixel;
				Multiset<MapColor> multiset = LinkedHashMultiset.create();
				ChunkPos chunkPos = new ChunkPos(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ));
				if (!terrain.contains(chunkPos) || (exploration != null && !exploration.exploredChunk(state.dimension, chunkPos))) continue;
				LayerSummary.Raw chunk = chunks.computeIfAbsent(chunkPos, k -> terrain.get(k).toSingleLayer(null, null, 999));
				if (chunk != null) {
					int t = 0;
					double height = 0.0;
					if (dim.hasCeiling()) {
						int u = blockX + blockZ * 231871;
						u = u * u * 31287121 + u * 11;
						if ((u >> 20 & 1) == 0) {
							multiset.add(Blocks.DIRT.defaultMapColor(), 10);
						} else {
							multiset.add(Blocks.STONE.defaultMapColor(), 100);
						}

						height = 100.0;
					} else {
						for (int u = 0; u < blocksPerPixel; u++) {
							for (int v = 0; v < blocksPerPixel; v++) {
								mutable.set(blockX + u, 0, blockZ + v);
								int index = 16 * (mutable.getX() - chunkPos.getMinBlockX()) + mutable.getZ() - chunkPos.getMinBlockZ();
								if (!chunk.exists().get(index)) continue;
								int y = 999 - chunk.depths()[index] + 1;
								height += (double) y / (blocksPerPixel * blocksPerPixel);
								t = chunk.waterDepths()[index];
								MapColor block = chunk.waterDepths()[index] > 0 ? MapColor.WATER : terrain.getBlockPalette(chunkPos).byId(chunk.blocks()[index]).defaultMapColor();
								multiset.add(block);
							}
						}
					}

					MapColor mapColor = Iterables.getFirst(Multisets.copyHighestCountFirst(multiset), MapColor.NONE);
					MapColor.Brightness brightness;
					if (mapColor == MapColor.WATER) {
						double heightShading = t * 0.1 + (pixelX + pixelZ & 1) * 0.2;
						if (heightShading < 0.5) {
							brightness = MapColor.Brightness.HIGH;
						} else if (heightShading > 0.9) {
							brightness = MapColor.Brightness.LOW;
						} else {
							brightness = MapColor.Brightness.NORMAL;
						}
					} else {
						double heightShading = (height - prevHeight) * 4.0 / (blocksPerPixel + 4) + ((pixelX + pixelZ & 1) - 0.5) * 0.4;
						if (heightShading > 0.6) {
							brightness = MapColor.Brightness.HIGH;
						} else if (heightShading < -0.6) {
							brightness = MapColor.Brightness.LOW;
						} else {
							brightness = MapColor.Brightness.NORMAL;
						}
					}

					prevHeight = height;
					state.updateColor(pixelX, pixelZ, mapColor.getPackedId(brightness));
				}
			}
		}
	}
}
