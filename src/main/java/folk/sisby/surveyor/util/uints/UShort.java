package folk.sisby.surveyor.util.uints;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

public record UShort(short value) implements SingleUInts {
	public static final byte TYPE = Tag.TAG_SHORT;

	public static UInts ofInt(int value) {
		return new UShort((short) value);
	}

	public static UInts fromNbt(Tag nbt) {
		return new UShort(((ShortTag) nbt).getAsShort());
	}

	public static UInts fromBuf(FriendlyByteBuf buf) {
		return new UShort(buf.readShort());
	}

	@Override
	public int get() {
		return value & SHORT_MASK;
	}

	@Override
	public void writeNbt(CompoundTag nbt, String key) {
		nbt.putShort(key, value);
	}

	@Override
	public void writeBuf(FriendlyByteBuf buf) {
		buf.writeShort(value);
	}

	@Override
	public int getType() {
		return TYPE;
	}
}
