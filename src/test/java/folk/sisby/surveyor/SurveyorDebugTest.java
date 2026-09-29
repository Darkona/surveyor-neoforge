package folk.sisby.surveyor;

import com.sun.management.ThreadMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Debug mode: counters summed per summary, on from the config or -Dsurveyor.debug=true, and free when off.
 */
class SurveyorDebugTest {
	private static final Path SRC = Path.of(System.getProperty("surveyor.projectDir", "."), "src/main/java/folk/sisby/surveyor");

	@AfterEach
	void reset() {
		System.clearProperty(SurveyorDebug.PROPERTY);
		SurveyorDebug.configure(false);
		SurveyorDebug.takeSummary();
	}

	@Test
	@DisplayName("Off, nothing is counted; on, counters and traffic are summed and reset by each summary")
	void countsWhenOn() {
		SurveyorDebug.configure(false);
		SurveyorDebug.count(SurveyorDebug.Count.CHUNKS_OFF_THREAD);
		SurveyorDebug.traffic("sent", "surveyor:test", 1, 1);
		assertNull(SurveyorDebug.takeSummary(), "debug mode off records nothing");

		SurveyorDebug.configure(true);
		SurveyorDebug.count(SurveyorDebug.Count.CHUNKS_OFF_THREAD);
		SurveyorDebug.count(SurveyorDebug.Count.CHUNKS_OFF_THREAD, 2);
		SurveyorDebug.max(SurveyorDebug.Count.CHUNKS_PENDING_MAX, 7);
		SurveyorDebug.max(SurveyorDebug.Count.CHUNKS_PENDING_MAX, 3);
		SurveyorDebug.traffic("sent", "surveyor:s2c_update_region", 2, 3);
		SurveyorDebug.traffic("sent", "surveyor:s2c_update_region", 1, 1);
		String summary = SurveyorDebug.takeSummary();
		assertEquals("chunks.offThread=3, chunks.pendingMax=7; sent surveyor:s2c_update_region x2 (payloads 3, players 4)", summary);
		assertNull(SurveyorDebug.takeSummary(), "a summary resets the counters");
	}

	@Test
	@DisplayName("-Dsurveyor.debug=true keeps debug mode on whatever the config says")
	void systemPropertyWins() {
		System.setProperty(SurveyorDebug.PROPERTY, "true");
		SurveyorDebug.configure(false);
		assertTrue(SurveyorDebug.on);
		System.clearProperty(SurveyorDebug.PROPERTY);
		SurveyorDebug.configure(false);
		assertFalse(SurveyorDebug.on);
	}

	@Test
	@DisplayName("Counting allocates nothing, on or off")
	void countingIsFree() {
		ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
		long thread = Thread.currentThread().threadId();
		for (boolean on : new boolean[]{false, true}) {
			SurveyorDebug.configure(on);
			for (int i = 0; i < 20_000; i++) SurveyorDebug.count(SurveyorDebug.Count.CHUNKS_PUBLISHED);
			long before = threads.getThreadAllocatedBytes(thread);
			for (int i = 0; i < 100_000; i++) {
				SurveyorDebug.count(SurveyorDebug.Count.CHUNKS_PUBLISHED);
				SurveyorDebug.max(SurveyorDebug.Count.CHUNKS_PENDING_MAX, i & 255);
			}
			long allocated = threads.getThreadAllocatedBytes(thread) - before;
			// A few KB may come from the JIT compiling the loop; one object per call would be megabytes.
			assertTrue(allocated < 16 * 1024, "counting allocated " + allocated + " bytes with debug " + (on ? "on" : "off"));
		}
	}

	@Test
	@DisplayName("Every debug log and traffic call is behind the debug check, so nothing is built when off")
	void logCallsGuarded() throws IOException {
		int calls = 0;
		try (var files = Files.walk(SRC)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".java") && !p.endsWith("SurveyorDebug.java")).toList()) {
				for (String line : Files.readAllLines(file)) {
					if (!line.contains("SurveyorDebug.log(") && !line.contains("SurveyorDebug.traffic(") && !line.contains("SurveyorDebug.tick(")) continue;
					calls++;
					assertTrue(line.contains("if (SurveyorDebug.on"), SRC.relativize(file) + ": unguarded debug call: " + line.trim());
				}
			}
		}
		assertTrue(calls > 10, "the mod logs its activity in debug mode");
	}
}
