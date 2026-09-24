package folk.sisby.surveyor.util;

import folk.sisby.surveyor.Surveyor;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Picks the client's save folder for a server (sisby-folk/surveyor#131). Servers that send a world id get a folder
 * named by it; others keep the seed folder. The first time an id folder is needed, the seed folder is copied into it,
 * so the map carries over; the seed folder itself is never moved or deleted.
 */
public final class SaveFolders {
	public static final String TEMP_SUFFIX = ".tmp";

	private SaveFolders() {
	}

	public static String seedFolder(long seed) {
		return String.valueOf(seed);
	}

	public static String folderName(Path root, long seed, @Nullable UUID worldId) {
		String seedFolder = seedFolder(seed);
		if (worldId == null) return seedFolder;
		String idFolder = worldId.toString();
		Path idPath = root.resolve(idFolder);
		if (Files.isDirectory(idPath)) return idFolder;
		Path seedPath = root.resolve(seedFolder);
		if (!Files.isDirectory(seedPath)) return idFolder;
		Path temp = root.resolve(idFolder + TEMP_SUFFIX);
		try {
			deleteTree(temp); // left by a copy that didn't finish
			copyTree(seedPath, temp);
			try {
				Files.move(temp, idPath, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, idPath);
			}
			Surveyor.LOGGER.info("[Surveyor] This server sent world id {}; copied the map from seed folder {} to {}.", worldId, seedFolder, idFolder);
			return idFolder;
		} catch (IOException e) {
			Surveyor.LOGGER.error("[Surveyor] Error copying seed folder {} to world id folder {}; using the seed folder this session.", seedFolder, idFolder, e);
			return seedFolder;
		}
	}

	private static void copyTree(Path from, Path to) throws IOException {
		Files.walkFileTree(from, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Files.createDirectories(to.resolve(from.relativize(dir)));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.copy(file, to.resolve(from.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) return;
		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
