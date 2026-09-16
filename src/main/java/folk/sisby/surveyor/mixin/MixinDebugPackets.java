package folk.sisby.surveyor.mixin;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugPackets.class)
public class MixinDebugPackets {
	@Inject(method = "sendPoiAddedPacket", at = @At("HEAD"))
	private static void onPointOfInterestAdded(ServerLevel world, BlockPos blockPos, CallbackInfo ci) {
		WorldLandmarks landmarks = WorldLandmarks.of(world);
		if (landmarks == null) return;
		Holder<PoiType> poiType = world.getPoiManager().getType(blockPos).orElse(null);
		if (poiType == null || poiType.unwrapKey().isEmpty() || !Surveyor.CONFIG.builtins.poiLandmarks.contains(poiType.unwrapKey().get().location().toString())) return;
		ResourceLocation poi = poiType.unwrapKey().get().location();
		landmarks.put(Landmark.global(
			ResourceLocation.fromNamespaceAndPath(poi.getNamespace(), "poi/%s/%s/%s/%s".formatted(poi.getPath(), blockPos.getX(), blockPos.getY(), blockPos.getZ())),
			builder -> LandmarkComponentTypes.forBlock(builder, world, blockPos)
		));
	}

	@Inject(method = "sendPoiRemovedPacket", at = @At("HEAD"))
	private static void onPointOfInterestRemoved(ServerLevel world, BlockPos blockPos, CallbackInfo ci) {
		WorldLandmarks landmarks = WorldLandmarks.of(world);
		if (landmarks == null) return;
		landmarks.removeAll(l -> l.owner().equals(WorldLandmarks.GLOBAL)
			&& l.id().getPath().startsWith("poi")
			&& l.components().contains(LandmarkComponentTypes.POS)
			&& l.components().get(LandmarkComponentTypes.POS).equals(blockPos)
		);
	}
}
