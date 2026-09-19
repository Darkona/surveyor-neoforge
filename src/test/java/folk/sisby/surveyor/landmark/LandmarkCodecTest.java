package folk.sisby.surveyor.landmark;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import folk.sisby.surveyor.landmark.component.LandmarkComponentType;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.packet.SurveyorPacketCodecs;
import folk.sisby.surveyor.util.TextUtil;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandmarkCodecTest {
	private static final UUID OWNER = UUID.fromString("12345678-1234-1234-1234-123456789abc");
	private static final LandmarkComponentType<Integer> UNENCODABLE = LandmarkComponentTypes.register(
		ResourceLocation.fromNamespaceAndPath("test", "unencodable"),
		Codec.INT.flatComapMap(i -> i, i -> DataResult.error(() -> "needs registries")),
		i -> Component.literal("?")
	);

	private static Landmark landmark(String path) {
		return Landmark.create(OWNER, ResourceLocation.fromNamespaceAndPath("test", path), b -> b
			.add(LandmarkComponentTypes.POS, new BlockPos(1, 64, -3))
			.add(LandmarkComponentTypes.NAME, Component.literal(path))
			.add(LandmarkComponentTypes.COLOR, 0xFF8800));
	}

	private static Table<UUID, ResourceLocation, Landmark> table(Landmark... landmarks) {
		Table<UUID, ResourceLocation, Landmark> table = HashBasedTable.create();
		for (Landmark landmark : landmarks) table.put(landmark.owner(), landmark.id(), landmark);
		return table;
	}

	private static RegistryFriendlyByteBuf buf() {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
	}

	@Test
	@DisplayName("A death message loses the hover and click events of its arguments too")
	void stripsTranslationArguments() {
		Style interactive = Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("item tooltip"))).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kill"));
		Component weapon = Component.literal("[Blade]").withStyle(interactive).append(Component.literal(" sibling").withStyle(interactive));
		Component message = Component.translatable("death.attack.player.item", Component.literal("Victim").withStyle(interactive), Component.literal("Killer"), weapon).withStyle(interactive);

		Component stripped = TextUtil.stripInteraction(message);
		assertEquals(message.getString(), stripped.getString(), "the text must stay the same");
		assertFalse(stripped.toString().contains("HoverEvent") || stripped.toString().contains("ClickEvent"), "an interaction survived: " + stripped);
	}

	@Test
	@DisplayName("A landmark that can't be encoded is left out; the others are saved")
	void encodeSkipsBadLandmarks() {
		Landmark bad = Landmark.create(OWNER, ResourceLocation.fromNamespaceAndPath("test", "grave/1"), b -> b.add(UNENCODABLE, 7));
		// a component error only drops that component (partial result); the landmark and its neighbours stay
		CompoundTag nbt = WorldLandmarks.encode(table(landmark("a"), bad, landmark("b")), NbtOps.INSTANCE);
		CompoundTag owner = nbt.getCompound(OWNER.toString());
		assertTrue(owner.contains("test:a") && owner.contains("test:b"), "good landmarks must be saved: " + nbt);
	}

	@Test
	@DisplayName("The landmark packet codec writes exactly what the original codec wrote")
	void wireFormatUnchanged() {
		Table<UUID, ResourceLocation, Landmark> landmarks = table(landmark("a"), landmark("b/c"));
		RegistryFriendlyByteBuf ours = buf(), original = buf();
		SurveyorPacketCodecs.LANDMARK_SUMMARIES.encode(ours, landmarks);
		ByteBufCodecs.fromCodec(WorldLandmarks.CODEC).encode(original, landmarks);
		byte[] a = new byte[ours.readableBytes()], b = new byte[original.readableBytes()];
		ours.getBytes(0, a);
		original.getBytes(0, b);
		assertArrayEquals(b, a, "an original client or server must read our packets");

		Table<UUID, ResourceLocation, Landmark> decoded = SurveyorPacketCodecs.LANDMARK_SUMMARIES.decode(original);
		assertEquals(landmarks.cellSet().size(), decoded.cellSet().size());
		assertEquals(new BlockPos(1, 64, -3), decoded.get(OWNER, ResourceLocation.fromNamespaceAndPath("test", "b/c")).get(LandmarkComponentTypes.POS));
	}

	@Test
	@DisplayName("One landmark with an invalid id doesn't drop the packet (the sender used to be kicked)")
	void decodeSkipsInvalidIds() {
		CompoundTag nbt = WorldLandmarks.encode(table(landmark("good")), NbtOps.INSTANCE);
		CompoundTag owner = nbt.getCompound(OWNER.toString());
		owner.put("test:Tamed1/white/10/20", owner.getCompound("test:good").copy()); // uppercase: not a valid id
		RegistryFriendlyByteBuf buf = buf();
		ByteBufCodecs.tagCodec(() -> net.minecraft.nbt.NbtAccounter.create(2097152L)).encode(buf, nbt);

		Table<UUID, ResourceLocation, Landmark> decoded = SurveyorPacketCodecs.LANDMARK_SUMMARIES.decode(buf);
		assertEquals(1, decoded.size());
		assertTrue(decoded.contains(OWNER, ResourceLocation.fromNamespaceAndPath("test", "good")));
	}

	@Test
	@DisplayName("Saving and loading keep every landmark, through registry ops")
	void nbtRoundTrip() {
		Table<UUID, ResourceLocation, Landmark> landmarks = table(landmark("a"), landmark("b"));
		RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, RegistryAccess.EMPTY);
		Table<UUID, ResourceLocation, Landmark> back = WorldLandmarks.decode(WorldLandmarks.encode(landmarks, ops), ops);
		assertEquals(landmarks.rowMap().keySet(), back.rowMap().keySet());
		assertEquals(landmarks.columnKeySet(), back.columnKeySet());
		assertNull(back.get(UUID.randomUUID(), ResourceLocation.fromNamespaceAndPath("test", "a")));
	}

	@Test
	@DisplayName("An enchanted weapon in a death message or a stack component saves with registries (Apotheosis/PvP crash)")
	void enchantedStacksSave() {
		HolderLookup.Provider registries = VanillaRegistries.createLookup();
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 5);
		sword.set(DataComponents.CUSTOM_NAME, Component.literal("Blade"));
		Component deathMessage = Component.translatable("death.attack.player.item", Component.literal("Victim"), Component.literal("Killer"), sword.getDisplayName());

		// the original bug: the weapon's SHOW_ITEM hover can't be encoded without registries
		assertTrue(ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, deathMessage).error().isPresent(), "the reproduction must fail without the fix");
		Component grave = TextUtil.stripInteraction(deathMessage);
		assertTrue(ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, grave).result().isPresent(), "the stripped grave name must encode anywhere");

		Landmark waypoint = Landmark.create(OWNER, ResourceLocation.fromNamespaceAndPath("test", "icon"), b -> b.add(LandmarkComponentTypes.STACK, sword).add(LandmarkComponentTypes.NAME, grave));
		RegistryOps<net.minecraft.nbt.Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
		CompoundTag nbt = WorldLandmarks.encode(table(waypoint), ops);
		Landmark back = WorldLandmarks.decode(nbt, ops).get(OWNER, waypoint.id());
		assertEquals(5, back.get(LandmarkComponentTypes.STACK).getEnchantments().getLevel(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS)));
	}
}
