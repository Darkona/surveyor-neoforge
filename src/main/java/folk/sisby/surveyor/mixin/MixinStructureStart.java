package folk.sisby.surveyor.mixin;

import folk.sisby.surveyor.structure.WorldStructures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureStart.class)
public abstract class MixinStructureStart {
	@Inject(method = "placeInChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/Structure;afterPlace(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/levelgen/structure/pieces/PiecesContainer;)V"))
	private void structureGenerated(WorldGenLevel serverWorldAccess, StructureManager structureAccessor, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, CallbackInfo ci) {
		StructureStart self = (StructureStart) (Object) this;
		ServerLevel world = serverWorldAccess instanceof ServerLevel sw ? sw : ((WorldGenRegion) serverWorldAccess).level;
		WorldStructures.onStructurePlace(world, self);
	}
}
