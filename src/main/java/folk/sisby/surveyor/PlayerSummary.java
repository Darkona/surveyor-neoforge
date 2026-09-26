package folk.sisby.surveyor;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.packet.S2CStructuresAddedPacket;
import folk.sisby.surveyor.packet.S2CUpdateRegionPacket;
import folk.sisby.surveyor.structure.WorldStructures;
import folk.sisby.surveyor.terrain.WorldTerrain;
import folk.sisby.surveyor.util.ArrayUtil;
import folk.sisby.surveyor.util.RegionPos;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import java.util.function.Predicate;
import folk.sisby.surveyor.packet.S2CPacket;

public interface PlayerSummary {
	String KEY_DATA = "surveyor";
	String KEY_USERNAME = "username";

	static PlayerSummary of(ServerPlayer player) {
		return ((SurveyorPlayer) player).surveyor$getSummary();
	}

	static PlayerSummary of(UUID uuid, MinecraftServer server) {
		return ServerSummary.of(server).getPlayer(uuid);
	}

	SurveyorExploration exploration();

	String username();

	ResourceKey<Level> dimension();

	Vec3 pos();

	float yaw();

	int viewDistance();

	boolean online();


	default void copyFrom(PlayerSummary oldSummary) {
		exploration().copyFrom(oldSummary.exploration());
	}

