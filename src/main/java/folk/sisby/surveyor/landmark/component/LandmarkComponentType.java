package folk.sisby.surveyor.landmark.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.SurveyorCodecs;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public record LandmarkComponentType<T>(ResourceLocation id, Codec<T> codec, Function<T, Component> viewer) {
	private static final Map<ResourceLocation, LandmarkComponentType<?>> TYPES = new HashMap<>();
	public static final Codec<LandmarkComponentType<?>> CODEC = ResourceLocation.CODEC.comapFlatMap(LandmarkComponentType::decode, LandmarkComponentType::id);

	private static DataResult<? extends LandmarkComponentType<?>> decode(ResourceLocation id) {
		LandmarkComponentType<?> type = TYPES.get(id);
		if (type != null) return DataResult.success(type);
		Surveyor.LOGGER.warn("[Surveyor] Generating passthrough for unregistered component type {}", id);
		return DataResult.success(LandmarkComponentTypes.register(id, SurveyorCodecs.NBT_ELEMENT, NbtUtils::toPrettyComponent));
	}

	public static boolean containsType(ResourceLocation id) {
		return TYPES.containsKey(id);
	}

	public static LandmarkComponentType<?> getType(ResourceLocation id) {
		return TYPES.get(id);
	}

	public static Set<ResourceLocation> keySet() {
		return new HashSet<>(TYPES.keySet());
	}

	public static void register(LandmarkComponentType<?> type) {
		if (containsType(type.id())) {
			throw new IllegalArgumentException("Multiple landmark types registered to the same ID: %s".formatted(type.id()));
		}
		TYPES.put(type.id(), type);
	}
}
