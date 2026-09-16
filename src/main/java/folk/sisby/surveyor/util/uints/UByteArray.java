package folk.sisby.surveyor.util.uints;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

public record UByteArray(byte[] value) implements ArrayUInts {
	public static final byte TYPE = Tag.TAG_BYTE_ARRAY;

	public static UInts ofInts(int[] ints) {
		byte[] value = new byte[ints.length];
		for (int i = 0; i < ints.length; i++) {
			value[i] = (byte) ints[i];
		}
		return new UByteArray(value);
	}

	public static UInts fromNbt(Tag nbt, int cardinality) {
		byte[] value = ((ByteArrayTag) nbt).getAsByteArray();
		return value.length == cardinality ? new UByteArray(value) : new UNibbleArray(value);
	}

	public static UInts fromBuf(FriendlyByteBuf buf) {
		return new UByteArray(buf.readByteArray());
	}

	@Override
	public int get(int i) {
		return value[i] & BYTE_MASK;
	}

	@Override
	public void writeNbt(CompoundTag nbt, String key) {
		nbt.putByteArray(key, value);
	}

	@Override
	public void writeBuf(FriendlyByteBuf buf) {
		buf.writeByteArray(value);
	}

	@Override
	public int getType() {
		return TYPE;
	}
}
