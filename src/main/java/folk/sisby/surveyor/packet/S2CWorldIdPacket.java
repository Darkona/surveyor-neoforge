package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.SurveyorDebug;
import folk.sisby.surveyor.Surveyor;
import io.netty.buffer.ByteBuf;
import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Sent during configuration: a stable id for the server's world, so clients behind a proxy don't mix up backends with
 * the same seed (sisby-folk/surveyor#131). Optional and additive: servers without it keep the seed-based client folder.
 */
public record S2CWorldIdPacket(UUID worldId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<S2CWorldIdPacket> ID = new CustomPacketPayload.Type<>(Surveyor.id("s2c_world_id"));
	public static final StreamCodec<ByteBuf, S2CWorldIdPacket> CODEC = UUIDUtil.STREAM_CODEC.map(S2CWorldIdPacket::new, S2CWorldIdPacket::worldId);
	private static final AtomicReference<Received> RECEIVED = new AtomicReference<>();

	@Override
	public Type<S2CWorldIdPacket> type() {
		return ID;
	}

	public static void receive(S2CWorldIdPacket packet, IPayloadContext context) {
		remember(context.connection(), packet.worldId());
		if (SurveyorDebug.on) SurveyorDebug.log("World id {} received from the server", packet.worldId());
	}

	static void remember(Connection connection, UUID worldId) {
		RECEIVED.set(new Received(new WeakReference<>(connection), worldId));
	}

	/**
	 * Takes the id received on this connection, if any. An id received on another connection is dropped.
	 */
	public static @Nullable UUID take(Connection connection) {
		Received received = RECEIVED.getAndSet(null);
		return received != null && received.connection().get() == connection ? received.worldId() : null;
	}

	private record Received(WeakReference<Connection> connection, UUID worldId) {
	}
}
