package folk.sisby.surveyor.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.config.SystemMode;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static folk.sisby.surveyor.SurveyorCommands.indent;
import static folk.sisby.surveyor.SurveyorCommands.prefix;

public class SurveyorClientCommands {

	private static int getLandmarks(WorldSummary summary, SurveyorExploration exploration, Consumer<Component> feedback, boolean global) {
		WorldLandmarks landmarks = summary.landmarks();
		if (landmarks == null) {
			feedback.accept(prefix().append(Component.literal("The landmark system is dynamically disabled!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		Map<ResourceLocation, Landmark> landmarkMap = landmarks.asMap(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), SurveyorClient.getExploration());
		if (landmarkMap.isEmpty()) {
			feedback.accept(prefix().append(Component.literal("There are no landmarks in this world!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		feedback.accept(prefix().append(Component.literal("World %s:".formatted(global ? "Landmarks" : "Waypoints"))));
		for (Landmark landmark : landmarkMap.values()) {
			feedback.accept(
				indent()
					.append(Component.literal("%s:".formatted(landmark.id().getNamespace())).withStyle(ChatFormatting.GRAY))
					.append(Component.literal(landmark.id().getPath()))
					.append(!landmark.components().contains(LandmarkComponentTypes.NAME) ? Component.nullToEmpty("") :
						Component.literal(": \"")
							.append(landmark.components().get(LandmarkComponentTypes.NAME).copy().withStyle(s -> s.withColor(landmark.components().contains(LandmarkComponentTypes.COLOR) ? 0xFFFFFF & landmark.components().get(LandmarkComponentTypes.COLOR) : ChatFormatting.GREEN.getColor())))
							.append(Component.literal("\""))
					)
			);
		}
		return landmarkMap.size();
	}


	private static int viewLandmark(WorldSummary summary, Consumer<Component> feedback, ResourceLocation id, boolean global) {
		WorldLandmarks landmarks = summary.landmarks();
		if (landmarks == null) {
			feedback.accept(prefix().append(Component.literal("The landmark system is dynamically disabled!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		if (!landmarks.contains(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), id)) {
			feedback.accept(prefix().append(Component.literal("No landmark exists of that id!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		Landmark landmark = landmarks.get(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), id);
		feedback.accept(prefix().append(Component.literal(landmark.owner().equals(WorldLandmarks.GLOBAL) ? "Landmark " : "Waypoint ").withStyle(ChatFormatting.GRAY)).append(Component.literal(id.toString())).append(Component.literal(": ")));
		landmark.toText().forEach(t -> feedback.accept(indent().append(t)));
		return 1;
	}

	private static int rawLandmark(WorldSummary summary, Consumer<Component> feedback, ResourceLocation id, boolean global) {
		WorldLandmarks landmarks = summary.landmarks();
		if (landmarks == null) {
			feedback.accept(prefix().append(Component.literal("The landmark system is dynamically disabled!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		if (!landmarks.contains(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), id)) {
			feedback.accept(prefix().append(Component.literal("No landmark exists of that id!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		Landmark landmark = landmarks.get(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), id);
		feedback.accept(prefix().append(Component.literal(landmark.owner().equals(WorldLandmarks.GLOBAL) ? "Landmark " : "Waypoint ").withStyle(ChatFormatting.GRAY)).append(Component.literal(id.toString())).append(Component.literal(": ")).append(Component.literal(landmark.toNbt().toString()).withStyle(ChatFormatting.AQUA)));
		return 1;
	}

	private static int removeLandmark(WorldSummary summary, Consumer<Component> feedback, ResourceLocation id, boolean global) {
		WorldLandmarks landmarks = summary.landmarks();
		if (landmarks == null) {
			feedback.accept(prefix().append(Component.literal("The landmark system is dynamically disabled!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		if (!landmarks.contains(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), id)) {
			feedback.accept(prefix().append(Component.literal("No landmark exists of that id!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		Landmark landmark = landmarks.get(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), id);
		landmarks.remove(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), id);
		feedback.accept(prefix().append(Component.literal("%s %s removed successfully!".formatted(landmark.owner().equals(WorldLandmarks.GLOBAL) ? "Landmark" : "Waypoint", id)).withStyle(ChatFormatting.GREEN)));
		return 1;
	}

	private static int addBlockLandmark(WorldSummary summary, Level world, Consumer<Component> feedback, BlockPos pos, boolean global) {
		WorldLandmarks landmarks = summary.landmarks();
		if (landmarks == null) {
			feedback.accept(prefix().append(Component.literal("The landmark system is dynamically disabled!").withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		ResourceLocation id = Surveyor.id("block/%s/%s/%s".formatted(pos.getX(), pos.getY(), pos.getZ()));
		if (landmarks.contains(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), id)) {
			feedback.accept(prefix().append(Component.literal("A landmark with this ID already exists! Replacing...").withStyle(ChatFormatting.YELLOW)));
		}
		landmarks.put(Landmark.create(global ? WorldLandmarks.GLOBAL : SurveyorClient.getClientUuid(), id, builder -> LandmarkComponentTypes.forBlock(builder, world, pos)));
		feedback.accept(prefix().append(Component.literal("Added new %s %s!".formatted(global ? "Landmark" : "Waypoint", id)).withStyle(ChatFormatting.GREEN)));
		return 1;
	}

	public static <T> T map(CommandContext<CommandSourceStack> context, SurveyorCommandExecutor<T> executor, boolean feedback) {
		LocalPlayer player = Minecraft.getInstance().player;
		ClientLevel world = Minecraft.getInstance().level;
		SurveyorExploration exploration = SurveyorClient.getExploration();
		try {
			return executor.execute(WorldSummary.of(world), player, world, exploration, player::sendSystemMessage);
		} catch (Exception e) {
			if (feedback) player.sendSystemMessage(Component.literal("Command failed! Check log for details.").withStyle(ChatFormatting.RED));
			if (feedback) Surveyor.LOGGER.error("[Surveyor] Error while executing command: {}", context.getInput(), e);
			return null;
		}
	}

	public static int execute(CommandContext<CommandSourceStack> context, SurveyorCommandExecutor<Integer> executor) {
		return Objects.requireNonNullElse(map(context, executor, true), 0);
	}

	private static CommandSourceStack sourceForPos(CommandSourceStack source) {
		return source;
	}

	public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
		dispatcher.register(
			Commands.literal("waypointsc")
				.requires(c -> !Minecraft.getInstance().isLocalServer() && Minecraft.getInstance().getConnection().getCommands().findNode(List.of("surveyor")) == null)
				.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.DISABLED)
				.executes(c -> execute(c, (w, p, sw, e, f) -> getLandmarks(w, e, f, false)))
				.then(Commands.literal("new")
					.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
					.then(Commands.literal("block")
						.then(Commands.argument("pos", BlockPosArgument.blockPos())
							.executes(c -> execute(c, (w, p, sw, e, f) -> addBlockLandmark(w, sw, f, c.getArgument("pos", WorldCoordinates.class).getBlockPos(sourceForPos(c.getSource())), false)))
						)
					)
				)
				.then(Commands.literal("view")
					.then(Commands.argument("id", ResourceLocationArgument.id())
						.suggests((c, b) -> SharedSuggestionProvider.suggestResource((Iterable<ResourceLocation>) map(c, (w, p, sw, e, f) -> w.landmarks() == null ? new HashSet<ResourceLocation>() : w.landmarks().asMap(SurveyorClient.getClientUuid(), p.hasPermissions(2) ? null : e).keySet(), false), b))
						.executes(c -> execute(c, (w, p, sw, e, f) -> viewLandmark(w, f, c.getArgument("id", ResourceLocation.class), false)))
					)
				)
				.then(Commands.literal("raw")
					.requires(c -> Surveyor.CONFIG.debugCommands)
					.then(Commands.argument("id", ResourceLocationArgument.id())
						.suggests((c, b) -> SharedSuggestionProvider.suggestResource((Iterable<ResourceLocation>) map(c, (w, p, sw, e, f) -> w.landmarks() == null ? new HashSet<ResourceLocation>() : w.landmarks().asMap(SurveyorClient.getClientUuid(), p.hasPermissions(2) ? null : e).keySet(), false), b))
						.executes(c -> execute(c, (w, p, sw, e, f) -> rawLandmark(w, f, c.getArgument("id", ResourceLocation.class), false)))
					)
				)
				.then(Commands.literal("remove")
					.requires(c -> Surveyor.CONFIG.landmarks != SystemMode.FROZEN)
					.then(Commands.argument("id", ResourceLocationArgument.id())
						.suggests((c, b) -> SharedSuggestionProvider.suggestResource((Iterable<ResourceLocation>) map(c, (w, p, sw, e, f) -> w.landmarks() == null ? new HashSet<ResourceLocation>() : w.landmarks().asMap(SurveyorClient.getClientUuid(), p.hasPermissions(2) ? null : e).keySet(), false), b))
						.executes(c -> execute(c, (w, p, sw, e, f) -> removeLandmark(w, f, c.getArgument("id", ResourceLocation.class), false)))
					)
				)
		);
	}

	public interface SurveyorCommandExecutor<T> {
		T execute(WorldSummary currentWorldSummary, LocalPlayer player, ClientLevel world, SurveyorExploration exploration, Consumer<Component> feedback);
	}
}
