package folk.sisby.surveyor.util;

import java.util.Arrays;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

public class ArrayUtil {
	public static int[] ofSingle(int value, int size) {
		int[] array = new int[size];
		Arrays.fill(array, value);
		return array;
	}

	public static boolean isSingle(int[] ints) {
		int head = ints[0];
		for (int i : ints) {
			if (i != head) return false;
		}
		return true;
	}

	public static Vec3 toVec3d(double[] doubles) {
		return new Vec3(doubles[0], doubles[1], doubles[2]);
	}

	public static int[] ofBlockBox(BoundingBox box) {
		return new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()};
	}
}
