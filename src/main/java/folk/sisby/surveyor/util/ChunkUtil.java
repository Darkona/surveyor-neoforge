package folk.sisby.surveyor.util;

import folk.sisby.surveyor.Surveyor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ChunkUtil {
	public static Integer airCount(ChunkAccess chunk) {
		int air = 0;
		for (LevelChunkSection section : chunk.getSections()) air += 4096 - section.nonEmptyBlockCount;
		return air;
	}

	public static Map<RegionPos, File> getRegionFiles(File folder, String prefix) {
		Map<RegionPos, File> files = new HashMap<>();
		for (File file : Objects.requireNonNullElse(folder.listFiles(), new File[0])) {
				String[] split = file.getName().split("\\.");
				if (split.length == 4 && split[0].equals(prefix) && split[3].equals("dat")) {
					try {
						files.put(new RegionPos(Integer.parseInt(split[1]), Integer.parseInt(split[2])), file);
					} catch (NumberFormatException ignored) {
					}
				}
			}
		return files;
	}

	public static Map<RegionPos, CompoundTag> getRegionNbt(File folder, String prefix) {
		Map<RegionPos, File> regionFiles = getRegionFiles(folder, prefix);
		Map<RegionPos, CompoundTag> regions = new HashMap<>();
		for (RegionPos regionPos : regionFiles.keySet()) {
			CompoundTag regionCompound = null;
			try {
				regionCompound = NbtIo.readCompressed(regionFiles.get(regionPos).toPath(), NbtAccounter.unlimitedHeap());
			} catch (IOException | ReportedNbtException e) {
				Surveyor.LOGGER.error("[Surveyor] Error loading region nbt file {}.", regionFiles.get(regionPos).getName(), e);
			}
			if (regionCompound != null) regions.put(regionPos, regionCompound);
		}
		return regions;
	}
}
