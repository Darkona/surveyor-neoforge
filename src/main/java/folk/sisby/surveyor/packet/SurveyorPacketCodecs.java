package folk.sisby.surveyor.packet;

import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.mojang.serialization.Codec;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.structure.RegionStructureSummary;
import folk.sisby.surveyor.structure.StructurePieceSummary;
import folk.sisby.surveyor.structure.StructureStartSummary;
import folk.sisby.surveyor.util.MapUtil;
import folk.sisby.surveyor.util.RegionPos;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

public interface SurveyorPacketCodecs {
	StreamCodec<FriendlyByteBuf, Table<ResourceKey<Level>, RegionPos, BitSet>> TERRAIN_KEYS = ByteBufCodecs.<FriendlyByteBuf, ResourceKey<Level>, Map<RegionPos, BitSet>, Map<ResourceKey<Level>, Map<RegionPos, BitSet>>>map(HashMap::new,
		ResourceKey.streamCodec(Registries.DIMENSION),
		ByteBufCodecs.map(HashMap::new,
			RegionPos.PACKET_CODEC,
			ByteBufCodecs.fromCodec(ExtraCodecs.BIT_SET)
		)
	).map(MapUtil::asTable, Table::rowMap);

	StreamCodec<FriendlyByteBuf, Map<ResourceKey<Level>, Multimap<ResourceKey<Structure>, ChunkPos>>> STRUCTURE_KEYS = ByteBufCodecs.map(HashMap::new,
		ResourceKey.streamCodec(Registries.DIMENSION), ByteBufCodecs.<FriendlyByteBuf, ResourceKey<Structure>, List<ChunkPos>, Map<ResourceKey<Structure>, List<ChunkPos>>>map(HashMap::new,
			ResourceKey.streamCodec(Registries.STRUCTURE),
			ByteBufCodecs.VAR_LONG.map(ChunkPos::new, ChunkPos::toLong).apply(ByteBufCodecs.list())
		).map(MapUtil::asMultiMap, MapUtil::asListMap)
	);

	StreamCodec<FriendlyByteBuf, Table<ResourceKey<Level>, ResourceKey<Structure>, LongSet>> STRUCTURE_KEYS_LONG_SET = ByteBufCodecs.<FriendlyByteBuf, ResourceKey<Level>, Map<ResourceKey<Structure>, LongSet>, Map<ResourceKey<Level>, Map<ResourceKey<Structure>, LongSet>>>map(HashMap::new,
		ResourceKey.streamCodec(Registries.DIMENSION),
		ByteBufCodecs.map(HashMap::new,
			ResourceKey.streamCodec(Registries.STRUCTURE),
			ByteBufCodecs.fromCodec(Codec.LONG_STREAM).map(LongOpenHashSet::toSet, LongSet::longStream)
		)
	).map(MapUtil::asTable, Table::rowMap);

	StreamCodec<FriendlyByteBuf, Map<ResourceKey<Level>, Multimap<UUID, ResourceLocation>>> LANDMARK_KEYS = ByteBufCodecs.map(HashMap::new,
		ResourceKey.streamCodec(Registries.DIMENSION),
		ByteBufCodecs.<FriendlyByteBuf, UUID, List<ResourceLocation>, Map<UUID, List<ResourceLocation>>>map(HashMap::new,
			UUIDUtil.STREAM_CODEC,
			ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list())
		).map(MapUtil::asMultiMap, MapUtil::asListMap)
	);

	StreamCodec<RegistryFriendlyByteBuf, Map<UUID, PlayerSummary>> GROUP_SUMMARIES = ByteBufCodecs.map(HashMap::new,
		UUIDUtil.STREAM_CODEC,
		StreamCodec.ofMember(PlayerSummary.OfflinePlayerSummary::writeBuf, PlayerSummary.OfflinePlayerSummary::readBuf)
	);

	StreamCodec<FriendlyByteBuf, Table<ResourceKey<Structure>, ChunkPos, StructureStartSummary>> STRUCTURE_SUMMARIES = ByteBufCodecs.<FriendlyByteBuf, ResourceKey<Structure>, Map<ChunkPos, StructureStartSummary>, Map<ResourceKey<Structure>, Map<ChunkPos, StructureStartSummary>>>map(HashMap::new,
		ResourceKey.streamCodec(Registries.STRUCTURE),
		ByteBufCodecs.map(HashMap::new,
			ByteBufCodecs.VAR_LONG.map(ChunkPos::new, ChunkPos::toLong),
			StreamCodec.ofMember((StructurePieceSummary s, FriendlyByteBuf b) -> b.writeNbt(s.toNbt()), (FriendlyByteBuf b) -> RegionStructureSummary.readStructurePieceNbt(b.readNbt())).apply(ByteBufCodecs.list()).map(StructureStartSummary::new, StructureStartSummary::getChildren)
		)
	).map(MapUtil::asTable, Table::rowMap);

	StreamCodec<FriendlyByteBuf, Map<ResourceKey<Structure>, ResourceKey<StructureType<?>>>> STRUCTURE_TYPES = ByteBufCodecs.map(HashMap::new,
		ResourceKey.streamCodec(Registries.STRUCTURE),
		ResourceKey.streamCodec(Registries.STRUCTURE_TYPE)
	);

	StreamCodec<FriendlyByteBuf, Multimap<ResourceKey<Structure>, TagKey<Structure>>> STRUCTURE_TAGS = ByteBufCodecs.<FriendlyByteBuf, ResourceKey<Structure>, List<TagKey<Structure>>, Map<ResourceKey<Structure>, List<TagKey<Structure>>>>map(HashMap::new,
		ResourceKey.streamCodec(Registries.STRUCTURE),
		ByteBufCodecs.fromCodec(TagKey.hashedCodec(Registries.STRUCTURE)).apply(ByteBufCodecs.list())
	).map(MapUtil::asMultiMap, MapUtil::asListMap);

	StreamCodec<ByteBuf, Table<UUID, ResourceLocation, Landmark>> LANDMARK_SUMMARIES = ByteBufCodecs.fromCodec(WorldLandmarks.CODEC);
}
