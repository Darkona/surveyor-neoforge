package folk.sisby.surveyor.util;

import folk.sisby.surveyor.Surveyor;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

// Fix: summary files are written in order per file and atomically; forced saves wait for them.
public final class SafeNbtWriter {
	private static final Map<Path, CompletableFuture<Void>> PENDING = new ConcurrentHashMap<>();

	private SafeNbtWriter() {
	}

	public static CompletableFuture<Void> write(Path path, CompoundTag nbt) {
		return write(path, nbt, Util.ioPool());
	}

	static CompletableFuture<Void> write(Path path, CompoundTag nbt, Executor executor) {
		Path key = path.toAbsolutePath().normalize();
		CompletableFuture<Void> queued = PENDING.compute(key, (k, previous) -> {
			CompletableFuture<Void> after = previous == null ? CompletableFuture.completedFuture(null) : previous.handle((v, t) -> null);
			return after.thenRunAsync(() -> writeNow(k, nbt), executor);
		});
		queued.whenComplete((v, t) -> PENDING.remove(key, queued));
		return queued;
	}

	public static void flush() {
		for (CompletableFuture<?> pending : PENDING.values().toArray(CompletableFuture[]::new)) {
			pending.handle((v, t) -> null).join();
		}
	}

	private static void writeNow(Path path, CompoundTag nbt) {
		Path temp = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			Files.createDirectories(path.getParent());
			NbtIo.writeCompressed(nbt, temp);
			try {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			Surveyor.LOGGER.error("[Surveyor] Error writing {}.", path, e);
		}
	}
}
