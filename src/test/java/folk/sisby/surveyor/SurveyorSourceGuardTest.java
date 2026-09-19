package folk.sisby.surveyor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyorSourceGuardTest {
	private static final Path SRC = Path.of(System.getProperty("surveyor.projectDir", "."), "src/main/java/folk/sisby/surveyor");

	private static String read(String file) throws IOException {
		return Files.readString(SRC.resolve(file));
	}

	@Test
	@DisplayName("Region state is only touched under the region's monitor")
	void regionStateGuarded() throws IOException {
		String src = read("terrain/RegionSummary.java");
		for (String sig : new String[]{"boolean contains(ChunkPos", "ChunkSummary get(ChunkPos", "ChunkSummary get(int x", "void set(int x", "BitSet bitSet()", "void putChunk(", "BitSet readUpdatePacket(", "S2CUpdateRegionPacket createUpdatePacket(", "ValueView getBiomePalette()", "ValueView getBlockPalette()", "boolean isLoaded()", "void readNbt("}) {
			int at = src.indexOf(sig);
			assertTrue(at > 0, sig);
			String line = src.substring(src.lastIndexOf('\n', at), at);
			assertTrue(line.contains("synchronized"), sig + " must be synchronized");
		}
		assertTrue(src.contains("synchronized (RegionSummary.this)"), "the write completion must unload under the monitor");
	}

	@Test
	@DisplayName("No save is skipped and every file goes through SafeNbtWriter")
	void savesGoThroughSafeWriter() throws IOException {
		assertFalse(read("terrain/RegionSummary.java").contains("if (saving) return;"), "a running save must not skip the next one");
		try (var files = Files.walk(SRC)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".java") && !p.endsWith("SafeNbtWriter.java")).toList()) {
				String src = Files.readString(file);
				assertFalse(src.contains("NbtIo.writeCompressed"), SRC.relativize(file) + " writes a summary file directly");
				assertFalse(src.contains("Util.ioPool()"), SRC.relativize(file) + " writes on the IO pool without ordering");
			}
		}
		assertTrue(read("ServerSummary.java").contains("if (force) SafeNbtWriter.flush();"), "forced saves must wait for the writes");
	}

	@Test
	@DisplayName("A sent chunk is always explored; its payload is built only for recipients")
	void addChunkExploresAndBuildsLazily() throws IOException {
		String src = read("PlayerSummary.java");
		String addChunk = src.substring(src.indexOf("public void addChunk(ResourceKey<Level> dimension, ChunkPos pos, boolean updateClient) {"));
		addChunk = addChunk.substring(0, addChunk.indexOf("SurveyorExploration.super.addChunk"));
		assertFalse(addChunk.contains("return;"), "nothing may return before the chunk is recorded as explored");
		assertTrue(addChunk.indexOf("S2CPacket.hasRecipients(") < addChunk.indexOf("S2CUpdateRegionPacket.of("), "check recipients before building the payload");
	}

	@Test
	@DisplayName("A player save reports the real online state")
	void playerSaveKeepsOnlineState() throws IOException {
		String src = read("mixin/MixinServerPlayerEntity.java");
		assertTrue(src.contains("updatePlayer(Surveyor.getUuid(self), nbt, !self.hasDisconnected())"));
	}

	@Test
	@DisplayName("Cross-thread state is concurrent and client events run on the client thread")
	void clientThreadOwnership() throws IOException {
		assertTrue(read("ServerSummary.java").contains("this.worlds = new ConcurrentHashMap<>()"));
		String events = read("client/SurveyorClientEvents.java");
		assertTrue(events.contains("Minecraft.getInstance().execute("), "off-thread invocations must hand off");
		assertTrue(events.contains("bits.clone()") && events.contains("ImmutableMultimap.copyOf("), "arguments must be snapshotted before the hand-off");
	}

	@Test
	@DisplayName("Structure discovery skips unchanged views and allocates no boxes")
	void structureDiscoveryCheap() throws IOException {
		String src = read("Surveyor.java");
		assertFalse(src.contains("inflatedBy("), "inflatedBy allocates a box per structure and per piece");
		assertTrue(src.contains("serverSummary.viewHit("), "the view raycast must go through the per-player memo");
	}

	@Test
	@DisplayName("Group explorations are live views; entity summaries are reused")
	void noPerCallCopies() throws IOException {
		String server = read("ServerSummary.java");
		String sharing = server.substring(server.indexOf("public SurveyorExploration getSharingExploration("));
		sharing = sharing.substring(0, sharing.indexOf("\n\t}"));
		assertTrue(sharing.contains("new GroupExploration(") && !sharing.contains("ofMerged("), "group explorations must not deep-copy");
		assertFalse(read("client/SurveyorClient.java").contains("ofMerged("), "the client's exploration must be a live union");
		String client = read("client/ClientSummary.java");
		assertTrue(client.contains("entitySummaries.get(player)"), "players() must reuse the entity summary of a player");
	}

	@Test
	@DisplayName("The region unload check scans the whole region and allocates nothing")
	void unloadCheckCoversRegion() throws IOException {
		String src = read("terrain/RegionSummary.java");
		String check = src.substring(src.indexOf("public boolean isUnloaded(Level world) {"));
		check = check.substring(0, check.indexOf("\n\t}"));
		assertFalse(check.contains("toChunks(") || check.contains("stream()"), "runs on every chunk unload: no per-call sets or streams");
		assertTrue(check.contains("RegionPos.CHUNK_SIZE"), "must cover all CHUNK_SIZE × CHUNK_SIZE chunks of the region");
	}

	@Test
	@DisplayName("Client summaries tolerate connections without a summary (ReplayMod)")
	void summariesSkipNulls() throws IOException {
		String src = read("client/SurveyorClient.java");
		String method = src.substring(src.indexOf("public static Map<ResourceKey<Level>, WorldSummary> getSummaries("));
		method = method.substring(0, method.indexOf("\n\t}"));
		assertFalse(method.contains("Collectors.toMap"), "toMap throws on the null summary of a replay connection");
		assertTrue(method.contains("summary != null"));
	}

	@Test
	@DisplayName("An unregistered structure is skipped, never thrown on")
	void unregisteredStructuresSkipped() throws IOException {
		for (String file : new String[]{"structure/WorldStructures.java", "structure/RegionStructureSummary.java"}) {
			assertFalse(read(file).contains("getResourceKey(start.getStructure()).orElseThrow()"), file + " throws on a structure missing from the registry");
		}
	}
}
