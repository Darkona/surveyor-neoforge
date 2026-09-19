package folk.sisby.surveyor.packet;

import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public interface SurveyorPacket extends CustomPacketPayload {
	int MAX_PAYLOAD_SIZE = 1_048_576;

	default List<SurveyorPacket> toPayloads() {
		return List.of(this);
	}

	/**
	 * Splits this packet into payloads small enough to send. Packets that hold registry-bound data measure themselves with
	 * these registries.
	 */
	default List<SurveyorPacket> toPayloads(RegistryAccess registries) {
		return toPayloads();
	}
}
