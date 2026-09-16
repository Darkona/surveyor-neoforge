package folk.sisby.surveyor.util.uints;

import folk.sisby.surveyor.util.ArrayUtil;
import java.util.Arrays;
import java.util.BitSet;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * A compressed representation of an int array for holding unsigned ints.
 * Keeps size down in memory, as well as in NBT and packets.
 * Designed to be paired with a bitset as a mask, so it can represent nullable values within a static-sized array.
 *
 * @author Sisby folk
 * @author Falkreon
 * @author Ampflower
 */
public interface UInts {
	byte NULL_TYPE = Tag.TAG_END;
	int MAX_SHORT = Short.MAX_VALUE - Short.MIN_VALUE;
	int MAX_BYTE = Byte.MAX_VALUE - Byte.MIN_VALUE;
	int MAX_NIBBLE = MAX_BYTE >> 4;
	int NIBBLE_SIZE = Byte.SIZE >> 1;
	int SHORT_MASK = 0xFFFF;
	int BYTE_MASK = 0xFF;
	int NIBBLE_MASK = 0xF;

	static UInts remap(UInts input, Function<Integer, Integer> remapping, int defaultValue, int cardinality) {
		return (input == null ? new UInt(defaultValue) : input).remap(remapping, defaultValue, cardinality);
	}

	static void writeBuf(UInts array, FriendlyByteBuf buf) {
		if (array == null) {
			buf.writeVarInt(NULL_TYPE);
			return;
		}
		buf.writeVarInt(array.getType());
		array.writeBuf(buf);
	}

	static UInts readNbt(Tag nbt, int cardinality) {
		if (nbt == null) return null;
		return switch (nbt.getId()) {
			case UByte.TYPE -> UByte.fromNbt(nbt);
			case UShort.TYPE -> UShort.fromNbt(nbt);
			case UInt.TYPE -> UInt.fromNbt(nbt);
			case UByteArray.TYPE -> UByteArray.fromNbt(nbt, cardinality);
			case UIntArray.TYPE -> UIntArray.fromNbt(nbt, cardinality);
			default -> throw new IllegalStateException("UIntArray encountered unexpected NBT type: " + nbt.getId());
		};
	}

	static UInts readBuf(FriendlyByteBuf buf, int cardinality) {
		int type = buf.readVarInt();
		return switch (type) {
			case UInts.NULL_TYPE -> null;
			case UByte.TYPE -> UByte.fromBuf(buf);
			case UShort.TYPE -> UShort.fromBuf(buf);
			case UInt.TYPE -> UInt.fromBuf(buf);
			case UNibbleArray.TYPE -> UNibbleArray.fromBuf(buf);
			case UByteArray.TYPE -> UByteArray.fromBuf(buf);
			case UShortArray.TYPE -> UShortArray.fromBuf(buf, cardinality);
			case UIntArray.TYPE -> UIntArray.fromBuf(buf);
			default -> throw new IllegalStateException("UIntArray encountered unexpected buf type: " + type);
		};
	}

	static UInts fromUInts(int[] uints, int defaultValue) {
		return ArrayUtil.isSingle(uints) ? ofSingle(uints[0], defaultValue) : ofMany(uints);
	}

	static UInts ofMany(int[] uints) {
		int max = Arrays.stream(uints).max().orElseThrow();
		if (max <= MAX_NIBBLE) return UNibbleArray.ofInts(uints);
		if (max <= MAX_BYTE) return UByteArray.ofInts(uints);
		if (max <= MAX_SHORT) return UShortArray.ofInts(uints);
		return UIntArray.ofInts(uints);
	}

	static UInts ofSingle(int uint, int defaultValue) {
		if (uint == defaultValue) return null;
		if (uint <= MAX_BYTE) return UByte.ofInt(uint);
		if (uint <= MAX_SHORT) return UShort.ofInt(uint);
		return UInt.ofInt(uint);
	}

	int getType();

	int[] getUnmasked(BitSet mask);

	void writeNbt(CompoundTag nbt, String key);

	void writeBuf(FriendlyByteBuf buf);

	int get(int i);

	UInts remap(Function<Integer, Integer> remapping, int defaultValue, int cardinality);
}
