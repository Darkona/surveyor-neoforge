package folk.sisby.surveyor.util.uints;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

public record UIntArray(int[] value) implements ArrayUInts {
	public static final byte TYPE = Tag.TAG_INT_ARRAY;

	public static UInts ofInts(int[] ints) {
		return new UIntArray(ints);
	}

	public static UInts fromNbt(Tag nbt, int cardinality) {
		int[] value = ((IntArrayTag) nbt).getAsIntArray();
		return value.length == cardinality ? new UIntArray(value) : UShortArray.ofPacked(value, cardinality);
	}

	public static UInts fromBuf(FriendlyByteBuf buf) {
		return new UIntArray(buf.readVarIntArray());
	}

	@Override
	public int get(int i) {
		return value[i];
	}

	@Override
	public void writeNbt(CompoundTag nbt, String key) {
		nbt.putIntArray(key, value);
	}

	@Override
	public void writeBuf(FriendlyByteBuf buf) {
		buf.writeVarIntArray(value);
	}

	@Override
	public int getType() {
		return TYPE;
	}
}
