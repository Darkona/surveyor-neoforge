package folk.sisby.surveyor.mixin;

import com.mojang.authlib.GameProfile;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.ServerSummary;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.SurveyorPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class MixinServerPlayerEntity implements SurveyorPlayer {
	@Unique
	PlayerSummary.ServerPlayerEntitySummary surveyor$summary = null;

	@Inject(method = "<init>", at = @At("TAIL"))
	public void init(MinecraftServer server, ServerLevel world, GameProfile profile, ClientInformation clientOptions, CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		surveyor$summary = new PlayerSummary.ServerPlayerEntitySummary(self);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	public void writeSurveyorData(CompoundTag nbt, CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		surveyor$summary.writeNbt(nbt);
		// Fix: report the real online state instead of offline on every save.
		ServerSummary.of(self.getServer()).updatePlayer(Surveyor.getUuid(self), nbt, !self.hasDisconnected());
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	public void readSurveyorData(CompoundTag nbt, CallbackInfo ci) {
		surveyor$summary.read(nbt);
	}

	@Override
	public PlayerSummary surveyor$getSummary() {
		return surveyor$summary;
	}
}
