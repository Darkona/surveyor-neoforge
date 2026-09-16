package folk.sisby.surveyor.mixin;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.portal.PortalShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PortalShape.class)
public class MixinNetherPortal {
	@Shadow @Final private LevelAccessor level;
	@Shadow private @Nullable BlockPos bottomLeft;
	@Shadow private int height;
	@Shadow @Final private Direction rightDir;
	@Shadow @Final private int width;

	@Inject(method = "createPortalBlocks", at = @At("TAIL"))
	private void onCreatePortal(CallbackInfo ci) {
		if (!Surveyor.CONFIG.builtins.netherPortalLandmarks) return;
		if (!(level instanceof ServerLevel serverWorld)) return;
		WorldLandmarks landmarks = WorldLandmarks.of(serverWorld);
		if (landmarks == null) return;
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(PoiTypes.NETHER_PORTAL.location().getNamespace(), "poi/%s/%s/%s/%s".formatted(PoiTypes.NETHER_PORTAL.location().getPath(), bottomLeft.getX(), bottomLeft.getY(), bottomLeft.getZ()));
		landmarks.put(Landmark.global(id, builder -> LandmarkComponentTypes.forBlock(builder, serverWorld, bottomLeft)
			.add(LandmarkComponentTypes.COLOR, DyeColor.PURPLE.getFireworkColor())
			.add(LandmarkComponentTypes.BOX, BoundingBox.fromCorners(bottomLeft, this.bottomLeft.relative(Direction.UP, this.height - 1).relative(rightDir, width - 1)))
		));
	}
}
