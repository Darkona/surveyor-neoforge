package folk.sisby.surveyor;

import folk.sisby.surveyor.config.SurveyorConfig;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;

public final class SurveyorGameEvents {
	private SurveyorGameEvents() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(SurveyorGameEvents::onChunkSent);
		NeoForge.EVENT_BUS.addListener(SurveyorGameEvents::onRightClickBlock);
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SurveyorGameEvents::onDeath);
		NeoForge.EVENT_BUS.addListener(SurveyorGameEvents::onClone);
	}

	private static void onChunkSent(ChunkWatchEvent.Sent event) {
		SurveyorExploration.of(event.getPlayer()).addChunk(event.getLevel().dimension(), event.getPos(), false);
	}

	private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || !player.isShiftKeyDown()) return;
		if (!(event.getItemStack().getItem() instanceof MapItem)) return;
		MapItemSavedData mapState = MapItem.getSavedData(event.getItemStack(), event.getLevel());
		if (mapState == null) return;
		boolean didThing = false;
		BlockState state = event.getLevel().getBlockState(event.getPos());
		if (Surveyor.CONFIG.builtins.recordFromMapItems && state.is(SurveyorMapIntegration.RECORD_FROM_MAP)) {
			SurveyorMapIntegration.recordMapData(player, mapState);
			didThing = true;
		}
		if (!mapState.locked && Surveyor.CONFIG.builtins.recordToMapItems != SurveyorConfig.Builtins.RecordStyle.NONE && state.is(SurveyorMapIntegration.RECORD_TO_MAP)) {
			SurveyorMapIntegration.applyMapData(player, mapState);
			didThing = true;
		}
		if (didThing) {
			player.level().playSound(null, player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, player.getSoundSource(), 1.0F, 0.7F);
			event.setCancellationResult(InteractionResult.SUCCESS);
			event.setCanceled(true);
		}
	}

	private static void onDeath(LivingDeathEvent event) {
		if (event.isCanceled() || !Surveyor.CONFIG.builtins.playerDeathWaypoints) return;
		if (!(event.getEntity() instanceof ServerPlayer self)) return;
		WorldLandmarks landmarks = WorldLandmarks.of(self.serverLevel());
		if (landmarks == null) return;
		landmarks.put(Landmark.createIncremental(landmarks, Surveyor.getUuid(self), Surveyor.id("grave"), builder -> builder
			.add(LandmarkComponentTypes.POS, self.blockPosition())
			.add(LandmarkComponentTypes.NAME, TextUtil.stripInteraction(self.getCombatTracker().getDeathMessage()))
			.add(LandmarkComponentTypes.TIME, self.level().getDayTime())
			.add(LandmarkComponentTypes.SEED, self.getRandom().nextInt())
		));
	}

	private static void onClone(PlayerEvent.Clone event) {
		if (event.getEntity() instanceof ServerPlayer player && event.getOriginal() instanceof ServerPlayer original) {
			PlayerSummary.of(player).copyFrom(PlayerSummary.of(original));
		}
	}
}
