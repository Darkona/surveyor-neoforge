package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.SurveyorDebug;
import folk.sisby.surveyor.Surveyor;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;

/**
 * Sends {@link S2CWorldIdPacket} and finishes at once; registered only for clients that have the channel.
 */
public record WorldIdConfigurationTask(ServerConfigurationPacketListener listener, UUID worldId) implements ICustomConfigurationTask {
	public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(Surveyor.id("world_id"));

	@Override
	public void run(Consumer<CustomPacketPayload> sender) {
		sender.accept(new S2CWorldIdPacket(worldId));
		if (SurveyorDebug.on) SurveyorDebug.log("World id {} sent during configuration", worldId);
		listener.finishCurrentTask(TYPE);
	}

	@Override
	public Type type() {
		return TYPE;
	}
}
