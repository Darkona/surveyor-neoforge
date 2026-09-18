package folk.sisby.surveyor.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BitSetUtilTest {
	@Test
	@DisplayName("half() splits a bitset into two disjoint halves that add back up to it")
	void halfPartitions() {
		Random random = new Random(3);
		for (int n : new int[]{0, 1, 2, 3, 100, 1024}) {
			BitSet original = new BitSet(RegionPos.CHUNK_AREA);
			while (original.cardinality() < n) original.set(random.nextInt(RegionPos.CHUNK_AREA));
			List<BitSet> halves = new ArrayList<>(BitSetUtil.half(original));
			BitSet a = halves.get(0), b = halves.get(1);
			assertFalse(a.intersects(b), "halves overlap for " + n);
			BitSet union = (BitSet) a.clone();
			union.or(b);
			assertEquals(original, union, "halves lose bits for " + n);
			assertEquals(n / 2, Math.min(a.cardinality(), b.cardinality()), "uneven split for " + n);
		}
	}

	@Test
	@DisplayName("splitSet() keeps the values of the bits still set, in order")
	void splitSetKeepsOrder() {
		BitSet old = BitSet.valueOf(new long[]{0b1011_0110});
		BitSet now = BitSet.valueOf(new long[]{0b1001_0100});
		// old bits 1,2,4,5,7 → values a..e; still set: 2,4,7 → b,c,e
		assertEquals(List.of("b", "c", "e"), ListUtil.splitSet(List.of("a", "b", "c", "d", "e"), now, old));
	}
}
