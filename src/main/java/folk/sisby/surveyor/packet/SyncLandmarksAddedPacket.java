package folk.sisby.surveyor.packet;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.util.MapUtil;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record SyncLandmarksAddedPacket(ResourceKey<Level> dimension, Table<UUID, ResourceLocation, Landmark> landmarks) implements SyncPacket {
	public static final CustomPacketPayload.Type<SyncLandmarksAddedPacket> ID = new CustomPacketPayload.Type<>(Surveyor.id("landmarks_added"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SyncLandmarksAddedPacket> CODEC = StreamCodec.composite(
		ResourceKey.streamCodec(Registries.DIMENSION), SyncLandmarksAddedPacket::dimension,
		SurveyorPacketCodecs.LANDMARK_SUMMARIES, SyncLandmarksAddedPacket::landmarks,
		SyncLandmarksAddedPacket::new
	);

	public static SyncLandmarksAddedPacket of(Multimap<UUID, ResourceLocation> keySet, WorldLandmarks summary) {
		return summary.createUpdatePacket(keySet);
	}

	@Override
	public List<SurveyorPacket> toPayloads(RegistryAccess registries) {
		List<SurveyorPacket> payloads = new ArrayList<>();
		// Fix: measure with registries, landmarks can hold registry-bound item stacks
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
		int size;
		try {
			CODEC.encode(buf, this);
			size = buf.readableBytes();
		} finally {
			buf.release();
		}
		if (size < MAX_PAYLOAD_SIZE) {
			payloads.add(this);
		} else {
			Multimap<UUID, ResourceLocation> keySet = MapUtil.keyMultiMap(landmarks);
			if (keySet.size() == 1) {
				Surveyor.LOGGER.error("Couldn't create a landmark update packet for {} at {} - an individual landmark would be too large to send!", keySet.keys().stream().findFirst().orElseThrow(), keySet.values().stream().findFirst().orElseThrow());
				return List.of();
			}
			Multimap<UUID, ResourceLocation> firstHalf = HashMultimap.create();
			Multimap<UUID, ResourceLocation> secondHalf = HashMultimap.create();
			keySet.forEach((key, pos) -> {
				if (firstHalf.size() < keySet.size() / 2) {
					firstHalf.put(key, pos);
				} else {
					secondHalf.put(key, pos);
				}
			});
			payloads.addAll(new SyncLandmarksAddedPacket(dimension, MapUtil.splitByKeyMap(landmarks, firstHalf)).toPayloads(registries));
			payloads.addAll(new SyncLandmarksAddedPacket(dimension, MapUtil.splitByKeyMap(landmarks, secondHalf)).toPayloads(registries));
		}
		return payloads;
	}

	@Override
	public Type<SyncLandmarksAddedPacket> type() {
		return ID;
	}
}
