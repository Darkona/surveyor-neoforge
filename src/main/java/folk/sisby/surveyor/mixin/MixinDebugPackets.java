package folk.sisby.surveyor.mixin;

import folk.sisby.surveyor.landmark.PoiLandmarks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugPackets.class)
public class MixinDebugPackets {
	@Inject(method = "sendPoiAddedPacket", at = @At("HEAD"))
	private static void onPointOfInterestAdded(ServerLevel world, BlockPos blockPos, CallbackInfo ci) {
		PoiLandmarks.onPoiAdded(world, blockPos);
	}
}
