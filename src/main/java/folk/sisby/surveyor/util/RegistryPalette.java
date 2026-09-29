package folk.sisby.surveyor.util;

import folk.sisby.surveyor.SurveyorDebug;
import folk.sisby.surveyor.Surveyor;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntIterators;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biomes;

public class RegistryPalette<T> implements IntIterable {
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
	private final Registry<T> registry;
	private final int fallbackId;
	private int[] raw;
	private int[] inverse;
	private final ValueView valueView;
	private int size;

	public RegistryPalette(Registry<T> registry) {
		this.registry = registry;
		this.raw = ArrayUtil.ofSingle(-1, registry.size());
		this.inverse = ArrayUtil.ofSingle(-1, registry.size());
		this.size = 0;
		this.valueView = new ValueView();
		this.fallbackId = fallbackId(registry);
	}

	// The void biome, like a region whose saved biome no longer exists; else the registry's default entry.
	private static <T> int fallbackId(Registry<T> registry) {
		if (registry.key().equals(Registries.BIOME)) {
			T voidBiome = registry.get(Biomes.THE_VOID.location());
			int voidId = voidBiome == null ? -1 : registry.getId(voidBiome);
			if (voidId >= 0) return voidId;
		}
		return registry instanceof DefaultedRegistry<T> defaulted ? Math.max(0, defaulted.getId(defaulted.get(defaulted.getDefaultKey()))) : 0;
	}

	// Fix: ids can be -1 (a value that isn't registered, e.g. a mod's biome held directly) or at least registry.size()
	// (ids with gaps); both used to crash the chunk scan every time the chunk loaded.
	public int find(int value) {
		return value >= 0 && value < inverse.length ? inverse[value] : -1;
	}

	private int add(int value) {
		if (value >= inverse.length) {
			int length = inverse.length;
			inverse = Arrays.copyOf(inverse, Math.max(value + 1, length * 2));
			Arrays.fill(inverse, length, inverse.length, -1);
		}
		if (size == raw.length) raw = Arrays.copyOf(raw, Math.max(1, size * 2));
		raw[size] = value;
		inverse[value] = size;
		T object = registry.byId(value);
		valueView.values.add(object);
		size++;
		return size - 1;
	}

	public int findOrAdd(int value) {
		if (value < 0) value = fallbackId;
		int index = find(value);
		return index == -1 ? add(value) : index;
	}

	public int findOrAdd(T value) {
		int id = value == null ? -1 : registry.getId(value);
		if (id < 0) SurveyorDebug.count(SurveyorDebug.Count.PALETTE_FALLBACKS);
		if (id < 0 && WARNED.add(registry.key().location() + "/" + value)) {
			Surveyor.LOGGER.warn("[Surveyor] {} isn't in the {} registry; mapping it as {}. Report this to the mod that adds it.", value, registry.key().location(), registry.getKey(registry.byId(fallbackId)));
		}
		return findOrAdd(id);
	}

	public int get(int index) {
		return raw[index];
	}

	public @NotNull IntIterator iterator() {
		return IntIterators.wrap(raw, 0, size);
	}

	public ValueView view() {
		return valueView;
	}

	public class ValueView implements IdMap<T> {
		private final T defaultValue = registry instanceof DefaultedRegistry<T> defreg ? defreg.get(defreg.getDefaultKey()) : registry.byId(0);
		// Fix: in singleplayer map mods read this on the client thread while the server thread's chunk scan appends to it;
		// an ArrayList could hand them a null or stale entry mid-growth. Appends are rare (new palette entries only).
		private final List<T> values = new CopyOnWriteArrayList<>();
		private boolean errored = false;

		public Registry<T> registry() {
			return registry;
		}

		@Override
		public T byId(int index) {
			if (index >= values.size()) {
				if (!errored) {
					Surveyor.LOGGER.error("[Surveyor] Palette view access at index {} for palette size {}! Returning garbage!", index, values.size(), new IllegalStateException("garbage palette"));
					errored = true;
				}
				return defaultValue;
			}
			return values.get(index);
		}

		@Override
		public int getId(T value) {
			return values.indexOf(value);
		}

		@Override
		public @NotNull Iterator<T> iterator() {
			return values.iterator();
		}

		@Override
		public int size() {
			return size;
		}
	}
}
