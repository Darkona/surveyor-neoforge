package folk.sisby.surveyor.util;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegistryPaletteTest {
	@SuppressWarnings("unchecked")
	private static Registry<String> registry(ResourceKey<? extends Registry<?>> key, String... entries) {
		MappedRegistry<String> registry = new MappedRegistry<>((ResourceKey<? extends Registry<String>>) key, Lifecycle.stable());
		for (String entry : entries) {
			registry.register(ResourceKey.create(registry.key(), ResourceLocation.parse(entry)), entry, RegistrationInfo.BUILT_IN);
		}
		return registry.freeze();
	}

	@Test
	@DisplayName("A value missing from the registry maps to the fallback instead of index -1")
	void unregisteredValueFallsBack() {
		RegistryPalette<String> palette = new RegistryPalette<>(registry(ResourceKey.createRegistryKey(ResourceLocation.parse("test:things")), "test:a", "test:b", "test:c"));
		assertEquals(0, palette.findOrAdd("test:c"));
		int unknown = palette.findOrAdd("not registered");
		assertEquals(0, palette.get(unknown), "fallback is id 0 in a registry without a default");
		assertEquals(unknown, palette.findOrAdd((String) null), "null maps to the same fallback entry");
		assertEquals(-1, palette.find(-1));
	}

	@Test
	@DisplayName("Unknown biomes map to the void biome, like missing biomes in saved regions")
	void unknownBiomeIsVoid() {
		Registry<String> biomes = registry(Registries.BIOME, "minecraft:plains", "minecraft:the_void", "minecraft:desert");
		RegistryPalette<String> palette = new RegistryPalette<>(biomes);
		assertEquals(biomes.getId("minecraft:the_void"), palette.get(palette.findOrAdd("end_phantasm:direct_biome")));
	}

	@Test
	@DisplayName("Ids beyond the registry size grow the palette instead of overflowing it")
	void largeIdsGrow() {
		RegistryPalette<String> palette = new RegistryPalette<>(registry(ResourceKey.createRegistryKey(ResourceLocation.parse("test:small")), "test:a", "test:b"));
		for (int id : new int[]{1, 5000, 0, 70_000, 5000}) {
			int index = palette.findOrAdd(id);
			assertEquals(id, palette.get(index));
			assertEquals(index, palette.find(id));
		}
		assertEquals(4, palette.view().size(), "5000 is added once");
	}
}
