package folk.sisby.surveyor.landmark;

import folk.sisby.surveyor.util.SafeNbtWriter;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.packet.SyncLandmarksAddedPacket;
import folk.sisby.surveyor.packet.SyncLandmarksRemovedPacket;
import folk.sisby.surveyor.util.DispatchMapCodec;
import folk.sisby.surveyor.util.MapUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public class WorldLandmarks {
	public static final UUID GLOBAL = UUID.fromString("99999999-9999-9999-9999-999999999999");
	public static final String KEY_LANDMARKS = "landmarks";
	public static final String KEY_REMOVED = "removed";
	public static final Codec<Table<UUID, ResourceLocation, Landmark>> CODEC = DispatchMapCodec.of(
		UUIDUtil.STRING_CODEC,
		uuid -> DispatchMapCodec.of(
			ResourceLocation.CODEC,
			id -> Landmark.createCodec(uuid, id)
		)
	).xmap(MapUtil::asTable, Table::rowMap);
	public static final Codec<Multimap<UUID, ResourceLocation>> REMOVED_CODEC = Codec.unboundedMap(
		UUIDUtil.STRING_CODEC,
		Codec.list(ResourceLocation.CODEC)
	).xmap(MapUtil::asMultiMap, MapUtil::asListMap);
	public static final int[] XAERO_COLORS = new int[]{
		0xFF_000000, 0xFF_0000AA, 0xFF_00AA00, 0xFF_00AAAA, 0xFF_AA0000, 0xFF_AA00AA, 0xFF_ffAA00, 0xFF_AAAAAA, 0xFF_555555, 0xFF_5555FF, 0xFF_55FF55, 0xFF_55FFFF, 0xFF_FF0000, 0xFF_FF55FF, 0xFF_FFFF55, 0xFF_FFFFFF
	};
	protected final WorldSummary summary;
	protected final Table<UUID, ResourceLocation, Landmark> landmarks = Tables.synchronizedTable(HashBasedTable.create());
	protected final @Nullable Multimap<UUID, ResourceLocation> removed;
	protected boolean dirty;

	public static WorldLandmarks of(Level world) {
		return Optional.ofNullable(world).map(WorldSummary::of).map(WorldSummary::landmarks).orElse(null);
	}

	public WorldLandmarks(WorldSummary summary, Table<UUID, ResourceLocation, Landmark> landmarks, Multimap<UUID, ResourceLocation> removed, boolean dirty) {
		this.summary = summary;
		this.landmarks.putAll(landmarks);
		this.removed = removed == null ? null : Multimaps.synchronizedSetMultimap(HashMultimap.create(removed));
		if (this.removed != null) this.landmarks.cellSet().forEach(c -> removed.remove(c.getRowKey(), c.getColumnKey()));
		this.dirty = dirty;
	}

	public static WorldLandmarks load(WorldSummary summary, File folder) {
		CompoundTag landmarkNbt = new CompoundTag();
		File landmarksFile = new File(folder, "landmarks.dat");
		if (landmarksFile.exists()) {
			try {
				landmarkNbt = NbtIo.readCompressed(landmarksFile.toPath(), NbtAccounter.unlimitedHeap());
			} catch (IOException | ReportedNbtException e) {
				Surveyor.LOGGER.error("[Surveyor] Error loading landmarks file for {}.", summary.dimension().location(), e);
			}
		}
		WorldLandmarks worldLandmarks = fromNbt(summary, landmarkNbt, landmarksFile);
		if (!summary.isClient()) worldLandmarks.tryMigrateXaeros(false); // for singleplayer
		return worldLandmarks;
	}

	public CompoundTag writeNbt(CompoundTag nbt) {
		RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, summary.manager());
		synchronized (landmarks) {
			nbt.put(KEY_LANDMARKS, encode(landmarks, ops));
		}
		if (removed != null) {
			synchronized (removed) {
				REMOVED_CODEC.encodeStart(ops, removed).resultOrPartial(Surveyor.LOGGER::error).ifPresent(tag -> nbt.put(KEY_REMOVED, tag));
			}
		}
		return nbt;
	}

	/**
	 * Encodes like {@link #CODEC}, one landmark at a time: a landmark that can't be encoded is logged and left out instead
	 * of failing the whole save or packet. Needs registry ops for registry-bound components (enchanted item stacks).
	 */
	public static CompoundTag encode(Table<UUID, ResourceLocation, Landmark> landmarks, DynamicOps<Tag> ops) {
		CompoundTag owners = new CompoundTag();
		for (Table.Cell<UUID, ResourceLocation, Landmark> cell : landmarks.cellSet()) {
			UUID owner = cell.getRowKey();
			ResourceLocation id = cell.getColumnKey();
			Landmark.createCodec(owner, id).encodeStart(ops, cell.getValue())
				.resultOrPartial(e -> Surveyor.LOGGER.error("[Surveyor] Leaving out landmark {} of {}, it can't be encoded: {}", id, owner, e))
				.ifPresent(tag -> {
					String ownerKey = owner.toString();
					if (!owners.contains(ownerKey, Tag.TAG_COMPOUND)) owners.put(ownerKey, new CompoundTag());
					owners.getCompound(ownerKey).put(id.toString(), tag);
				});
		}
		return owners;
	}

	/**
	 * Decodes like {@link #CODEC}, keeping every landmark that decodes: one bad entry must not drop the rest.
	 */
	public static Table<UUID, ResourceLocation, Landmark> decode(Tag tag, DynamicOps<Tag> ops) {
		return CODEC.parse(ops, tag)
			.resultOrPartial(e -> Surveyor.LOGGER.error("[Surveyor] Some landmarks couldn't be decoded and were skipped: {}", e))
			.orElseGet(HashBasedTable::create);
	}

	public static WorldLandmarks fromNbt(WorldSummary summary, CompoundTag nbt, File landmarksFile) {
		CompoundTag landmarks = nbt.getCompound(KEY_LANDMARKS);
		boolean dirty = false;
		Table<UUID, ResourceLocation, Landmark> outMap = HashBasedTable.create();
		Multimap<UUID, ResourceLocation> removedMap = summary.server() == null ? null : HashMultimap.create();
		if (landmarks.getAllKeys().stream().anyMatch(k -> k.contains(":"))) { // 0.X
			Surveyor.LOGGER.warn("[Surveyor] Partially recovering landmarks from 0.X");
			try {
				Files.copy(landmarksFile.toPath(), landmarksFile.toPath().resolveSibling("landmarks.dat_v0"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new RuntimeException("Surveyor failed to back up v0 landmarks, was the file locked?", e);
			}
			try {
				for (String key : landmarks.getAllKeys()) {
					CompoundTag type = landmarks.getCompound(key);
					ResourceLocation typeId = ResourceLocation.tryParse(key);
					for (String coords : type.getAllKeys()) {
						CompoundTag landmark = type.getCompound(coords);
						UUID owner = landmark.contains("owner") ? UUIDUtil.AUTHLIB_CODEC.decode(NbtOps.INSTANCE, landmark.get("owner")).resultOrPartial(Surveyor.LOGGER::error).orElseThrow().getFirst() : GLOBAL;
						BlockPos pos = new BlockPos(Integer.parseInt(coords.split(",")[0]), Integer.parseInt(coords.split(",")[1]), Integer.parseInt(coords.split(",")[2]));
						DyeColor dye = !landmark.contains("color") ? null : DyeColor.CODEC.decode(NbtOps.INSTANCE, landmark.get("color")).resultOrPartial(Surveyor.LOGGER::error).map(Pair::getFirst).orElse(null);
						ResourceLocation id = (landmark.contains("texture") ? ResourceLocation.tryParse(landmark.getString("texture")) : typeId).withSuffix((dye == null ? "" : "/" + dye.getName()) + "/" + pos.getX() + (pos.getY() == 0 ? "" : "/" + pos.getY()) + "/" + pos.getZ());
						outMap.put(owner, id, Landmark.create(owner, id, b -> b
							.add(LandmarkComponentTypes.POS, pos)
							.add(LandmarkComponentTypes.BOX, !landmark.contains("box") ? null : BoundingBox.CODEC.decode(NbtOps.INSTANCE, landmark.get("box")).resultOrPartial(Surveyor.LOGGER::error).map(Pair::getFirst).orElse(null))
							.add(LandmarkComponentTypes.COLOR, dye == null ? null : dye.getFireworkColor())
							.add(LandmarkComponentTypes.NAME, !landmark.contains("name") ? null : ComponentSerialization.CODEC.decode(NbtOps.INSTANCE, landmark.get("name")).resultOrPartial(Surveyor.LOGGER::error).map(Pair::getFirst).orElse(null))
							.add(LandmarkComponentTypes.SEED, !landmark.contains("seed") ? null : landmark.getInt("seed"))
							.add(LandmarkComponentTypes.TIME, !landmark.contains("created") ? null : landmark.getLong("created"))
						));
						dirty = true;
					}
				}
				Surveyor.LOGGER.info("[Surveyor] Recovered {} landmarks from legacy data.", outMap.size());
			} catch (Exception e) {
				Surveyor.LOGGER.error("[Surveyor] Encountered an error during v0 landmark migration, skipping...", e);
			}
		} else {
			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, summary.manager());
			if (!landmarks.isEmpty()) outMap.putAll(decode(landmarks, ops));
			if (!summary.isClient()) {
				CompoundTag removed = nbt.getCompound(KEY_REMOVED);
				if (!removed.isEmpty()) removedMap = REMOVED_CODEC.parse(ops, removed).resultOrPartial(Surveyor.LOGGER::error).orElseGet(HashMultimap::create);
			}
		}
		return new WorldLandmarks(summary, outMap, removedMap, dirty);
	}

	public boolean contains(UUID uuid, ResourceLocation id) {
		return landmarks.contains(uuid, id);
	}

	public @Nullable Landmark get(UUID uuid, ResourceLocation id) {
		return contains(uuid, id) ? landmarks.get(uuid, id) : null;
	}

	public Map<ResourceLocation, Landmark> asMap(UUID uuid, SurveyorExploration exploration) {
		Map<ResourceLocation, Landmark> outMap = new HashMap<>(landmarks.row(uuid));
		if (exploration != null) outMap.values().removeIf(l -> !exploration.exploredLandmark(summary.dimension(), l));
		return outMap;
	}

	public Table<UUID, ResourceLocation, Landmark> asMap(SurveyorExploration exploration) {
		Table<UUID, ResourceLocation, Landmark> outMap = HashBasedTable.create(landmarks);
		if (exploration != null) outMap.values().removeIf(l -> !exploration.exploredLandmark(summary.dimension(), l));
		return outMap;
	}

	public Multimap<UUID, ResourceLocation> keySet(SurveyorExploration exploration) {
		Multimap<UUID, ResourceLocation> outMap = MapUtil.keyMultiMap(landmarks);
		if (exploration != null) outMap.entries().removeIf(e -> !exploration.exploredLandmark(summary.dimension(), landmarks.get(e.getKey(), e.getValue())));
		return outMap;
	}

	public Multimap<UUID, ResourceLocation> removed() {
		return HashMultimap.create(Objects.requireNonNullElse(removed, HashMultimap.create()));
	}

	public void handleChanged(Table<UUID, ResourceLocation, Landmark> changed, boolean local, @Nullable UUID sender) {
		if (changed.isEmpty()) return;
		Table<UUID, ResourceLocation, Landmark> landmarksRemoved = HashBasedTable.create(changed);
		landmarksRemoved.cellSet().removeAll(landmarks.cellSet());

		Table<UUID, ResourceLocation, Landmark> landmarksAdded = HashBasedTable.create(changed);
		landmarksAdded.cellSet().removeAll(landmarksRemoved.cellSet());

		if (!landmarksRemoved.isEmpty()) SurveyorEvents.Invoke.landmarksRemoved(summary, MapUtil.keyMultiMap(landmarksRemoved));
		if (!landmarksAdded.isEmpty()) SurveyorEvents.Invoke.landmarksAdded(summary, MapUtil.keyMultiMap(landmarksAdded));
		if (!local) {
			Table<UUID, ResourceLocation, Landmark> globalRemoved = MapUtil.asTable(Map.of(WorldLandmarks.GLOBAL, landmarksRemoved.row(GLOBAL)));
			Table<UUID, ResourceLocation, Landmark> globalAdded = MapUtil.asTable(Map.of(WorldLandmarks.GLOBAL, landmarksAdded.row(GLOBAL)));
			landmarksRemoved.rowMap().remove(WorldLandmarks.GLOBAL);
			landmarksAdded.rowMap().remove(WorldLandmarks.GLOBAL);
			if (!globalRemoved.isEmpty()) new SyncLandmarksRemovedPacket(summary.dimension(), MapUtil.keyMultiMap(globalRemoved)).send(sender, summary, Surveyor.CONFIG.networking.landmarks, false);
			if (!globalAdded.isEmpty()) new SyncLandmarksAddedPacket(summary.dimension(), globalAdded).send(sender, summary, Surveyor.CONFIG.networking.landmarks, false);
			if (!landmarksRemoved.isEmpty()) new SyncLandmarksRemovedPacket(summary.dimension(), MapUtil.keyMultiMap(landmarksRemoved)).send(sender, summary, Surveyor.CONFIG.networking.waypoints, false);
			if (!landmarksAdded.isEmpty()) new SyncLandmarksAddedPacket(summary.dimension(), landmarksAdded).send(sender, summary, Surveyor.CONFIG.networking.waypoints, false);
		}
	}

	public Table<UUID, ResourceLocation, Landmark> putForBatch(Table<UUID, ResourceLocation, Landmark> changed, Landmark landmark) {
		if (Surveyor.CONFIG.landmarks == SystemMode.FROZEN) return changed;
		landmarks.put(landmark.owner(), landmark.id(), landmark);
		if (removed != null) removed.remove(landmark.owner(), landmark.id());
		dirty();
		changed.put(landmark.owner(), landmark.id(), landmark);
		return changed;
	}

	public Table<UUID, ResourceLocation, Landmark> putForBatch(Landmark landmark) {
		return putForBatch(HashBasedTable.create(), landmark);
	}

	public void putLocal(Landmark landmark) {
		handleChanged(putForBatch(landmark), true, null);
	}

	public void put(Landmark landmark) {
		handleChanged(putForBatch(landmark), false, null);
	}

	public void put(UUID sender, Landmark landmark) {
		handleChanged(putForBatch(landmark), false, sender);
	}

	public Table<UUID, ResourceLocation, Landmark> removeForBatch(Table<UUID, ResourceLocation, Landmark> changed, UUID uuid, ResourceLocation id) {
		if (Surveyor.CONFIG.landmarks == SystemMode.FROZEN) return changed;
		if (!landmarks.contains(uuid, id)) return changed;
		Landmark landmark = landmarks.remove(uuid, id);
		if (removed != null) removed.put(uuid, id);
		dirty();
		changed.put(uuid, id, landmark);
		return changed;
	}

	public Table<UUID, ResourceLocation, Landmark> removeForBatch(UUID uuid, ResourceLocation id) {
		return removeForBatch(HashBasedTable.create(), uuid, id);
	}

	public void removeLocal(UUID uuid, ResourceLocation id) {
		handleChanged(removeForBatch(uuid, id), true, null);
	}

	public void remove(UUID uuid, ResourceLocation id) {
		handleChanged(removeForBatch(uuid, id), false, null);
	}

	public void remove(UUID sender, UUID uuid, ResourceLocation id) {
		handleChanged(removeForBatch(uuid, id), false, sender);
	}

	public Table<UUID, ResourceLocation, Landmark> removeAllForBatch(Table<UUID, ResourceLocation, Landmark> changed, Predicate<Landmark> predicate) {
		if (Surveyor.CONFIG.landmarks == SystemMode.FROZEN) return null;
		Table<UUID, ResourceLocation, Landmark> toRemove = HashBasedTable.create(landmarks);
		toRemove.values().removeIf(predicate.negate());
		toRemove.cellSet().forEach(c -> removeForBatch(changed, c.getRowKey(), c.getColumnKey()));
		return changed;
	}

	public Table<UUID, ResourceLocation, Landmark> removeAllForBatch(Predicate<Landmark> predicate) {
		return removeAllForBatch(HashBasedTable.create(), predicate);
	}

	public void removeAll(Predicate<Landmark> predicate) {
		handleChanged(removeAllForBatch(predicate), false, null);
	}

	public int save(File folder) {
		if (isDirty()) {
			File landmarksFile = new File(folder, "landmarks.dat");
			CompoundTag landmarksCompound = writeNbt(new CompoundTag());
			SafeNbtWriter.write(landmarksFile.toPath(), landmarksCompound);
			dirty = false;
			return landmarks.size();
		}
		return 0;
	}

	public Table<UUID, ResourceLocation, Landmark> readUpdatePacket(SyncLandmarksAddedPacket packet, @Nullable ServerPlayer sender) {
		Table<UUID, ResourceLocation, Landmark> changed = HashBasedTable.create();
		packet.landmarks().values().forEach(landmark -> {
			boolean waypoint = !landmark.owner().equals(GLOBAL);
			if ((sender == null || Surveyor.canModify(landmark.owner(), sender)) && (waypoint && Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.SOLO) || !waypoint && Surveyor.CONFIG.networking.landmarks.atLeast(NetworkMode.SOLO))) {
				putForBatch(changed, landmark);
			}
		});
		if (!changed.isEmpty()) handleChanged(changed, sender == null, sender == null ? null : Surveyor.getUuid(sender));
		return changed;
	}

	public Table<UUID, ResourceLocation, Landmark>  readUpdatePacket(SyncLandmarksRemovedPacket packet, @Nullable ServerPlayer sender) {
		Table<UUID, ResourceLocation, Landmark> changed = HashBasedTable.create();
		packet.landmarks().forEach((uuid, id) -> {
			Landmark landmark = get(uuid, id);
			if (landmark == null) return;
			boolean waypoint = !landmark.owner().equals(GLOBAL);
			if ((sender == null || Surveyor.canModify(landmark.owner(), sender)) && ((waypoint && Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.SOLO)) || (!waypoint && Surveyor.CONFIG.networking.landmarks.atLeast(NetworkMode.SOLO)))) {
				removeForBatch(changed, uuid, id);
			}
		});
		if (!changed.isEmpty()) handleChanged(changed, sender == null, sender == null ? null : Surveyor.getUuid(sender));
		return changed;
	}

	public SyncLandmarksAddedPacket createUpdatePacket(Multimap<UUID, ResourceLocation> keySet) {
		Table<UUID, ResourceLocation, Landmark> updated = HashBasedTable.create();
		keySet.forEach((uuid, id) -> updated.put(uuid, id, get(uuid, id)));
		return new SyncLandmarksAddedPacket(summary.dimension(), updated);
	}

	public boolean isDirty() {
		return dirty && Surveyor.CONFIG.landmarks != SystemMode.FROZEN;
	}

	private void dirty() {
		dirty = true;
	}

	public void tryMigrateXaeros(boolean handleChanged) {
		if (!FMLEnvironment.dist.isClient()) return;
		File saveFolder = SurveyorClient.getXaerosSavePath(summary.dimension());
		if (saveFolder != null) {
			Table<UUID, ResourceLocation, Landmark> changed = HashBasedTable.create();
			Surveyor.LOGGER.info("[Surveyor] Attempting to parse xaero's waypoints from {}/{}", saveFolder.getParentFile().getName(), saveFolder.getName());
			try {
				Files.createFile(saveFolder.toPath().resolve(".surveyor_migrated"));
				for (File file : Objects.requireNonNullElse(saveFolder.listFiles(f -> f.getName().endsWith(".txt")), new File[0])) {
					for (String line : Files.readAllLines(file.toPath())) {
						try {
							if (line.startsWith("waypoint:")) {
								String[] split = line.split(":");
								BlockPos pos = new BlockPos(Integer.parseInt(split[3]), split[4].equals("~") ? 0 : Integer.parseInt(split[4]), Integer.parseInt(split[5]));
								ResourceLocation id = ResourceLocation.fromNamespaceAndPath("xaeros", "waypoint/%d/%d/%d".formatted(pos.getX(), pos.getY(), pos.getZ()));
								putForBatch(changed, Landmark.create(SurveyorClient.getClientUuid(), id, b -> b
									.add(LandmarkComponentTypes.POS, pos)
									.add(LandmarkComponentTypes.COLOR, XAERO_COLORS[Integer.parseInt(split[6])])
									.add(LandmarkComponentTypes.NAME, Component.literal(split[1]))
								));
								dirty = true;
							}
						} catch (Exception e) {
							Surveyor.LOGGER.error("[Surveyor] Error parsing xaeros waypoint: {}", line, e);
						}
					}
				}
			} catch (Exception e) {
				Surveyor.LOGGER.error("[Surveyor] Error parsing xaeros data from {}", saveFolder, e);
			}
			Surveyor.LOGGER.info("[Surveyor] Migrated {} waypoints from xaeros data.", changed.size());
			if (handleChanged) handleChanged(changed, Surveyor.CONFIG.networking.landmarks.atMost(NetworkMode.SOLO), null);
		}
	}
}
