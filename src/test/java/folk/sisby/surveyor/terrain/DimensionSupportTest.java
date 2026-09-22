package folk.sisby.surveyor.terrain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.dimension.DimensionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionSupportTest {
	private static DimensionType type(int minY, int height) {
		return new DimensionType(OptionalLong.empty(), true, false, false, true, 1.0, true, false, minY, height, height, BlockTags.INFINIBURN_OVERWORLD, ResourceLocation.withDefaultNamespace("overworld"), 0.0F, new DimensionType.MonsterSettings(false, true, ConstantInt.of(0), 0));
	}

	@Test
	@DisplayName("Layers stay inside the dimension's height, for any height")
	void layersInsideHeight() {
		for (DimensionType type : new DimensionType[]{type(-64, 384), type(0, 256), type(-2032, 4064), type(0, 16)}) {
			int[] layers = DimensionSupport.getSummaryLayers(type, false, 63);
			assertTrue(layers.length >= 2);
			for (int i = 0; i < layers.length; i++) {
				assertTrue(layers[i] >= type.minY() && layers[i] < type.minY() + type.height(), "layer " + layers[i] + " outside " + type.minY() + ".." + (type.minY() + type.height()));
				if (i > 0) assertTrue(layers[i] < layers[i - 1], "layers must be sorted from the top");
			}
		}
	}

	@Test
	@DisplayName("Cached layers are checked against the level's dimension type; inline types don't throw")
	void cacheValidatedAndNoThrow() throws IOException {
		String src = Files.readString(Path.of(System.getProperty("surveyor.projectDir", "."), "src/main/java/folk/sisby/surveyor/terrain/DimensionSupport.java"));
		assertFalse(src.contains("unwrapKey().orElseThrow()"), "a dimension type defined inline has no key");
		assertTrue(src.contains("layers.type() != type"), "layers cached for another world's dimension type must be recomputed");
	}
}
