package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.ServerSummary;
import folk.sisby.surveyor.util.SafeNbtWriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S2CWorldIdPacketTest {
	private static final Path SRC = Path.of(System.getProperty("surveyor.projectDir", "."), "src/main/java/folk/sisby/surveyor");

	@Test
	@DisplayName("The world id round-trips through its codec")
	void codecRoundTrip() {
		UUID id = UUID.randomUUID();
		ByteBuf buf = Unpooled.buffer();
		S2CWorldIdPacket.CODEC.encode(buf, new S2CWorldIdPacket(id));
		assertEquals(id, S2CWorldIdPacket.CODEC.decode(buf).worldId());
	}

	@Test
	@DisplayName("A received id is used once, and only by its own connection")
	void takenOncePerConnection() {
		Connection first = new Connection(PacketFlow.CLIENTBOUND);
		Connection second = new Connection(PacketFlow.CLIENTBOUND);
		UUID id = UUID.randomUUID();
		S2CWorldIdPacket.remember(first, id);
		assertEquals(id, S2CWorldIdPacket.take(first));
		assertNull(S2CWorldIdPacket.take(first), "taken ids are cleared");
		S2CWorldIdPacket.remember(first, id);
		assertNull(S2CWorldIdPacket.take(second), "an id from another connection is dropped");
		assertNull(S2CWorldIdPacket.take(first));
	}

	@Test
	@DisplayName("The server's world id is generated once and kept")
	void worldIdPersists(@TempDir Path folder) {
		UUID first = ServerSummary.loadWorldId(folder.toFile());
		SafeNbtWriter.flush();
		assertTrue(Files.exists(folder.resolve(ServerSummary.WORLD_ID_FILE)));
		assertEquals(first, ServerSummary.loadWorldId(folder.toFile()));
	}

	@Test
	@DisplayName("The payload is optional and only sent to clients that have the channel")
	void optionalAndGuarded() throws IOException {
		String networking = Files.readString(SRC.resolve("SurveyorNetworking.java"));
		assertTrue(networking.contains("event.registrar(\"1\").optional()"), "payloads must stay optional");
		String tasks = networking.substring(networking.indexOf("public static void registerTasks("));
		assertTrue(tasks.indexOf("hasChannel(S2CWorldIdPacket.ID)") < tasks.indexOf("event.register("), "check the channel before registering the task");
	}
}
