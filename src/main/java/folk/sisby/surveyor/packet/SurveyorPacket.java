package folk.sisby.surveyor.packet;

import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public interface SurveyorPacket extends CustomPacketPayload {
	int MAX_PAYLOAD_SIZE = 1_048_576;

	default List<SurveyorPacket> toPayloads() {
		return List.of(this);
	}
}
