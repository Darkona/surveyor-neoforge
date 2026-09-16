package folk.sisby.surveyor.util.uints;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

public record UInt(int value) implements SingleUInts {
	public static final byte TYPE = Tag.TAG_INT;

	public static UInts ofInt(int value) {
		return new UInt(value);
	}

	public static UInts fromNbt(Tag nbt) {
		return new UInt(((IntTag) nbt).getAsInt());
	}

	public static UInts fromBuf(FriendlyByteBuf buf) {
		return new UInt(buf.readVarInt());
	}

	@Override
	public int get() {
		return value;
	}

	@Override
	public void writeNbt(CompoundTag nbt, String key) {
		nbt.putInt(key, value);
	}

	@Override
	public void writeBuf(FriendlyByteBuf buf) {
		buf.writeVarInt(value);
	}

	@Override
	public int getType() {
		return TYPE;
	}
}
