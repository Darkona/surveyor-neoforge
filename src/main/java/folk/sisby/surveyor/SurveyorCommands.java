package folk.sisby.surveyor;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.DataResult;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentType;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.structure.WorldStructures;
import folk.sisby.surveyor.terrain.WorldTerrain;
import folk.sisby.surveyor.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.text.WordUtils;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class SurveyorCommands {
	private static final Multimap<UUID, UUID> requests = HashMultimap.create();

	public static MutableComponent prefix() {
		return Component.literal("").append(Component.literal("[Surveyor] ").withStyle(ChatFormatting.DARK_RED));
	}

	public static MutableComponent indent() {
		return Component.literal("").append(Component.literal("|| ").withStyle(ChatFormatting.DARK_RED));
	}

	private static void informGroup(ServerPlayer player, Set<PlayerSummary> group, Consumer<Component> feedback) {
		feedback.accept(
			Component.literal("You're sharing your map with ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal("%d".formatted(group.size() - 1)).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" other" + (group.size() - 1 > 1 ? " players:" : " player:")).withStyle(ChatFormatting.GOLD))
		);
		feedback.accept(
			TextUtil.highlightStrings(group.stream().map(PlayerSummary::username).filter(u -> !u.equals(player.getGameProfile().getName())).toList(), s -> ChatFormatting.WHITE).withStyle(ChatFormatting.GOLD)
		);
	}

	private static boolean playerMissing(@Nullable ServerPlayer player, Consumer<Component> feedback) {
		if (player == null) {
			feedback.accept(prefix().append(Component.literal("Can't run this command as a non-player!")));
			return true;
		}
		return false;
	}

	private static int informGlobal(MinecraftServer server, @Nullable ServerPlayer player, @Nullable SurveyorExploration exploration, Consumer<Component> feedback) {
		feedback.accept(prefix().append(Component.literal("The server has global sharing enabled!").withStyle(ChatFormatting.YELLOW)));
		feedback.accept(prefix().append(Component.literal("You can't leave or modify the global sharing group!").withStyle(ChatFormatting.YELLOW)));
		if (playerMissing(player, feedback)) return 0;
		informGroup(player, ServerSummary.of(server).groupPlayers(Surveyor.getUuid(player)), feedback);
		return 0;
	}

	private static int info(MinecraftServer server, @Nullable ServerPlayer player, @Nullable SurveyorExploration exploration, Consumer<Component> feedback) {
		Set<PlayerSummary> group = player == null ? null : ServerSummary.of(server).groupPlayers(Surveyor.getUuid(player));
		SurveyorExploration groupExploration = player == null ? null : SurveyorExploration.ofShared(player);
		Set<Landmark> landmarks = new HashSet<>();
		Set<Landmark> waypoints = new HashSet<>();
		Set<Landmark> groupLandmarks = new HashSet<>();
		Set<Landmark> groupWaypoints = new HashSet<>();
		int chunks = exploration == null ? 0 : exploration.chunkCount();
		int structures = exploration == null ? 0 : exploration.structureCount();
		for (ServerLevel world : server.getAllLevels()) {
			WorldSummary summary = WorldSummary.of(world);
			if (summary == null) continue;
			WorldLandmarks worldLandmarks = summary.landmarks();
			if (worldLandmarks != null) {
				worldLandmarks.asMap(exploration).values().forEach(landmark -> (landmark.owner().equals(WorldLandmarks.GLOBAL) ? landmarks : waypoints).add(landmark));
				if (groupExploration != null) worldLandmarks.asMap(groupExploration).values().forEach(landmark -> (landmark.owner().equals(WorldLandmarks.GLOBAL) ? groupLandmarks : groupWaypoints).add(landmark));
			}
			if (exploration == null) {
				WorldTerrain worldTerrain = summary.terrain();
				WorldStructures worldStructures = summary.structures();
				if (worldTerrain != null) chunks += worldTerrain.bitSet(null).values().stream().mapToInt(BitSet::cardinality).sum();
				if (worldStructures != null) structures += worldStructures.keySet(null).size();
			}
		}
		feedback.accept(prefix().append(Component.literal(player == null ? "Surveyor Data Summary:" : "Map Exploration Summary:")));
		feedback.accept(
			indent()
				.append(Component.literal(player == null ? "Mapped " : "You've explored ").withStyle(ChatFormatting.AQUA))
				.append(Component.literal("%d".formatted(chunks)).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" total chunks!").withStyle(ChatFormatting.AQUA))
				.append(
					group == null || group.size() <= 1 ? Component.empty() :
						Component.literal(" (").withStyle(ChatFormatting.AQUA)
							.append(Component.literal("%d".formatted(groupExploration.chunkCount())).withStyle(ChatFormatting.WHITE))
							.append(Component.literal(" with friends)").withStyle(ChatFormatting.AQUA))
				)
		);
		feedback.accept(
			indent()
				.append(Component.literal(player == null ? "Mapped " : "You've discovered ").withStyle(ChatFormatting.LIGHT_PURPLE))
				.append(Component.literal("%d".formatted(structures)).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" structures!").withStyle(ChatFormatting.LIGHT_PURPLE))
				.append(
					group == null || group.size() <= 1 ? Component.empty() :
						Component.literal(" (").withStyle(ChatFormatting.LIGHT_PURPLE)
							.append(Component.literal("%d".formatted(groupExploration.structureCount())).withStyle(ChatFormatting.WHITE))
							.append(Component.literal(" with friends)").withStyle(ChatFormatting.LIGHT_PURPLE))
				)
		);
		feedback.accept(
			indent()
				.append(Component.literal(player == null ? "Mapped " : "You've discovered ").withStyle(ChatFormatting.GREEN))
				.append(Component.literal("%d".formatted(landmarks.size())).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" landmarks!").withStyle(ChatFormatting.GREEN))
				.append(
					group == null || group.size() <= 1 ? Component.empty() :
						Component.literal(" (").withStyle(ChatFormatting.GREEN)
							.append(Component.literal("%d".formatted(groupLandmarks.size())).withStyle(ChatFormatting.WHITE))
							.append(Component.literal(" with friends)").withStyle(ChatFormatting.GREEN))
				)
		);
		feedback.accept(
			indent()
				.append(Component.literal(player == null ? "Recorded " : "...and created ").withStyle(ChatFormatting.GREEN))
				.append(Component.literal("%d".formatted(waypoints.size())).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" waypoints!").withStyle(ChatFormatting.GREEN))
				.append(
					group == null || group.size() <= 1 ? Component.empty() :
						Component.literal(" (").withStyle(ChatFormatting.GREEN)
							.append(Component.literal("%d".formatted(groupWaypoints.size())).withStyle(ChatFormatting.WHITE))
							.append(Component.literal(" with friends)").withStyle(ChatFormatting.GREEN))
				)
		);
		if (group != null && group.size() > 1) {
			informGroup(player, group, feedback);
		}
		return 1;
	}

	private static int share(MinecraftServer server, @Nullable ServerPlayer player, Consumer<Component> feedback, String username) {
		if (playerMissing(player, feedback)) return 0;
		ServerSummary summary = ServerSummary.of(server);
		ServerPlayer sharePlayer = server.getPlayerList().getPlayerByName(username);
		if (sharePlayer == null) {
			feedback.accept(prefix().append(Component.literal("Can't find an online player named ").withStyle(ChatFormatting.YELLOW)).append(Component.literal(username).withStyle(ChatFormatting.WHITE)).append(Component.literal(".").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		if (sharePlayer == player) {
			feedback.accept(prefix().append(Component.literal("You can't share map exploration with yourself!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		if (requests.containsEntry(Surveyor.getUuid(player), Surveyor.getUuid(sharePlayer))) { // Accept Request
			if (summary.groupSize(Surveyor.getUuid(player)) > 1 && summary.groupSize(Surveyor.getUuid(sharePlayer)) > 1) {
				feedback.accept(prefix().append(Component.literal("You're in a group! leave your group first with:").withStyle(ChatFormatting.YELLOW)));
				feedback.accept(prefix().append(Component.literal("/surveyor unshare").withStyle(ChatFormatting.GOLD)));
				return 0;
			}
			requests.removeAll(Surveyor.getUuid(player)); // clear all other requests
			ServerSummary.of(player.getServer()).joinGroup(Surveyor.getUuid(player), Surveyor.getUuid(sharePlayer));
			feedback.accept(prefix().append(Component.literal("You're now sharing map exploration with ").withStyle(ChatFormatting.GREEN)).append(Component.literal("%d".formatted(summary.groupSize(Surveyor.getUuid(player)) - 1)).withStyle(ChatFormatting.WHITE)).append(Component.literal((summary.groupSize(Surveyor.getUuid(player)) - 1) > 1 ? " players:" : " player:").withStyle(ChatFormatting.GREEN)));
			feedback.accept(TextUtil.highlightStrings(summary.groupPlayers(Surveyor.getUuid(player)).stream().map(PlayerSummary::username).filter(u -> !u.equals(player.getGameProfile().getName())).toList(), s -> ChatFormatting.WHITE).withStyle(ChatFormatting.GREEN));
			for (ServerPlayer friend : summary.getSharingPlayers(Surveyor.getUuid(player), NetworkMode.GROUP, false)) {
				friend.sendSystemMessage(prefix().append(player.getDisplayName().copy().withStyle(ChatFormatting.WHITE)).append(Component.literal(" is now sharing their map with you.").withStyle(ChatFormatting.AQUA)));
				friend.sendSystemMessage(prefix().append(Component.literal("You're now sharing map exploration with ").withStyle(ChatFormatting.AQUA)).append(Component.literal("%d".formatted(summary.groupSize(Surveyor.getUuid(player)) - 1)).withStyle(ChatFormatting.WHITE)).append(Component.literal((summary.groupSize(Surveyor.getUuid(player)) - 1) > 1 ? " players:" : " player:").withStyle(ChatFormatting.AQUA)));
				friend.sendSystemMessage(TextUtil.highlightStrings(summary.groupPlayers(Surveyor.getUuid(player)).stream().map(PlayerSummary::username).filter(u -> !u.equals(friend.getGameProfile().getName())).toList(), s -> ChatFormatting.WHITE).withStyle(ChatFormatting.AQUA));
			}
			return 1;
		} else if (!requests.containsEntry(Surveyor.getUuid(sharePlayer), Surveyor.getUuid(player))) { // Make Request
			requests.put(Surveyor.getUuid(sharePlayer), Surveyor.getUuid(player));
			feedback.accept(prefix().append(Component.literal("Share request sent to ").withStyle(ChatFormatting.GREEN)).append(sharePlayer.getDisplayName().copy().withStyle(ChatFormatting.WHITE)).append(Component.literal(".").withStyle(ChatFormatting.GREEN)));
			sharePlayer.sendSystemMessage(prefix().append(player.getDisplayName().copy().withStyle(ChatFormatting.WHITE)).append(Component.literal(" wants to share map exploration!").withStyle(ChatFormatting.AQUA)));
			if (summary.groupSize(Surveyor.getUuid(player)) <= 1 && summary.groupSize(Surveyor.getUuid(sharePlayer)) <= 1) { // Creating a group
				feedback.accept(prefix().append(Component.literal("If accepted, you'll share your map exploration.").withStyle(ChatFormatting.GREEN)));
				sharePlayer.sendSystemMessage(prefix().append(Component.literal("To share your explored map area, enter:").withStyle(ChatFormatting.AQUA)));
			} else if (summary.groupSize(Surveyor.getUuid(player)) <= 1) { // Joining their group
				feedback.accept(prefix().append(Component.literal("If accepted, you'll share with their group of ").withStyle(ChatFormatting.GREEN)).append(Component.literal("%d".formatted(summary.groupSize(Surveyor.getUuid(sharePlayer)))).withStyle(ChatFormatting.WHITE)).append(Component.literal(".").withStyle(ChatFormatting.GREEN)));
				sharePlayer.sendSystemMessage(prefix().append(Component.literal("To share your group of ").append(Component.literal("%d".formatted(summary.groupSize(Surveyor.getUuid(sharePlayer)))).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.AQUA)).append(Component.literal(", enter:").withStyle(ChatFormatting.AQUA)));
			} else { // Sharing your group
				feedback.accept(prefix().append(Component.literal("If accepted, they'll share with your group of ").withStyle(ChatFormatting.GREEN)).append(Component.literal("%d".formatted(summary.groupSize(Surveyor.getUuid(player)))).withStyle(ChatFormatting.WHITE)).append(Component.literal(".").withStyle(ChatFormatting.GREEN)));
				sharePlayer.sendSystemMessage(prefix().append(Component.literal("To share with their group of ").append(Component.literal("%d".formatted(summary.groupSize(Surveyor.getUuid(player)))).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.AQUA)).append(Component.literal(", enter:").withStyle(ChatFormatting.AQUA)));
			}
			sharePlayer.sendSystemMessage(prefix().append(Component.literal("/surveyor share %s".formatted(player.getGameProfile().getName())).withStyle(ChatFormatting.GOLD)));
			return 1;
		} else {
			feedback.accept(prefix().append(Component.literal("You've already sent this player a share request!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
	}

	private static int unshare(MinecraftServer server, @Nullable ServerPlayer player, @Nullable SurveyorExploration exploration, Consumer<Component> feedback) {
		if (playerMissing(player, feedback)) return 0;
		ServerSummary summary = ServerSummary.of(server);
		int shareNumber = summary.groupSize(Surveyor.getUuid(player)) - 1;
		if (shareNumber == 0) {
			feedback.accept(prefix().append(Component.literal("You're not sharing map exploration with anyone!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		} else {
			Set<ServerPlayer> friends = summary.getSharingPlayers(Surveyor.getUuid(player), NetworkMode.GROUP, false);
			ServerSummary.of(player.getServer()).leaveGroup(Surveyor.getUuid(player));
			feedback.accept(prefix().append(Component.literal("Stopped sharing map exploration with ").withStyle(ChatFormatting.GREEN)).append(Component.literal("%d".formatted(shareNumber)).withStyle(ChatFormatting.WHITE)).append(Component.literal(shareNumber > 1 ? " players." : " player.").withStyle(ChatFormatting.GREEN)));
			for (ServerPlayer friend : friends) {
				int groupSize = summary.groupSize(Surveyor.getUuid(friend)) - 1;
				friend.sendSystemMessage(prefix().append(player.getDisplayName().copy().withStyle(ChatFormatting.WHITE)).append(Component.literal(" is no longer sharing with you.").withStyle(ChatFormatting.AQUA)));
				friend.sendSystemMessage(prefix().append(Component.literal("You're now sharing map exploration with ").withStyle(ChatFormatting.AQUA)).append(Component.literal("%d".formatted(groupSize)).withStyle(ChatFormatting.WHITE)).append(Component.literal(groupSize == 0 ? " players." : groupSize > 1 ? " players:" : " player:").withStyle(ChatFormatting.AQUA)));
				if (groupSize > 0) friend.sendSystemMessage(TextUtil.highlightStrings(summary.groupPlayers(Surveyor.getUuid(friend)).stream().map(PlayerSummary::username).filter(u -> !u.equals(friend.getGameProfile().getName())).toList(), s -> ChatFormatting.WHITE).withStyle(ChatFormatting.AQUA));
			}
			return 1;
		}
	}

	private static int getLandmarks(MinecraftServer server, @Nullable ServerPlayer player, Consumer<Component> feedback, boolean global) {
		Map<ResourceLocation, Collection<Landmark>> dimensionLandmarks = new LinkedHashMap<>();
		boolean op = player == null || player.hasPermissions(2);
		for (ServerLevel world : server.getAllLevels()) {
			WorldLandmarks landmarks = WorldLandmarks.of(world);
			if (landmarks == null) {
				feedback.accept(prefix().append(Component.literal("The landmark system is dynamically disabled!").withStyle(ChatFormatting.YELLOW)));
				return 0;
			}
			NetworkMode mode = global ? Surveyor.CONFIG.networking.landmarks : Surveyor.CONFIG.networking.waypoints;
			Table<UUID, ResourceLocation, Landmark> table = landmarks.asMap(op || mode.atLeast(NetworkMode.SERVER) ? null : mode.atLeast(NetworkMode.GROUP) ? SurveyorExploration.ofShared(player) : SurveyorExploration.of(player));
			if (global) {
				if (table.containsRow(WorldLandmarks.GLOBAL)) {
					dimensionLandmarks.put(world.dimension().location(), table.row(WorldLandmarks.GLOBAL).values());
				}
			} else {
				table.rowKeySet().remove(WorldLandmarks.GLOBAL);
				dimensionLandmarks.put(world.dimension().location(), table.values());
			}
		}
		int numLandmarks = dimensionLandmarks.values().stream().mapToInt(Collection::size).sum();
		feedback.accept(prefix().append(Component.literal("%s %d %s:".formatted(op ? "There are" : "You've discovered", numLandmarks, global ? "Landmarks" : "Waypoints"))));
		for (ResourceLocation dimension : dimensionLandmarks.keySet()) {
			Collection<Landmark> landmarks = dimensionLandmarks.get(dimension);
			if (!landmarks.isEmpty()) feedback.accept(indent().append(indent()).append(Component.literal("%s:".formatted(WordUtils.capitalize(dimension.getPath().replaceAll("[/_-]", " "))))));
			for (Landmark landmark : landmarks) {
				Component idText = Component.empty().append(Component.literal("%s:".formatted(landmark.id().getNamespace())).withStyle(ChatFormatting.GRAY)).append(Component.literal(landmark.id().getPath()));
				Integer color = landmark.get(LandmarkComponentTypes.COLOR);
				String command = global ? "/landmarks view %s %s".formatted(dimension, landmark.id()) : "/waypoints view %s %s%s".formatted(dimension, landmark.id(), player != null && landmark.owner().equals(Surveyor.getUuid(player)) ? "" : " " + landmark.owner());
				feedback.accept(
					indent()
						.append(player == null || global ? Component.empty() : Component.literal("%s | ".formatted(Optional.ofNullable(ServerSummary.of(server).getPlayer(landmark.owner())).map(PlayerSummary::username).orElse(landmark.owner().toString()))).withStyle(ChatFormatting.GRAY))
						.append(player == null && landmark.contains(LandmarkComponentTypes.NAME) ? idText.copy().append(" ") : Component.empty())
						.append((landmark.contains(LandmarkComponentTypes.NAME) ? Component.literal("\"").append(landmark.get(LandmarkComponentTypes.NAME)).append("\"") : idText).copy().withStyle(s -> s
							.withColor(color == null ? 0xFFFFFF : 0xFFFFFF & color)
							.withHoverEvent(player == null ? null : new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.empty()
								.append(global || landmark.owner().equals(Surveyor.getUuid(player)) ? Component.empty() : Component.empty().append(Component.literal("owner: ").withStyle(ChatFormatting.AQUA)).append(landmark.owner().toString()).append("\n"))
								.append(Component.literal("id: ").withStyle(ChatFormatting.AQUA)).append(idText).append("\n")
								.append(ComponentUtils.formatList(landmark.toText(), Component.nullToEmpty("\n"))).append("\n").append(Component.literal(command).withStyle(ChatFormatting.AQUA))))
							.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)))
						)
				);
			}
		}
		return numLandmarks;
	}

	private static int viewLandmark(@Nullable ServerPlayer player, Consumer<Component> feedback, ServerLevel world, UUID owner, ResourceLocation id, boolean raw) {
		WorldLandmarks landmarks = WorldLandmarks.of(world);
		if (landmarks == null) {
			feedback.accept(prefix().append(Component.literal("The landmark system is dynamically disabled!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		Landmark landmark = landmarks.get(owner, id);
		if (landmark == null) {
			feedback.accept(prefix().append(Component.literal("No landmark exists of that id!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		String command = landmark.owner().equals(WorldLandmarks.GLOBAL) ? "/landmarks remove %s %s".formatted(world.dimension().location(), landmark.id()) : "/waypoints remove %s %s%s".formatted(world.dimension().location(), landmark.id(), (player == null || !landmark.owner().equals(Surveyor.getUuid(player)) ? " " + landmark.owner() : ""));
		feedback.accept(prefix()
			.append(Component.literal(owner.equals(WorldLandmarks.GLOBAL) ? "Landmark " : "Waypoint ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(id.toString()))
			.append(Component.literal(" "))
			.append(!Surveyor.canModify(landmark.owner(), player) ? Component.empty() : Component.empty()
				.append(Component.literal("<").withStyle(ChatFormatting.GRAY))
				.append(Component.literal("remove").withStyle(ChatFormatting.AQUA).withStyle(s -> s
					.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command).withStyle(ChatFormatting.AQUA)))
					.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
				))
				.append(Component.literal(">").withStyle(ChatFormatting.GRAY))
			)
		);
		if (raw) {
			feedback.accept(indent().append(Component.literal(landmark.toNbt(world.registryAccess()).toString())));
		} else {
			landmark.toText().forEach(t -> feedback.accept(indent().append(t)));
		}
		return 1;
	}

	private static int removeLandmark(@Nullable ServerPlayer player, Consumer<Component> feedback, ServerLevel world, UUID owner, ResourceLocation id) {
		WorldLandmarks landmarks = WorldLandmarks.of(world);
		if (landmarks == null) {
			feedback.accept(prefix().append(Component.literal("The landmark system is dynamically disabled!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		Landmark landmark = landmarks.get(owner, id);
		if (landmark == null) {
			feedback.accept(prefix().append(Component.literal("No landmark exists of that id!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		if (!Surveyor.canModify(owner, player)) {
			feedback.accept(prefix().append(Component.literal("You don't have permission to modify that landmark!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		landmarks.remove(owner, id);
		feedback.accept(prefix()
			.append(Component.literal(owner.equals(WorldLandmarks.GLOBAL) ? "Landmark " : "Waypoint ").withStyle(ChatFormatting.GREEN))
			.append(Component.literal(id.toString()))
			.append(Component.literal(" removed successfully!").withStyle(ChatFormatting.GREEN))
		);
		return 1;
	}

	private static int trimLandmark(@Nullable ServerPlayer player, Consumer<Component> feedback, ServerLevel world, UUID owner, ResourceLocation id, ResourceLocation componentType) {
		WorldLandmarks landmarks = WorldLandmarks.of(world);
		if (landmarks == null) {
			feedback.accept(prefix().append(Component.literal("The landmark system is dynamically disabled!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		Landmark landmark = landmarks.get(owner, id);
		if (landmark == null) {
			feedback.accept(prefix().append(Component.literal("No landmark exists of that id!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		if (!Surveyor.canModify(owner, player)) {
			feedback.accept(prefix().append(Component.literal("You don't have permission to modify that landmark!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		landmark.components().remove(LandmarkComponentType.getType(componentType));
		landmarks.put(landmark);
		feedback.accept(prefix()
			.append(Component.literal(owner.equals(WorldLandmarks.GLOBAL) ? "Landmark " : "Waypoint ").withStyle(ChatFormatting.GREEN))
			.append(Component.literal(id.toString()))
			.append(Component.literal(" trimmed successfully!").withStyle(ChatFormatting.GREEN))
		);
		return 1;
	}

	private static int appendColor(@Nullable ServerPlayer player, Consumer<Component> feedback, ServerLevel world, UUID owner, ResourceLocation id, String colorString) {
		WorldLandmarks landmarks = WorldLandmarks.of(world);
		if (landmarks == null) {
			feedback.accept(prefix().append(Component.literal("The landmark system is dynamically disabled!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		Landmark landmark = landmarks.get(owner, id);
		if (landmark == null) {
			feedback.accept(prefix().append(Component.literal("No landmark exists of that id!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		if (!Surveyor.canModify(landmark.owner(), player)) {
			feedback.accept(prefix().append(Component.literal("You don't have permission to modify that landmark!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		DataResult<TextColor> color = TextColor.parseColor(colorString);
		if (color.isError()) {
			feedback.accept(prefix().append(Component.literal("Not a valid color! Use color names or hex codes").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		landmark.components().set(LandmarkComponentTypes.COLOR, color.getOrThrow().getValue());
		landmarks.put(landmark);
		feedback.accept(prefix()
			.append(Component.literal(owner.equals(WorldLandmarks.GLOBAL) ? "Landmark " : "Waypoint ").withStyle(ChatFormatting.GREEN))
			.append(Component.literal(id.toString()))
			.append(Component.literal(" appended successfully!").withStyle(ChatFormatting.GREEN))
		);
		return 1;
	}

	private static int addBlockLandmark(@Nullable ServerPlayer player, Consumer<Component> feedback, ServerLevel world, UUID owner, BlockPos pos) {
		WorldLandmarks landmarks = WorldLandmarks.of(world);
		if (landmarks == null) {
			feedback.accept(prefix().append(Component.literal("The landmark system is dynamically disabled!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		if (!Surveyor.canModify(owner, player)) {
			feedback.accept(prefix().append(Component.literal("You don't have permission to add that landmark!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		ResourceLocation id = Surveyor.id("block/%s/%s/%s".formatted(pos.getX(), pos.getY(), pos.getZ()));
		if (landmarks.contains(owner, id)) {
			feedback.accept(prefix().append(Component.literal("A landmark with this ID already exists! Replacing...").withStyle(ChatFormatting.YELLOW)));
		}
		landmarks.put(Landmark.create(owner, id, builder -> LandmarkComponentTypes.forBlock(builder, world, pos)));
		feedback.accept(prefix()
			.append(Component.literal("Added new " + (owner.equals(WorldLandmarks.GLOBAL) ? "Landmark " : "Waypoint ")).withStyle(ChatFormatting.GREEN))
			.append(Component.literal(id.toString()))
			.append(Component.literal("!").withStyle(ChatFormatting.GREEN))
		);
		return 1;
	}

	private static int addIdLandmark(@Nullable ServerPlayer player, Consumer<Component> feedback, ServerLevel world, UUID owner, ResourceLocation id, BlockPos pos, ItemInput stack, Component name, Component lore) {
		ItemStack icon;
		try {
			icon = stack.createItemStack(1, false);
		} catch (CommandSyntaxException e) {
			throw new RuntimeException(e);
		}
		WorldLandmarks landmarks = WorldLandmarks.of(world);
		if (landmarks == null) {
			feedback.accept(prefix().append(Component.literal("The landmark system is dynamically disabled!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		if (!Surveyor.canModify(owner, player)) {
			feedback.accept(prefix().append(Component.literal("You don't have permission to add that landmark!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		if (landmarks.contains(owner, id)) {
			feedback.accept(prefix().append(Component.literal("A landmark with this ID already exists! Replacing...").withStyle(ChatFormatting.YELLOW)));
		}
		landmarks.put(Landmark.create(owner, id, builder -> builder
			.add(LandmarkComponentTypes.POS, pos)
			.add(LandmarkComponentTypes.STACK, icon)
			.add(LandmarkComponentTypes.NAME, name)
			.add(LandmarkComponentTypes.LORE, lore == null ? null : List.of(lore))
		));
		feedback.accept(prefix()
			.append(Component.literal("Added new " + (owner.equals(WorldLandmarks.GLOBAL) ? "Landmark " : "Waypoint ")).withStyle(ChatFormatting.GREEN))
			.append(Component.literal(id.toString()))
			.append(Component.literal("!").withStyle(ChatFormatting.GREEN))
		);
		return 1;
	}

	private static CompletableFuture<Suggestions> suggestLandmarks(CommandContext<CommandSourceStack> c, SuggestionsBuilder b, boolean global) {
		ServerPlayer player = c.getSource().getPlayer();
		if (player == null) return b.buildFuture();
		boolean op = player.hasPermissions(2);
		NetworkMode mode = global ? Surveyor.CONFIG.networking.landmarks : Surveyor.CONFIG.networking.waypoints;
		SurveyorExploration exploration = op || mode.atLeast(NetworkMode.SERVER) ? null : mode.atLeast(NetworkMode.GROUP) ? SurveyorExploration.ofShared(player) : SurveyorExploration.of(player);
		ServerLevel world;
		try {
			world = DimensionArgument.getDimension(c, "dim");
		} catch (CommandSyntaxException e) {
			throw new RuntimeException(e);
		}
		WorldLandmarks landmarks = WorldLandmarks.of(world);
		if (landmarks == null) return b.buildFuture();
		return SharedSuggestionProvider.suggestResource(global ? landmarks.asMap(WorldLandmarks.GLOBAL, exploration).keySet() : landmarks.asMap(exploration).columnKeySet(), b);
	}

	private static CompletableFuture<Suggestions> suggestOwners(CommandContext<CommandSourceStack> c, SuggestionsBuilder b) {
		ServerPlayer player = c.getSource().getPlayer();
		if (player == null) return b.buildFuture();
		boolean op = player.hasPermissions(2);
		SurveyorExploration exploration = op || Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.SERVER) ? null : Surveyor.CONFIG.networking.waypoints.atLeast(NetworkMode.GROUP) ? SurveyorExploration.ofShared(player) : SurveyorExploration.of(player);
		ServerLevel world;
		ResourceLocation id;
		try {
			world = DimensionArgument.getDimension(c, "dim");
			id = c.getArgument("id", ResourceLocation.class);
		} catch (CommandSyntaxException e) {
			throw new RuntimeException(e);
		}
		WorldLandmarks landmarks = WorldLandmarks.of(world);
		if (landmarks == null) return b.buildFuture();
		return SharedSuggestionProvider.suggest(landmarks.asMap(exploration).columnMap().get(id).keySet().stream().map(UUID::toString), b);
	}

	public static <T> T map(CommandContext<CommandSourceStack> context, SurveyorCommandExecutor<T> executor, boolean feedback) {
		ServerPlayer player = context.getSource().getPlayer();
		SurveyorExploration exploration = player == null ? null : SurveyorExploration.of(player);
		try {
			return executor.execute(context.getSource().getServer(), player, exploration, t -> context.getSource().sendSuccess(() -> t, false));
		} catch (Exception e) {
			if (feedback) context.getSource().sendSuccess(() -> prefix().append(Component.literal("Command failed! Check log for details.").withStyle(ChatFormatting.RED)), false);
			if (feedback) Surveyor.LOGGER.error("[Surveyor] Error while executing command: {}", context.getInput(), e);
			return null;
		}
	}

	public static int execute(CommandContext<CommandSourceStack> context, SurveyorCommandExecutor<Integer> executor) {
		return Objects.requireNonNullElse(map(context, executor, true), 0);
	}

	public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
		dispatcher.register(
			Commands.literal("surveyor")
				.executes(c -> execute(c, SurveyorCommands::info))
				.then(Surveyor.CONFIG.networking.globalSharing ?
					Commands.literal("share")
						.executes(c -> execute(c, SurveyorCommands::informGlobal)) :
					Commands.literal("share")
						.then(Commands.argument("player", StringArgumentType.word())
							.suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getServer().getPlayerList().getPlayers().stream().filter(p -> c.getSource().getPlayer() != p).map(p -> p.getGameProfile().getName()), b))
							.executes(c -> execute(c, (s, p, e, f) -> share(s, p, f, c.getArgument("player", String.class))))
						)
				).then(Surveyor.CONFIG.networking.globalSharing ?
					Commands.literal("unshare")
						.executes(c -> execute(c, SurveyorCommands::informGlobal)) :
					Commands.literal("unshare")
						.executes(c -> execute(c, SurveyorCommands::unshare))
				)
		);
		dispatcher.register(
			Commands.literal("landmarks")
				.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.DISABLED)
				.executes(c -> execute(c, (s, p, e, f) -> getLandmarks(s, p, f, true)))
				.then(Commands.literal("new")
					.requires(c -> c.hasPermission(2) && Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
					.then(Commands.literal("block")
						.then(Commands.argument("pos", BlockPosArgument.blockPos())
							.then(Commands.argument("dim", DimensionArgument.dimension())
								.executes(c -> execute(c, (s, p, e, f) -> {
									try {
										return addBlockLandmark(p, f, DimensionArgument.getDimension(c, "dim"), WorldLandmarks.GLOBAL, c.getArgument("pos", WorldCoordinates.class).getBlockPos(c.getSource()));
									} catch (CommandSyntaxException ex) {
										throw new RuntimeException(ex);
									}
								}))
							)
							.executes(c -> execute(c, (s, p, e, f) -> {
								if (p == null) {
									f.accept(prefix().append(Component.literal("missing UUID argument for server console").withStyle(ChatFormatting.RED)));
									return 0;
								}
								return addBlockLandmark(p, f, p.serverLevel(), WorldLandmarks.GLOBAL, c.getArgument("pos", WorldCoordinates.class).getBlockPos(c.getSource()));
							}))
						)
					)
					.then(Commands.literal("id")
						.then(Commands.argument("dim", DimensionArgument.dimension())
							.then(Commands.argument("id", ResourceLocationArgument.id())
								.then(Commands.argument("pos", BlockPosArgument.blockPos())
									.then(Commands.argument("icon", ItemArgument.item(registryAccess))
										.then(Commands.argument("name", StringArgumentType.string())
											.then(Commands.argument("lore", ComponentArgument.textComponent(registryAccess))
												.executes(c -> execute(c, (s, p, e, f) -> {
													try {
														return addIdLandmark(p, f, DimensionArgument.getDimension(c, "dim"), WorldLandmarks.GLOBAL, c.getArgument("id", ResourceLocation.class), c.getArgument("pos", WorldCoordinates.class).getBlockPos(c.getSource()), ItemArgument.getItem(c, "icon"), Component.nullToEmpty(c.getArgument("name", String.class)), c.getArgument("lore", Component.class));
													} catch (CommandSyntaxException ex) {
														throw new RuntimeException(ex);
													}
												}))
											)
										)
									)
								)
							)
						)
					)
				)
				.then(Commands.literal("append")
					.requires(c -> c.hasPermission(2))
					.then(Commands.argument("id", ResourceLocationArgument.id())
						.suggests((c, b) -> suggestLandmarks(c, b, true))
						.then(Commands.literal("surveyor:color")
							.then(Commands.argument("color", StringArgumentType.greedyString())
								.suggests((c, s) -> SharedSuggestionProvider.suggest(ChatFormatting.getNames(true, false), s))
								.executes(c -> execute(c, (s, p, e, f) -> {
									try {
										return appendColor(p, f, DimensionArgument.getDimension(c, "dim"), WorldLandmarks.GLOBAL, c.getArgument("id", ResourceLocation.class), c.getArgument("color", String.class));
									} catch (CommandSyntaxException ex) {
										throw new RuntimeException(ex);
									}
								}))
							)
						)
					)
				)
				.then(Commands.literal("trim")
					.requires(c -> c.hasPermission(2))
					.then(Commands.argument("id", ResourceLocationArgument.id())
						.suggests((c, b) -> suggestLandmarks(c, b, true))
						.then(Commands.argument("component", ResourceLocationArgument.id())
							.suggests((c, b) -> SharedSuggestionProvider.suggestResource(LandmarkComponentType.keySet(), b))
							.executes(c -> execute(c, (s, p, e, f) -> {
								try {
									return trimLandmark(p, f, DimensionArgument.getDimension(c, "dim"), WorldLandmarks.GLOBAL, c.getArgument("id", ResourceLocation.class), c.getArgument("component", ResourceLocation.class));
								} catch (CommandSyntaxException ex) {
									throw new RuntimeException(ex);
								}
							}))
						)
					)
				)
				.then(Commands.literal("view")
					.then(Commands.argument("dim", DimensionArgument.dimension())
						.then(Commands.argument("id", ResourceLocationArgument.id())
							.suggests((c, b) -> suggestLandmarks(c, b, true))
							.executes(c -> execute(c, (s, p, e, f) -> {
								try {
									return viewLandmark(p, f, DimensionArgument.getDimension(c, "dim"), WorldLandmarks.GLOBAL, c.getArgument("id", ResourceLocation.class), false);
								} catch (CommandSyntaxException ex) {
									throw new RuntimeException(ex);
								}
							}))
						)
					)
				)
				.then(Commands.literal("raw")
					.requires(c -> Surveyor.CONFIG.debugCommands)
					.then(Commands.argument("dim", DimensionArgument.dimension())
						.then(Commands.argument("id", ResourceLocationArgument.id())
							.suggests((c, b) -> suggestLandmarks(c, b, true))
							.executes(c -> execute(c, (s, p, e, f) -> {
								try {
									return viewLandmark(p, f, DimensionArgument.getDimension(c, "dim"), WorldLandmarks.GLOBAL, c.getArgument("id", ResourceLocation.class), true);
								} catch (CommandSyntaxException ex) {
									throw new RuntimeException(ex);
								}
							}))
						)
					)
				)
				.then(Commands.literal("remove")
					.requires(c -> c.hasPermission(2) && Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
					.then(Commands.argument("dim", DimensionArgument.dimension())
						.then(Commands.argument("id", ResourceLocationArgument.id())
							.suggests((c, b) -> suggestLandmarks(c, b, true))
							.executes(c -> execute(c, (s, p, e, f) -> {
								try {
									return removeLandmark(p, f, DimensionArgument.getDimension(c, "dim"), WorldLandmarks.GLOBAL, c.getArgument("id", ResourceLocation.class));
								} catch (CommandSyntaxException ex) {
									throw new RuntimeException(ex);
								}
							}))
						)
					)
				)
		);
		dispatcher.register(
			Commands.literal("waypoints")
				.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.DISABLED)
				.executes(c -> execute(c, (s, p, e, f) -> getLandmarks(s, p, f, false)))
				.then(Commands.literal("new")
					.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
					.then(Commands.literal("block")
						.then(Commands.argument("pos", BlockPosArgument.blockPos())
							.then(Commands.argument("dim", DimensionArgument.dimension())
								.then(Commands.argument("owner", UuidArgument.uuid())
									.suggests(SurveyorCommands::suggestOwners)
									.requires(c -> c.hasPermission(2))
									.executes(c -> execute(c, (s, p, e, f) -> {
										try {
											return addBlockLandmark(p, f, DimensionArgument.getDimension(c, "dim"), UuidArgument.getUuid(c, "owner"), c.getArgument("pos", WorldCoordinates.class).getBlockPos(c.getSource()));
										} catch (CommandSyntaxException ex) {
											throw new RuntimeException(ex);
										}
									}))
								)
								.executes(c -> execute(c, (s, p, e, f) -> {
									try {
										if (p == null) {
											f.accept(prefix().append(Component.literal("missing UUID argument for server console").withStyle(ChatFormatting.RED)));
											return 0;
										}
										return addBlockLandmark(p, f, DimensionArgument.getDimension(c, "dim"), Surveyor.getUuid(p), c.getArgument("pos", WorldCoordinates.class).getBlockPos(c.getSource()));
									} catch (CommandSyntaxException ex) {
										throw new RuntimeException(ex);
									}
								}))
							)
							.executes(c -> execute(c, (s, p, e, f) -> {
								if (p == null) {
									f.accept(prefix().append(Component.literal("missing UUID argument for server console").withStyle(ChatFormatting.RED)));
									return 0;
								}
								return addBlockLandmark(p, f, p.serverLevel(), Surveyor.getUuid(p), c.getArgument("pos", WorldCoordinates.class).getBlockPos(c.getSource()));
							}))
						)
					)
					.then(Commands.literal("id")
						.then(Commands.argument("uuid", UuidArgument.uuid())
							.then(Commands.argument("dim", DimensionArgument.dimension())
								.then(Commands.argument("id", ResourceLocationArgument.id())
									.then(Commands.argument("pos", BlockPosArgument.blockPos())
										.then(Commands.argument("icon", ItemArgument.item(registryAccess))
											.then(Commands.argument("name", StringArgumentType.string())
												.then(Commands.argument("lore", ComponentArgument.textComponent(registryAccess))
													.executes(c -> execute(c, (s, p, e, f) -> {
														try {
															return addIdLandmark(p, f, DimensionArgument.getDimension(c, "dim"), UuidArgument.getUuid(c, "uuid"), c.getArgument("id", ResourceLocation.class), c.getArgument("pos", WorldCoordinates.class).getBlockPos(c.getSource()), ItemArgument.getItem(c, "icon"), Component.nullToEmpty(c.getArgument("name", String.class)), c.getArgument("lore", Component.class));
														} catch (CommandSyntaxException ex) {
															throw new RuntimeException(ex);
														}
													}))
												)
											)
										)
									)
								)
							)
						)
						.then(Commands.argument("dim", DimensionArgument.dimension())
							.then(Commands.argument("id", ResourceLocationArgument.id())
								.then(Commands.argument("pos", BlockPosArgument.blockPos())
									.then(Commands.argument("icon", ItemArgument.item(registryAccess))
										.then(Commands.argument("name", StringArgumentType.string())
											.then(Commands.argument("lore", ComponentArgument.textComponent(registryAccess))
												.executes(c -> execute(c, (s, p, e, f) -> {
													try {
														if (p == null) {
															f.accept(prefix().append(Component.literal("missing UUID argument for server console").withStyle(ChatFormatting.RED)));
															return 0;
														}
														return addIdLandmark(p, f, DimensionArgument.getDimension(c, "dim"), Surveyor.getUuid(p), c.getArgument("id", ResourceLocation.class), c.getArgument("pos", WorldCoordinates.class).getBlockPos(c.getSource()), ItemArgument.getItem(c, "icon"), Component.nullToEmpty(c.getArgument("name", String.class)), c.getArgument("lore", Component.class));
													} catch (CommandSyntaxException ex) {
														throw new RuntimeException(ex);
													}
												}))
											)
										)
									)
								)
							)
						)
					)
				)
				.then(Commands.literal("append")
					.requires(c -> c.hasPermission(2))
					.then(Commands.argument("id", ResourceLocationArgument.id())
						.suggests((c, b) -> suggestLandmarks(c, b, true))
						.then(Commands.literal("surveyor:color")
							.then(Commands.argument("color", StringArgumentType.greedyString())
								.suggests((c, s) -> SharedSuggestionProvider.suggest(ChatFormatting.getNames(true, false), s))
								.then(Commands.argument("dim", DimensionArgument.dimension())
									.then(Commands.argument("owner", UuidArgument.uuid())
									.suggests(SurveyorCommands::suggestOwners)
										.requires(c -> c.hasPermission(2))
										.executes(c -> execute(c, (s, p, e, f) -> {
											try {
												return appendColor(p, f, DimensionArgument.getDimension(c, "dim"), UuidArgument.getUuid(c, "owner"), c.getArgument("id", ResourceLocation.class), c.getArgument("color", String.class));
											} catch (CommandSyntaxException ex) {
												throw new RuntimeException(ex);
											}
										}))
									)
									.executes(c -> execute(c, (s, p, e, f) -> {
										try {
											if (p == null) {
												f.accept(prefix().append(Component.literal("missing UUID argument for server console").withStyle(ChatFormatting.RED)));
												return 0;
											}
											return appendColor(p, f, DimensionArgument.getDimension(c, "dim"), Surveyor.getUuid(p), c.getArgument("id", ResourceLocation.class), c.getArgument("color", String.class));
										} catch (CommandSyntaxException ex) {
											throw new RuntimeException(ex);
										}
									}))
								)
								.executes(c -> execute(c, (s, p, e, f) -> {
									if (p == null) {
										f.accept(prefix().append(Component.literal("missing UUID argument for server console").withStyle(ChatFormatting.RED)));
										return 0;
									}
									return appendColor(p, f, p.serverLevel(), Surveyor.getUuid(p), c.getArgument("id", ResourceLocation.class), c.getArgument("color", String.class));
								}))
							)
						)
					)
				)
				.then(Commands.literal("trim")
					.requires(c -> c.hasPermission(2))
					.then(Commands.argument("id", ResourceLocationArgument.id())
						.suggests((c, b) -> suggestLandmarks(c, b, true))
						.then(Commands.argument("component", ResourceLocationArgument.id())
							.suggests((c, b) -> SharedSuggestionProvider.suggestResource(LandmarkComponentType.keySet(), b))
							.then(Commands.argument("dim", DimensionArgument.dimension())
								.then(Commands.argument("owner", UuidArgument.uuid())
									.suggests(SurveyorCommands::suggestOwners)
									.requires(c -> c.hasPermission(2))
									.executes(c -> execute(c, (s, p, e, f) -> {
										try {
											return trimLandmark(p, f, DimensionArgument.getDimension(c, "dim"), UuidArgument.getUuid(c, "owner"), c.getArgument("id", ResourceLocation.class), c.getArgument("component", ResourceLocation.class));
										} catch (CommandSyntaxException ex) {
											throw new RuntimeException(ex);
										}
									}))
								)
								.executes(c -> execute(c, (s, p, e, f) -> {
									try {
										if (p == null) {
											f.accept(prefix().append(Component.literal("missing UUID argument for server console").withStyle(ChatFormatting.RED)));
											return 0;
										}
										return trimLandmark(p, f, DimensionArgument.getDimension(c, "dim"), Surveyor.getUuid(p), c.getArgument("id", ResourceLocation.class), c.getArgument("component", ResourceLocation.class));
									} catch (CommandSyntaxException ex) {
										throw new RuntimeException(ex);
									}
								}))
							)
							.executes(c -> execute(c, (s, p, e, f) -> {
								if (p == null) {
									f.accept(prefix().append(Component.literal("missing UUID argument for server console").withStyle(ChatFormatting.RED)));
									return 0;
								}
								return trimLandmark(p, f, p.serverLevel(), Surveyor.getUuid(p), c.getArgument("id", ResourceLocation.class), c.getArgument("component", ResourceLocation.class));
							}))
						)
					)
				)
				.then(Commands.literal("view")
					.then(Commands.argument("dim", DimensionArgument.dimension())
						.then(Commands.argument("id", ResourceLocationArgument.id())
							.suggests((c, b) -> suggestLandmarks(c, b, false))
							.then(Commands.argument("owner", UuidArgument.uuid())
									.suggests(SurveyorCommands::suggestOwners)
								.requires(c -> c.hasPermission(2))
								.executes(c -> execute(c, (s, p, e, f) -> {
									try {
										return viewLandmark(p, f, DimensionArgument.getDimension(c, "dim"), UuidArgument.getUuid(c, "owner"), c.getArgument("id", ResourceLocation.class), false);
									} catch (CommandSyntaxException ex) {
										throw new RuntimeException(ex);
									}
								}))
							)
							.executes(c -> execute(c, (s, p, e, f) -> {
								try {
									if (p == null) {
										f.accept(prefix().append(Component.literal("missing UUID argument for server console").withStyle(ChatFormatting.RED)));
										return 0;
									}
									return viewLandmark(p, f, DimensionArgument.getDimension(c, "dim"), Surveyor.getUuid(p), c.getArgument("id", ResourceLocation.class), false);
								} catch (CommandSyntaxException ex) {
									throw new RuntimeException(ex);
								}
							}))
						)
					)
				)
				.then(Commands.literal("raw")
					.requires(c -> Surveyor.CONFIG.debugCommands)
					.then(Commands.argument("dim", DimensionArgument.dimension())
						.then(Commands.argument("id", ResourceLocationArgument.id())
							.suggests((c, b) -> suggestLandmarks(c, b, true))
							.then(Commands.argument("owner", UuidArgument.uuid())
									.suggests(SurveyorCommands::suggestOwners)
								.requires(c -> c.hasPermission(2))
								.executes(c -> execute(c, (s, p, e, f) -> {
									try {
										return viewLandmark(p, f, DimensionArgument.getDimension(c, "dim"), UuidArgument.getUuid(c, "owner"), c.getArgument("id", ResourceLocation.class), true);
									} catch (CommandSyntaxException ex) {
										throw new RuntimeException(ex);
									}
								}))
							)
							.executes(c -> execute(c, (s, p, e, f) -> {
								try {
									if (p == null) {
										f.accept(prefix().append(Component.literal("missing UUID argument for server console").withStyle(ChatFormatting.RED)));
										return 0;
									}
									return viewLandmark(p, f, DimensionArgument.getDimension(c, "dim"), Surveyor.getUuid(p), c.getArgument("id", ResourceLocation.class), true);
								} catch (CommandSyntaxException ex) {
									throw new RuntimeException(ex);
								}
							}))
						)
					)
				)
				.then(Commands.literal("remove")
					.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
					.then(Commands.argument("dim", DimensionArgument.dimension())
						.then(Commands.argument("id", ResourceLocationArgument.id())
							.suggests((c, b) -> suggestLandmarks(c, b, false))
							.then(Commands.argument("owner", UuidArgument.uuid())
									.suggests(SurveyorCommands::suggestOwners)
								.requires(c -> c.hasPermission(2))
								.executes(c -> execute(c, (s, p, e, f) -> {
									try {
										return removeLandmark(p, f, DimensionArgument.getDimension(c, "dim"), UuidArgument.getUuid(c, "owner"), c.getArgument("id", ResourceLocation.class));
									} catch (CommandSyntaxException ex) {
										throw new RuntimeException(ex);
									}
								}))
							)
							.executes(c -> execute(c, (s, p, e, f) -> {
								try {
									if (p == null) {
										f.accept(prefix().append(Component.literal("missing UUID argument for server console").withStyle(ChatFormatting.RED)));
										return 0;
									}
									return removeLandmark(p, f, DimensionArgument.getDimension(c, "dim"), Surveyor.getUuid(p), c.getArgument("id", ResourceLocation.class));
								} catch (CommandSyntaxException ex) {
									throw new RuntimeException(ex);
								}
							}))
						)
					)
				)
		);
	}

	public interface SurveyorCommandExecutor<T> {
		T execute(MinecraftServer server, @Nullable ServerPlayer player, @Nullable SurveyorExploration exploration, Consumer<Component> feedback);
	}
}
