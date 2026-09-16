package folk.sisby.surveyor.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SafeNbtWriterTest {
	@TempDir
	Path dir;

	@Test
	@DisplayName("Writes of one file land in submission order, even on a many-thread pool")
	void writesAreOrderedPerFile() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			Path file = dir.resolve("c.0.0.dat");
			for (int i = 0; i < 300; i++) {
				CompoundTag nbt = new CompoundTag();
				nbt.putInt("v", i);
				nbt.putIntArray("payload", new int[(i * 37) % 4000]);
				SafeNbtWriter.write(file, nbt, pool);
			}
			SafeNbtWriter.flush();
			assertEquals(299, NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getInt("v"), "an older snapshot overwrote a newer one");
			assertFalse(Files.exists(dir.resolve("c.0.0.dat.tmp")), "the temp file must be moved into place");
		} finally {
			pool.shutdown();
		}
	}

	@Test
	@DisplayName("Different files are written independently and all are flushed")
	void flushWaitsForEveryFile() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			for (int f = 0; f < 20; f++) {
				CompoundTag nbt = new CompoundTag();
				nbt.putInt("f", f);
				SafeNbtWriter.write(dir.resolve("s." + f + ".dat"), nbt, pool);
			}
			SafeNbtWriter.flush();
			for (int f = 0; f < 20; f++) {
				assertEquals(f, NbtIo.readCompressed(dir.resolve("s." + f + ".dat"), NbtAccounter.unlimitedHeap()).getInt("f"));
			}
		} finally {
			pool.shutdown();
		}
	}
}
