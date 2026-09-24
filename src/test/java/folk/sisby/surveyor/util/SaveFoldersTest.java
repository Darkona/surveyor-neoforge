package folk.sisby.surveyor.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveFoldersTest {
	private static final long SEED = -4172144997902289642L;
	private static final UUID ID = UUID.fromString("1b2c3d4e-0000-4000-8000-000000000131");

	@TempDir
	Path root;

	private Path seedFile(String name, byte[] data) throws IOException {
		Path file = root.resolve(String.valueOf(SEED)).resolve("minecraft/overworld").resolve(name);
		Files.createDirectories(file.getParent());
		Files.write(file, data);
		return file;
	}

	@Test
	@DisplayName("Servers without a world id keep the seed folder")
	void noIdUsesSeed() {
		assertEquals(String.valueOf(SEED), SaveFolders.folderName(root, SEED, null));
		assertFalse(Files.exists(root.resolve(ID.toString())));
	}

	@Test
	@DisplayName("A new world id without a seed folder starts empty")
	void newIdNoSeed() {
		assertEquals(ID.toString(), SaveFolders.folderName(root, SEED, ID));
		assertFalse(Files.exists(root.resolve(ID.toString())), "nothing is copied");
	}

	@Test
	@DisplayName("The seed folder is copied once into the id folder and left in place")
	void copiesSeedFolder() throws IOException {
		byte[] data = {1, 2, 3, 4};
		Path original = seedFile("r.0.0.dat", data);
		assertEquals(ID.toString(), SaveFolders.folderName(root, SEED, ID));
		Path copy = root.resolve(ID.toString()).resolve("minecraft/overworld/r.0.0.dat");
		assertArrayEquals(data, Files.readAllBytes(copy));
		assertArrayEquals(data, Files.readAllBytes(original), "the seed folder is untouched");
		assertFalse(Files.exists(root.resolve(ID + SaveFolders.TEMP_SUFFIX)));

		// Later writes to the seed folder (e.g. another backend with the same seed) don't reach the id folder again.
		Files.write(original, new byte[]{9});
		assertEquals(ID.toString(), SaveFolders.folderName(root, SEED, ID));
		assertArrayEquals(data, Files.readAllBytes(copy));
	}

	@Test
	@DisplayName("A copy left half done is thrown away and redone")
	void redoesUnfinishedCopy() throws IOException {
		seedFile("r.0.0.dat", new byte[]{5});
		Path temp = root.resolve(ID + SaveFolders.TEMP_SUFFIX).resolve("minecraft/overworld");
		Files.createDirectories(temp);
		Files.write(temp.resolve("partial.dat"), new byte[]{0});
		assertEquals(ID.toString(), SaveFolders.folderName(root, SEED, ID));
		Path id = root.resolve(ID.toString()).resolve("minecraft/overworld");
		assertTrue(Files.exists(id.resolve("r.0.0.dat")));
		assertFalse(Files.exists(id.resolve("partial.dat")));
		assertFalse(Files.exists(root.resolve(ID + SaveFolders.TEMP_SUFFIX)));
	}
}
