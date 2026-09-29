package folk.sisby.surveyor;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLongArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Debug mode: logs what Surveyor does, for tests, smoke servers and bug reports. On with the {@code debug} option or
 * {@code -Dsurveyor.debug=true}.
 * <p>
 * Costs nothing when off: hot paths only call {@link #count} (a boolean check, no allocation) and every
 * {@link #log} call site is guarded with {@code if (SurveyorDebug.on)}. Per-chunk and per-packet activity is summed and
 * logged once every {@value #SUMMARY_TICKS} ticks, as one line with the counters that changed.
 */
public final class SurveyorDebug {
	public static final String PROPERTY = "surveyor.debug";
	public static final String PREFIX = "[Surveyor/debug] ";
	public static final int SUMMARY_TICKS = 100;
	private static final Logger LOGGER = LoggerFactory.getLogger("surveyor/debug");

	/**
	 * Whether debug mode is on: the config option or the system property. Read on hot paths.
	 */
	public static boolean on = Boolean.getBoolean(PROPERTY);

	public enum Count {
		CHUNKS_OFF_THREAD("chunks.offThread"),
		CHUNKS_IN_PLACE("chunks.inPlace"),
		CHUNKS_IN_PLACE_READS_LEVEL("chunks.inPlace.readsLevel"),
		CHUNKS_IN_PLACE_QUEUE_FULL("chunks.inPlace.queueFull"),
		CHUNKS_IN_PLACE_ASYNC_OFF("chunks.inPlace.asyncOff"),
		CHUNKS_PUBLISHED("chunks.published"),
		CHUNKS_STALE("chunks.stale"),
		CHUNKS_FAILED("chunks.failed"),
		CHUNKS_PENDING_MAX("chunks.pendingMax"),
		REGIONS_READ("regions.read"),
		REGIONS_WRITTEN("regions.written"),
		REGIONS_UNLOADED("regions.unloaded"),
		STRUCTURES_ADDED("structures.added"),
		STRUCTURE_REGIONS_READ("structureRegions.read"),
		STRUCTURE_REGIONS_DROPPED("structureRegions.dropped"),
		LANDMARKS_PUT("landmarks.put"),
		LANDMARKS_REMOVED("landmarks.removed"),
		LANDMARKS_FROZEN("landmarks.ignoredFrozen"),
		POIS_ADDED("pois.added"),
		POIS_DEFERRED("pois.deferred"),
		POIS_RESOLVED("pois.resolved"),
		POIS_WAITING_AGAIN("pois.waitingAgain"),
		POIS_SKIPPED_WORLDGEN("pois.skippedWorldgen"),
		POSITIONS_SENT("positions.groupsSent"),
		POSITIONS_HIDDEN("positions.hiddenSkipped"),
		PALETTE_FALLBACKS("palette.fallbacks");

		final String key;

		Count(String key) {
			this.key = key;
		}
	}

	private static final Count[] COUNTS = Count.values();
	private static final AtomicLongArray VALUES = new AtomicLongArray(COUNTS.length);
	// Packet traffic by direction and type: {packets, payloads, players}. Only touched while debug mode is on.
	private static final Map<String, long[]> TRAFFIC = new TreeMap<>();

	private SurveyorDebug() {
	}

	/**
	 * Sets debug mode from the config; the system property keeps it on.
	 */
	public static void configure(boolean config) {
		boolean wasOn = on;
		on = config || Boolean.getBoolean(PROPERTY);
		if (on && !wasOn) LOGGER.info(PREFIX + "Debug mode on; activity is summed up every {} ticks.", SUMMARY_TICKS);
	}

	public static void count(Count count) {
		if (on) VALUES.incrementAndGet(count.ordinal());
	}

	public static void count(Count count, int amount) {
		if (on) VALUES.addAndGet(count.ordinal(), amount);
	}

	public static void max(Count count, int value) {
		if (!on) return;
		int i = count.ordinal();
		for (long old = VALUES.get(i); value > old && !VALUES.compareAndSet(i, old, value); old = VALUES.get(i)) ;
	}

	/**
	 * Counts packets of one type; guard the call with {@code if (SurveyorDebug.on)}.
	 */
	public static void traffic(String direction, Object type, int payloads, int players) {
		if (!on) return;
		synchronized (TRAFFIC) {
			long[] values = TRAFFIC.computeIfAbsent(direction + " " + type, k -> new long[3]);
			values[0]++;
			values[1] += payloads;
			values[2] += players;
		}
	}

	/**
	 * Logs one event; guard the call with {@code if (SurveyorDebug.on)} so nothing is built when off.
	 */
	public static void log(String format, Object... args) {
		LOGGER.info(PREFIX + format, args);
	}

	/**
	 * Called every tick of the server and of the client; logs the summary every {@value #SUMMARY_TICKS} ticks.
	 */
	public static void tick(long tick) {
		if (!on || tick % SUMMARY_TICKS != 0) return;
		String summary = takeSummary();
		if (summary != null) LOGGER.info(PREFIX + "Last {} ticks: {}", SUMMARY_TICKS, summary);
	}

	/**
	 * The counters and traffic since the last call, then resets them; null when nothing happened.
	 */
	static String takeSummary() {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < COUNTS.length; i++) {
			long value = VALUES.getAndSet(i, 0);
			if (value != 0) builder.append(builder.isEmpty() ? "" : ", ").append(COUNTS[i].key).append('=').append(value);
		}
		synchronized (TRAFFIC) {
			for (Map.Entry<String, long[]> entry : TRAFFIC.entrySet()) {
				long[] v = entry.getValue();
				builder.append(builder.isEmpty() ? "" : "; ").append(entry.getKey()).append(" x").append(v[0]).append(" (payloads ").append(v[1]).append(", players ").append(v[2]).append(')');
			}
			TRAFFIC.clear();
		}
		return builder.isEmpty() ? null : builder.toString();
	}
}
