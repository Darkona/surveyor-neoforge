package folk.sisby.surveyor.util.uints;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

public record UShortArray(short[] value) implements ArrayUInts {
	public static final int TYPE = Tag.TAG_STRING;

	public static UInts ofInts(int[] ints) {
		short[] value = new short[ints.length];
		for (int i = 0; i < ints.length; i++) {
			value[i] = (short) ints[i];
		}
		return new UShortArray(value);
	}

	public static UInts ofPacked(int[] ints, int cardinality) {
		short[] value = new short[cardinality];
		for (int i = 0; i < value.length; i += 2) {
			value[i] = (short) (ints[i / 2] >>> Short.SIZE);
		}
		for (int i = 1; i < value.length; i += 2) {
			value[i] = (short) (ints[i / 2] & SHORT_MASK);
		}
		return new UShortArray(value);
	}

	public static UInts fromBuf(FriendlyByteBuf buf, int cardinality) {
		return ofPacked(buf.readVarIntArray(), cardinality);
	}

	public int[] packToInts() {
		int[] packed = new int[value.length / 2 + (value.length & 1)];
		for (int i = 0; i < value.length; i += 2) {
			packed[i / 2] |= value[i] << Short.SIZE;
		}
		for (int i = 1; i < value.length; i += 2) {
			packed[i / 2] |= value[i];
		}
		return packed;
	}

	@Override
	public int get(int i) {
		return value[i] & SHORT_MASK;
	}

	@Override
	public void writeNbt(CompoundTag nbt, String key) {
		nbt.putIntArray(key, packToInts());
	}

	@Override
	public void writeBuf(FriendlyByteBuf buf) {
		buf.writeVarIntArray(packToInts());
	}

	@Override
	public int getType() {
		return TYPE;
	}
}
