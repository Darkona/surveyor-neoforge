package folk.sisby.surveyor.packet;

import com.google.common.base.Predicates;
import folk.sisby.surveyor.ServerSummary;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.config.NetworkMode;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public interface S2CPacket extends SurveyorPacket {
	default void send(Collection<ServerPlayer> players) {
		if (players.isEmpty()) return;
		List<SurveyorPacket> split = this.toPayloads(players.iterator().next().registryAccess());
		if (split.isEmpty()) return;
		for (ServerPlayer player : players) {
			if (!player.connection.hasChannel(type()) || player.getServer().isSingleplayerOwner(player.getGameProfile())) continue;
			split.forEach(p -> PacketDistributor.sendToPlayer(player, p));
		}
	}

	default void send(ServerPlayer player) {
		send(List.of(player));
	}

	default void send(UUID sender, MinecraftServer server) {
		List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
		players.removeIf(p -> Surveyor.getUuid(p).equals(sender));
		send(players);
	}

	default void send(UUID sender, MinecraftServer server, Predicate<ServerPlayer> filter, NetworkMode mode, boolean withSelf) {
		if (mode.atMost(NetworkMode.NONE) || (sender != null && mode.atMost(NetworkMode.SOLO))) return;
		List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
		if (sender != null) {
			Set<ServerPlayer> group = ServerSummary.of(server).getSharingPlayers(sender, mode, withSelf);
			players.removeIf(p -> !group.contains(p));
		}
		players.removeIf(filter.negate());
		if (this instanceof ShareFlagged<?> sfp) {
			players.stream().filter(p -> Surveyor.getUuid(p).equals(sender)).findFirst().ifPresent(p -> {
				sfp.withShared(false).send(p);
				players.remove(p);
			});
			sfp.withShared(true).send(players);
		} else {
			send(players);
		}
	}

	static boolean hasRecipients(UUID sender, MinecraftServer server, Predicate<ServerPlayer> filter, NetworkMode mode, boolean withSelf, CustomPacketPayload.Type<?> type) {
		if (mode.atMost(NetworkMode.NONE) || (sender != null && mode.atMost(NetworkMode.SOLO))) return false;
		Set<ServerPlayer> group = sender == null ? null : ServerSummary.of(server).getSharingPlayers(sender, mode, withSelf);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (group != null && !group.contains(player)) continue;
			if (!filter.test(player)) continue;
			if (!player.connection.hasChannel(type) || server.isSingleplayerOwner(player.getGameProfile())) continue;
			return true;
		}
		return false;
	}

	default void send(UUID sender, MinecraftServer server, NetworkMode mode, boolean withSelf) {
		send(sender, server, Predicates.alwaysTrue(), mode, withSelf);
	}

	default void send(MinecraftServer server) {
		send(server.getPlayerList().getPlayers());
	}
}
