package folk.sisby.surveyor.landmark;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.landmark.component.LandmarkComponentHolder;
import folk.sisby.surveyor.landmark.component.LandmarkComponentMap;
import folk.sisby.surveyor.landmark.component.LandmarkComponentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

public record Landmark(UUID owner, ResourceLocation id, LandmarkComponentMap components) implements LandmarkComponentHolder {
	public static Codec<Landmark> createCodec(UUID uuid, ResourceLocation id) {
		return RecordCodecBuilder.create(instance -> instance.group(
			LandmarkComponentMap.CODEC.fieldOf("components").forGetter(Landmark::components)
		).apply(instance, (components) -> new Landmark(uuid, id, components)));
	}

	public static Landmark create(UUID owner, ResourceLocation id, UnaryOperator<LandmarkComponentMap.Builder> componentChanges) {
		return new Landmark(owner, id, componentChanges.apply(LandmarkComponentMap.builder()).build());
	}

	public static Landmark global(ResourceLocation id, UnaryOperator<LandmarkComponentMap.Builder> componentChanges) {
		return create(WorldLandmarks.GLOBAL, id, componentChanges);
	}

	public static Landmark createIncremental(WorldLandmarks landmarks, UUID uuid, ResourceLocation prefix, UnaryOperator<LandmarkComponentMap.Builder> componentChanges) {
		int i = 1;
		while (landmarks.contains(uuid, ResourceLocation.fromNamespaceAndPath(prefix.getNamespace(), prefix.getPath() + "/" + i))) {
			i++;
		}
		return create(uuid, ResourceLocation.fromNamespaceAndPath(prefix.getNamespace(), prefix.getPath() + "/" + i), componentChanges);
	}

	public static Landmark globalIncremental(WorldLandmarks landmarks, ResourceLocation prefix, UnaryOperator<LandmarkComponentMap.Builder> componentChanges) {
		return createIncremental(landmarks, WorldLandmarks.GLOBAL, prefix, componentChanges);
	}

	public List<Component> toText() {
		List<Component> outList = new ArrayList<>();
		for (LandmarkComponentType<?> type : components.keySet().stream().sorted(Comparator.comparing(LandmarkComponentType::id)).toList()) {
			outList.add(Component.literal("").append(Component.literal(type.id().toString().replace("surveyor:", "")).withStyle(ChatFormatting.AQUA)).append(Component.literal(": ").append(getView(type))));
		}
		return outList;
	}

	public Tag toNbt() {
		return createCodec(owner, id).encodeStart(NbtOps.INSTANCE, this).resultOrPartial(Surveyor.LOGGER::error).orElseThrow();
	}

	/**
	 * Registry-aware {@link #toNbt()}: components such as enchanted item stacks only encode with registries.
	 */
	public Tag toNbt(HolderLookup.Provider registries) {
		return createCodec(owner, id).encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this).resultOrPartial(Surveyor.LOGGER::error).orElseGet(CompoundTag::new);
	}
}
