package folk.sisby.surveyor.mixin.client;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.ClientSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.client.SurveyorNetworkHandler;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.packet.S2CWorldIdPacket;
import folk.sisby.surveyor.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPlayNetworkHandler implements SurveyorNetworkHandler {
	@Unique
	ClientSummary surveyor$summary = null;

	@Override
	public ClientSummary surveyor$getSummary() {
		return surveyor$summary;
	}

	@Inject(method = "handleLogin", at = @At("TAIL"))
	private void onJoin(ClientboundLoginPacket packet, CallbackInfo ci) {
		if (surveyor$summary != null) return; // some mods might do this
		ClientPacketListener self = (ClientPacketListener) (Object) this;
		surveyor$summary = new ClientSummary(packet.commonPlayerSpawnInfo().seed(), S2CWorldIdPacket.take(self.getConnection()), self);
		surveyor$summary.connect();
	}

	@Inject(method = "clearLevel", at = @At("HEAD"))
	void saveOnDisconnect(CallbackInfo ci) {
		if (surveyor$summary != null) surveyor$summary.disconnect();
	}

	@Inject(method = "handleRespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getAllMapData()Ljava/util/Map;"))
	void saveOnLeaveWorld(ClientboundRespawnPacket packet, CallbackInfo ci) { // just for unloading regions. ditch when we figure out how to do that better.
		ClientPacketListener self = (ClientPacketListener) (Object) this;
		if (surveyor$summary != null) surveyor$summary.leaveWorld(self.getLevel().dimension());
	}

	@Inject(method = "handlePlayerCombatKill", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;shouldShowDeathScreen()Z"))
	private void onDeathScreen(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
		if (!Surveyor.CONFIG.builtins.playerDeathWaypoints) return;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.level() == null) return;
		WorldSummary summary = WorldSummary.of(player.level());
		if (summary == null) return;
		if (summary.isClient()) {
			WorldLandmarks landmarks = summary.landmarks();
			if (landmarks == null) return;
			landmarks.put(
                    Landmark.createIncremental(landmarks, SurveyorClient.getClientUuid(), Surveyor.id("grave"), builder -> builder
					.add(LandmarkComponentTypes.POS, player.blockPosition())
					.add(LandmarkComponentTypes.NAME, TextUtil.stripInteraction(packet.message()))
					.add(LandmarkComponentTypes.TIME, player.level().getDayTime())
					.add(LandmarkComponentTypes.SEED, player.getRandom().nextInt())
				)
			);
		}
	}

}
