package folk.sisby.surveyor.packet;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.MapUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record SyncLandmarksRequestedPacket(ResourceKey<Level> dimension, Multimap<UUID, ResourceLocation> landmarks) implements SyncPacket {
	public static final CustomPacketPayload.Type<SyncLandmarksRequestedPacket> ID = new CustomPacketPayload.Type<>(Surveyor.id("landmarks_requested"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SyncLandmarksRequestedPacket> CODEC = StreamCodec.composite(
		ResourceKey.streamCodec(Registries.DIMENSION), SyncLandmarksRequestedPacket::dimension,
		ByteBufCodecs.<RegistryFriendlyByteBuf, UUID, List<ResourceLocation>, Map<UUID, List<ResourceLocation>>>map(HashMap::new, UUIDUtil.STREAM_CODEC, ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list())).map(MapUtil::asMultiMap, MapUtil::asListMap), SyncLandmarksRequestedPacket::landmarks,
		SyncLandmarksRequestedPacket::new
	);

	public static SyncLandmarksRequestedPacket of(ResourceKey<Level> dimension, UUID uuid, ResourceLocation id) {
		return new SyncLandmarksRequestedPacket(dimension, MapUtil.asMultiMap(Map.of(uuid, List.of(id))));
	}

	@Override
	public Type<SyncLandmarksRequestedPacket> type() {
		return ID;
	}
}
