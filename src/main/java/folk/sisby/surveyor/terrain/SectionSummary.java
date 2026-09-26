package folk.sisby.surveyor.terrain;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;

public record SectionSummary(Palette<BlockState> blockPalette, int[] blockIndices, Palette<Holder<Biome>> biomePalette, int[] biomeIndices) {
	public static SectionSummary ofSection(LevelChunkSection section) {
		if (section.hasOnlyAir()) {
			return null;
		} else {
			return of(section.getStates(), (PalettedContainer<Holder<Biome>>) section.getBiomes());
		}
	}

	/**
	 * Also used on copies of a section's containers, off the level's thread (sisby-folk/surveyor#148).
	 */
	public static SectionSummary of(PalettedContainer<BlockState> states, PalettedContainer<Holder<Biome>> biomes) {
		int[] blockIndices = new int[PalettedContainer.Strategy.SECTION_STATES.size()];
		states.data.storage().unpack(blockIndices);
		int[] biomeIndices = new int[PalettedContainer.Strategy.SECTION_BIOMES.size()];
		biomes.data.storage().unpack(biomeIndices);
		return new SectionSummary(states.data.palette(), blockIndices, biomes.data.palette(), biomeIndices);
	}

	public BlockState getBlockState(int relativeX, int y, int relativeZ) {
		return blockPalette().valueFor(blockIndices()[PalettedContainer.Strategy.SECTION_STATES.getIndex(relativeX, y & 15, relativeZ)]);
	}

	public Holder<Biome> getBiomeEntry(int relativeX, int y, int relativeZ, int bottomY, int topY) {
		return biomePalette().valueFor(biomeIndices()[PalettedContainer.Strategy.SECTION_BIOMES.getIndex(QuartPos.fromBlock(relativeX) & 3, Mth.clamp(QuartPos.fromBlock(y), QuartPos.fromBlock(bottomY), QuartPos.fromBlock(topY) - 1) & 3, QuartPos.fromBlock(relativeZ) & 3)]);
	}
}