	record OfflinePlayerSummary(SurveyorExploration exploration, String username, ResourceKey<Level> dimension, Vec3 pos, float yaw, boolean online) implements PlayerSummary {
		public OfflinePlayerSummary(UUID uuid, CompoundTag nbt, boolean online) {
			this(
				OfflinePlayerExploration.from(uuid, nbt.getCompound(KEY_DATA)),
				nbt.getCompound(KEY_DATA).contains(KEY_USERNAME) ? nbt.getCompound(KEY_DATA).getString(KEY_USERNAME) : "???",
				ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("Dimension"))),
				nbt.contains("Pos", Tag.TAG_LIST) ? ArrayUtil.toVec3d(nbt.getList("Pos", Tag.TAG_DOUBLE).stream().mapToDouble(e -> ((DoubleTag) e).getAsDouble()).toArray()) : new Vec3(0, 0, 0),
				nbt.getList("Rotation", Tag.TAG_FLOAT).getFloat(0),
				online
			);
		}

		public OfflinePlayerSummary(ServerPlayer player) {
			this(
				PlayerSummary.OfflinePlayerSummary.OfflinePlayerExploration.ofMerged(Set.of(SurveyorExploration.of(player))),
				player.getGameProfile().getName(),
				player.level().dimension(),
				player.position(),
				player.getYRot(),
				true
			);
		}

		public static void writeBuf(PlayerSummary summary, RegistryFriendlyByteBuf buf) {
			buf.writeUtf(summary.username());
			buf.writeResourceKey(summary.dimension());
			buf.writeDouble(summary.pos().x);
			buf.writeDouble(summary.pos().y);
			buf.writeDouble(summary.pos().z);
			buf.writeFloat(summary.yaw());
			buf.writeBoolean(summary.online());
		}

		public static PlayerSummary readBuf(RegistryFriendlyByteBuf buf) {
			return new OfflinePlayerSummary(
				null,
				buf.readUtf(),
				buf.readResourceKey(Registries.DIMENSION),
				new Vec3(
					buf.readDouble(),
					buf.readDouble(),
					buf.readDouble()
				),
				buf.readFloat(),
				buf.readBoolean()
			);
		}

		@Override
		public int viewDistance() {
			return 0;
		}

		public record OfflinePlayerExploration(Set<UUID> sharedPlayers, Table<ResourceKey<Level>, RegionPos, BitSet> chunks, Table<ResourceKey<Level>, ResourceKey<Structure>, LongSet> starts, boolean personal) implements SurveyorExploration {
			public static OfflinePlayerExploration empty(UUID uuid) {
				return new OfflinePlayerExploration(Set.of(uuid), HashBasedTable.create(), HashBasedTable.create(), true);
			}

			public static OfflinePlayerExploration ofMerged(Set<SurveyorExploration> explorations) {
				Set<UUID> sharedPlayers = new HashSet<>();
				Table<ResourceKey<Level>, RegionPos, BitSet> chunks = HashBasedTable.create();
				Table<ResourceKey<Level>, ResourceKey<Structure>, LongSet> starts = HashBasedTable.create();
				OfflinePlayerExploration outExploration = new OfflinePlayerExploration(sharedPlayers, chunks, starts, false);
				for (SurveyorExploration exploration : explorations) {
					sharedPlayers.addAll(exploration.sharedPlayers());
					exploration.chunks().cellSet().forEach(c -> outExploration.mergeRegion(c.getRowKey(), c.getColumnKey(), c.getValue(), false));
					exploration.starts().cellSet().forEach(c -> outExploration.mergeStructures(c.getRowKey(), c.getColumnKey(), c.getValue()));
				}
				return outExploration;
			}

			public static SurveyorExploration from(UUID uuid, CompoundTag nbt) {
				OfflinePlayerExploration mutable = new OfflinePlayerExploration(new HashSet<>(Set.of(uuid)), HashBasedTable.create(), HashBasedTable.create(), true);
				mutable.read(nbt);
				return mutable;
			}
		}
	}

	class PlayerEntitySummary implements PlayerSummary {
		protected final Player player;

		public PlayerEntitySummary(Player player) {
			this.player = player;
		}

		@Override
		public SurveyorExploration exploration() {
			return null;
		}

		@Override
		public String username() {
			return player.getGameProfile().getName();
		}

		@Override
		public ResourceKey<Level> dimension() {
			return player.level().dimension();
		}

		@Override
		public Vec3 pos() {
			return player.position();
		}

		@Override
		public float yaw() {
			return player.getYRot();
		}

		@Override
		public int viewDistance() {
			return 0;
		}

		@Override
		public boolean online() {
			return true;
		}
	}

	class ServerPlayerEntitySummary extends PlayerEntitySummary implements PlayerSummary {
		private final ServerPlayerExploration exploration;
		private boolean positionSynced = false;
		private long syncedPositionState;

		public ServerPlayerEntitySummary(ServerPlayer player) {
			super(player);
			this.exploration = new ServerPlayerExploration(player, Tables.synchronizedTable(HashBasedTable.create()), Tables.synchronizedTable(HashBasedTable.create()));
		}

		@Override
		public SurveyorExploration exploration() {
			return exploration;
		}

		public boolean markPositionSynced() {
			long state = HashCommon.mix((long) player.level().dimension().hashCode());
			state = HashCommon.mix(state ^ Double.doubleToLongBits(player.getX()));
			state = HashCommon.mix(state ^ Double.doubleToLongBits(player.getY()));
			state = HashCommon.mix(state ^ Double.doubleToLongBits(player.getZ()));
			state = HashCommon.mix(state ^ Float.floatToIntBits(player.getYRot()));
			if (positionSynced && state == syncedPositionState) return false;
			positionSynced = true;
			syncedPositionState = state;
			return true;
		}

		@Override
		public int viewDistance() {
			return ((ServerPlayer) this.player).requestedViewDistance();
		}

		private boolean viewCached = false;
		private long viewState;
		private long viewHit;

		public long viewHit(Supplier<BlockPos> raycast) {
			ServerPlayer serverPlayer = (ServerPlayer) player;
			long state = HashCommon.mix(BlockPos.containing(serverPlayer.getEyePosition()).asLong());
			state = HashCommon.mix(state ^ Float.floatToIntBits(serverPlayer.getYRot()));
			state = HashCommon.mix(state ^ Float.floatToIntBits(serverPlayer.getXRot()));
			state = HashCommon.mix(state ^ serverPlayer.level().dimension().hashCode() ^ ((long) serverPlayer.requestedViewDistance() << 32));
			if (!viewCached || state != viewState) {
				viewHit = raycast.get().asLong();
				viewState = state;
				viewCached = true;
			}
			return viewHit;
		}

		public void copyExploration(ServerPlayerEntitySummary oldSummary) {
			exploration.copyFrom(oldSummary.exploration);
		}

		public void read(CompoundTag nbt) {
			exploration.read(nbt.getCompound(KEY_DATA));
		}

		public void writeNbt(CompoundTag nbt) {
			CompoundTag surveyorNbt = new CompoundTag();
			exploration.write(surveyorNbt);
			surveyorNbt.putString(PlayerSummary.KEY_USERNAME, username());
			nbt.put(PlayerSummary.KEY_DATA, surveyorNbt);
		}

		public record ServerPlayerExploration(ServerPlayer player, Table<ResourceKey<Level>, RegionPos, BitSet> chunks, Table<ResourceKey<Level>, ResourceKey<Structure>, LongSet> starts) implements SurveyorExploration {
			@Override
			public Set<UUID> sharedPlayers() {
				return Set.of(Surveyor.getUuid(player));
			}

			@Override
			public boolean personal() {
				return true;
			}

			@Override
			public void mergeRegion(ResourceKey<Level> dimension, RegionPos regionPos, BitSet chunks, boolean updateClient) { // This method is currently unused for server players, but its implemented anyway
				WorldSummary summary = WorldSummary.of(player.getServer().getLevel(dimension));
				WorldTerrain terrain = summary == null ? null : summary.terrain();
				if (player.getServer().isSingleplayerOwner(player.getGameProfile())) {
					updateClientForMergeRegion(summary, regionPos, chunks);
				}
				if (Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SOLO)) {
					for (ServerPlayer friend : ServerSummary.of(player.getServer()).getSharingPlayers(Surveyor.getUuid(player), Surveyor.CONFIG.networking.terrain, !updateClient)) {
						SurveyorExploration friendExploration = SurveyorExploration.of(friend);
						BitSet sendSet = (BitSet) chunks.clone();
						if (friendExploration.chunks().contains(dimension, regionPos)) sendSet.andNot(friendExploration.chunks().get(dimension, regionPos));
						if (!sendSet.isEmpty() && terrain != null) S2CUpdateRegionPacket.of(dimension, friend != player, regionPos, terrain.getRegion(regionPos), sendSet).send(friend);
					}
				}
				SurveyorExploration.super.mergeRegion(dimension, regionPos, chunks, updateClient);
			}

			@Override
			public void addChunk(ResourceKey<Level> dimension, ChunkPos pos, boolean updateClient) {
				WorldSummary summary = WorldSummary.of(player.getServer().getLevel(dimension));
				// Fix: record the chunk as explored even when the terrain system is off.
				WorldTerrain terrain = summary == null ? null : summary.terrain();
				// The chunk may still be summarising off-thread (sisby-folk/surveyor#148): the packet and the map need it now.
				if (terrain != null && player.getServer().isSameThread() && player.getServer().getLevel(dimension) instanceof ServerLevel level) terrain.finishPending(level, pos);
				if (terrain != null && Surveyor.CONFIG.networking.terrain.atLeast(NetworkMode.SOLO)) {
					UUID sender = Surveyor.getUuid(player);
					Predicate<ServerPlayer> unexplored = p -> !SurveyorExploration.of(p).exploredChunk(dimension, pos);
					if (S2CPacket.hasRecipients(sender, player.getServer(), unexplored, Surveyor.CONFIG.networking.terrain, updateClient, S2CUpdateRegionPacket.ID)) {
						RegionPos regionPos = RegionPos.of(pos);
						S2CUpdateRegionPacket.of(dimension, true, regionPos, terrain.getRegion(regionPos), RegionPos.chunkToBitSet(pos))
							.send(sender, player.getServer(), unexplored, Surveyor.CONFIG.networking.terrain, updateClient);
					}
				}
				SurveyorExploration.super.addChunk(dimension, pos, updateClient);
				if (player.getServer().isSingleplayerOwner(player.getGameProfile())) updateClientForAddChunk(summary, pos);
			}

			@Override
			public void addStructure(ResourceKey<Level> dimension, ResourceKey<Structure> structureKey, ChunkPos pos) {
				WorldSummary summary = WorldSummary.of(player.getServer().getLevel(dimension));
				WorldStructures structures = summary == null ? null : summary.structures();
				if (structures != null && Surveyor.CONFIG.networking.structures.atLeast(NetworkMode.SOLO)) {
					S2CStructuresAddedPacket packet = S2CStructuresAddedPacket.of(false, structureKey, pos, structures);
					packet.send(Surveyor.getUuid(player), player.getServer(), p -> !SurveyorExploration.of(p).exploredStructure(dimension, structureKey, pos), Surveyor.CONFIG.networking.structures, false);
				}
				SurveyorExploration.super.addStructure(dimension, structureKey, pos);
				if (player.getServer().isSingleplayerOwner(player.getGameProfile())) updateClientForAddStructure(summary, structureKey, pos);
			}
		}
	}
}
