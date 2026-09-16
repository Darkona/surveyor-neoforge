package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.Surveyor;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * for players new to the server with no data associated
 */
public record S2CGroupAmendedPacket(UUID player) implements S2CPacket {
	public static final CustomPacketPayload.Type<S2CGroupAmendedPacket> ID = new CustomPacketPayload.Type<>(Surveyor.id("s2c_group_amended"));
	public static final StreamCodec<ByteBuf, S2CGroupAmendedPacket> CODEC = UUIDUtil.STREAM_CODEC.map(S2CGroupAmendedPacket::new, S2CGroupAmendedPacket::player);

	@Override
	public Type<S2CGroupAmendedPacket> type() {
		return ID;
	}
}
