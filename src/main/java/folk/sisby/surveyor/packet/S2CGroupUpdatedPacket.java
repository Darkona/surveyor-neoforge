package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.Surveyor;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record S2CGroupUpdatedPacket(Map<UUID, PlayerSummary> players) implements S2CPacket {
	public static final CustomPacketPayload.Type<S2CGroupUpdatedPacket> ID = new CustomPacketPayload.Type<>(Surveyor.id("s2c_group_updated"));
	public static final StreamCodec<RegistryFriendlyByteBuf, S2CGroupUpdatedPacket> CODEC = SurveyorPacketCodecs.GROUP_SUMMARIES.map(S2CGroupUpdatedPacket::new, S2CGroupUpdatedPacket::players);

	public static S2CGroupUpdatedPacket of(UUID uuid, PlayerSummary summary) {
		return new S2CGroupUpdatedPacket(Map.of(uuid, summary));
	}

	@Override
	public Type<S2CGroupUpdatedPacket> type() {
		return ID;
	}
}
