package folk.sisby.surveyor.util.uints;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

public record UByte(byte value) implements SingleUInts {
	public static final byte TYPE = Tag.TAG_BYTE;

	public static UInts ofInt(int value) {
		return new UByte((byte) value);
	}

	public static UInts fromNbt(Tag nbt) {
		return new UByte(((ByteTag) nbt).getAsByte());
	}

	public static UInts fromBuf(FriendlyByteBuf buf) {
		return new UByte(buf.readByte());
	}

	@Override
	public int get() {
		return value & BYTE_MASK;
	}

	@Override
	public void writeNbt(CompoundTag nbt, String key) {
		nbt.putByte(key, value);
	}

	@Override
	public void writeBuf(FriendlyByteBuf buf) {
		buf.writeByte(value);
	}

	@Override
	public int getType() {
		return TYPE;
	}
}
