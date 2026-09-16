package folk.sisby.surveyor.packet;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record C2SKnownLandmarksPacket(Map<ResourceKey<Level>, Multimap<UUID, ResourceLocation>> landmarks) implements C2SPacket {
	public static final Type<C2SKnownLandmarksPacket> ID = new Type<>(Surveyor.id("c2s_known_landmarks"));
	public static final StreamCodec<FriendlyByteBuf, C2SKnownLandmarksPacket> CODEC = SurveyorPacketCodecs.LANDMARK_KEYS.map(C2SKnownLandmarksPacket::new, C2SKnownLandmarksPacket::landmarks);

	public static C2SPacket of(ResourceKey<Level> dimension, Multimap<UUID, ResourceLocation> landmarks) {
		return new C2SKnownLandmarksPacket(Map.of(dimension, landmarks));
	}

	@Override
	public Type<C2SKnownLandmarksPacket> type() {
		return ID;
	}
}
