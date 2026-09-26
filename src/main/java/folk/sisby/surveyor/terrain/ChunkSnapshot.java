package folk.sisby.surveyor.terrain;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import org.jetbrains.annotations.Nullable;

/**
 * Copies of a chunk's block and biome containers, taken on the level's thread so the chunk can be summarised on a
 * worker (sisby-folk/surveyor#148). Also the {@link BlockGetter} the scan hands to {@code getMapColor}: no block
 * entities, which is why chunks with blocks that override the level-aware map colour aren't snapshotted.
 */
final class ChunkSnapshot implements BlockGetter {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	/**
	 * Blocks whose map colour may read the level: vanilla never overrides it, modded blocks might.
	 */
	private static final ClassValue<Boolean> READS_LEVEL = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			try {
				return type.getMethod("getMapColor", BlockState.class, BlockGetter.class, BlockPos.class, MapColor.class).getDeclaringClass() != IBlockExtension.class;
			} catch (NoSuchMethodException e) {
				return true;
			}
		}
	};
	// A global palette can't be listed, so maybeHas answers true for it: those chunks are summarised in place.
	private static final Predicate<BlockState> MAY_READ_LEVEL = state -> READS_LEVEL.get(state.getBlock().getClass());

	static boolean readsLevel(Class<?> blockClass) {
		return READS_LEVEL.get(blockClass);
	}

	final ChunkPos pos;
	final int minSection;
	final int minBuildHeight;
	final int maxBuildHeight;
	final int[] layerHeights;
	final @Nullable Integer airCount;
	private final PalettedContainer<BlockState>[] states;
	private final PalettedContainer<Holder<Biome>>[] biomes;
	private final boolean[] onlyAir;

	@SuppressWarnings("unchecked")
	private ChunkSnapshot(ChunkPos pos, int minSection, int minBuildHeight, int maxBuildHeight, int[] layerHeights, @Nullable Integer airCount, int sections) {
		this.pos = pos;
		this.minSection = minSection;
		this.minBuildHeight = minBuildHeight;
		this.maxBuildHeight = maxBuildHeight;
		this.layerHeights = layerHeights;
		this.airCount = airCount;
		this.states = new PalettedContainer[sections];
		this.biomes = new PalettedContainer[sections];
		this.onlyAir = new boolean[sections];
	}

	/**
	 * @return the snapshot, or null when a block's map colour could depend on the level: summarise in place then.
	 */
	@SuppressWarnings("unchecked")
	static @Nullable ChunkSnapshot of(LevelChunkSection[] sections, ChunkPos pos, int minSection, int minBuildHeight, int maxBuildHeight, int[] layerHeights, @Nullable Integer airCount) {
		for (LevelChunkSection section : sections) {
			if (!section.hasOnlyAir() && section.getStates().maybeHas(MAY_READ_LEVEL)) return null;
		}
		ChunkSnapshot snapshot = new ChunkSnapshot(pos, minSection, minBuildHeight, maxBuildHeight, layerHeights, airCount, sections.length);
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			snapshot.onlyAir[i] = section.hasOnlyAir();
			snapshot.states[i] = section.getStates().copy();
			snapshot.biomes[i] = ((PalettedContainer<Holder<Biome>>) section.getBiomes()).copy();
		}
		return snapshot;
	}

	SectionSummary[] sections() {
		SectionSummary[] sections = new SectionSummary[states.length];
		for (int i = 0; i < states.length; i++) {
			sections[i] = onlyAir[i] ? null : SectionSummary.of(states[i], biomes[i]);
		}
		return sections;
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		int i = (pos.getY() >> 4) - minSection;
		return i < 0 || i >= states.length ? AIR : states[i].get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return getBlockState(pos).getFluidState();
	}

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
		return null;
	}

	@Override
	public int getHeight() {
		return maxBuildHeight - minBuildHeight;
	}

	@Override
	public int getMinBuildHeight() {
		return minBuildHeight;
	}
}
