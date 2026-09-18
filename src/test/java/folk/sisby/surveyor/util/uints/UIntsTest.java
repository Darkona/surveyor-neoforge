package folk.sisby.surveyor.util.uints;

import io.netty.buffer.Unpooled;
import java.util.BitSet;
import java.util.Map;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class UIntsTest {
	private static final int[] CARDINALITIES = {2, 3, 255, 256, 257};

	private static int[] random(int cardinality, int max, long seed) {
		Random random = new Random(seed);
		int[] ints = new int[cardinality];
		for (int i = 0; i < cardinality; i++) ints[i] = max == Integer.MAX_VALUE ? random.nextInt() & Integer.MAX_VALUE : random.nextInt(max + 1);
		if (cardinality > 1) {
			ints[0] = max; // force the width, and a value in the top half of it
			ints[1] = max == 0 ? 0 : max - 1;
		}
		return ints;
	}

	private static void assertRoundTrips(int[] ints, Class<?> type) {
		UInts uints = UInts.ofMany(ints);
		assertInstanceOf(type, uints);
		for (int i = 0; i < ints.length; i++) assertEquals(ints[i], uints.get(i), "get(" + i + ")");

		CompoundTag nbt = new CompoundTag();
		uints.writeNbt(nbt, "k");
		UInts fromNbt = UInts.readNbt(nbt.get("k"), ints.length);
		assertInstanceOf(type, fromNbt, "NBT read picks another width");
		for (int i = 0; i < ints.length; i++) assertEquals(ints[i], fromNbt.get(i), "NBT get(" + i + ")");

		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		UInts.writeBuf(uints, buf);
		UInts fromBuf = UInts.readBuf(buf, ints.length);
		for (int i = 0; i < ints.length; i++) assertEquals(ints[i], fromBuf.get(i), "buf get(" + i + ")");
		assertEquals(0, buf.readableBytes(), "the reader leaves bytes behind");
	}

	@Test
	@DisplayName("Every array width round-trips through NBT and packets, even and odd lengths")
	void arraysRoundTrip() {
		Map<Integer, Class<?>> widths = Map.of(
			UInts.MAX_NIBBLE, UNibbleArray.class,
			UInts.MAX_BYTE, UByteArray.class,
			UInts.MAX_SHORT, UShortArray.class,
			Integer.MAX_VALUE, UIntArray.class
		);
		widths.forEach((max, type) -> {
			for (int cardinality : CARDINALITIES) {
				assertRoundTrips(random(cardinality, max, cardinality * 31L + max), type);
			}
		});
	}

	@Test
	@DisplayName("Shorts above Short.MAX_VALUE do not corrupt their packed neighbour")
	void highShortsKeepNeighbours() {
		assertRoundTrips(new int[]{1, 40_000, 2, 65_535, 32_768}, UShortArray.class);
		assertRoundTrips(new int[]{65_535, 65_535, 0, 32_768}, UShortArray.class);
	}

	@Test
	@DisplayName("A single value is stored at the smallest width, and the default as null")
	void singlesRoundTrip() {
		assertNull(UInts.fromUInts(new int[]{7, 7, 7}, 7));
		Map<Integer, Class<?>> singles = Map.of(0, UByte.class, 255, UByte.class, 256, UShort.class, 65_535, UShort.class, 65_536, UInt.class);
		singles.forEach((value, type) -> {
			UInts uints = UInts.fromUInts(new int[]{value, value, value}, -1);
			assertInstanceOf(type, uints);
			assertEquals(value, uints.get(2));
			CompoundTag nbt = new CompoundTag();
			uints.writeNbt(nbt, "k");
			assertEquals(value, UInts.readNbt(nbt.get("k"), 3).get(0));
			FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
			UInts.writeBuf(uints, buf);
			assertEquals(value, UInts.readBuf(buf, 3).get(0));
		});
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		UInts.writeBuf(null, buf);
		assertNull(UInts.readBuf(buf, 3));
	}

	@Test
	@DisplayName("getUnmasked() spreads packed values over the set bits of the mask")
	void unmaskedFollowsMask() {
		BitSet mask = new BitSet(8);
		mask.set(1);
		mask.set(4);
		mask.set(6);
		int[] unmasked = UInts.ofMany(new int[]{3, 9, 12}).getUnmasked(mask);
		assertEquals(3, unmasked[1]);
		assertEquals(9, unmasked[4]);
		assertEquals(12, unmasked[6]);
		assertEquals(0, unmasked[0] | unmasked[2] | unmasked[3] | unmasked[5] | unmasked[7]);
	}

	@Test
	@DisplayName("remap() rewrites mapped values, keeps unmapped ones, and re-picks the width")
	void remapRewritesValues() {
		UInts remapped = UInts.ofMany(new int[]{1, 2, 3, 2}).remap(v -> v == 2 ? 70_000 : null, 0, 4);
		assertInstanceOf(UIntArray.class, remapped);
		assertArrayEquals(new int[]{1, 70_000, 3, 70_000}, new int[]{remapped.get(0), remapped.get(1), remapped.get(2), remapped.get(3)});
		assertNull(new UByte((byte) 5).remap(v -> 9, 9, 4), "a single remapped onto the default becomes null");
		assertNull(UInts.remap(null, v -> null, 4, 4), "a null input stays the default");
	}
}
