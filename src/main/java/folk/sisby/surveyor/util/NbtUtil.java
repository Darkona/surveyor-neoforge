package folk.sisby.surveyor.util;

import java.util.Collection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public class NbtUtil {
	public static void removeRecursive(CompoundTag nbt, Collection<String> keys) {
		keys.forEach(nbt::remove);
		for (String key : nbt.getAllKeys()) {
			if (nbt.contains(key, Tag.TAG_COMPOUND)) {
				removeRecursive(nbt.getCompound(key), keys);
			} else if (nbt.contains(key, Tag.TAG_LIST)) {
				for (Tag listNbt : nbt.getList(key, Tag.TAG_COMPOUND)) {
					removeRecursive((CompoundTag) listNbt, keys);
				}
			}
		}
	}
}
