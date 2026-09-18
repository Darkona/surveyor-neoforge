package folk.sisby.surveyor.util;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionPosTest {
	private static final int[] COORDS = {0, 1, 31, 32, 33, -1, -31, -32, -33, 1_000_000, -1_000_000, 29_999_999 >> 4, -(29_999_999 >> 4)};

	@Test
	@DisplayName("Chunk → region → bit → chunk is the identity, negative coordinates included")
	void chunkBitRoundTrip() {
		for (int x : COORDS) for (int z : COORDS) {
			ChunkPos chunk = new ChunkPos(x, z);
			RegionPos region = RegionPos.of(chunk);
			int bit = RegionPos.chunkToBit(chunk);
			assertTrue(bit >= 0 && bit < RegionPos.CHUNK_AREA, chunk::toString);
			assertEquals(chunk, region.toChunk(bit), chunk::toString);
		}
	}

	@Test
	@DisplayName("A block and the chunk holding it land in the same region")
	void blockAndChunkAgree() {
		for (int x : COORDS) for (int z : COORDS) {
			BlockPos block = new BlockPos(x * 16 + 7, 64, z * 16 + 3);
			assertEquals(RegionPos.of(new ChunkPos(block)), RegionPos.of(block), block::toString);
		}
	}

	@Test
	@DisplayName("The long, string and packet encodings round-trip")
	void encodingsRoundTrip() {
		for (int x : COORDS) for (int z : COORDS) {
			RegionPos pos = new RegionPos(x, z);
			assertEquals(pos, RegionPos.of(pos.toLong()));
			assertEquals(pos, RegionPos.of(pos.toString()));
			assertEquals(pos, RegionPos.CODEC.parse(JsonOps.INSTANCE, RegionPos.CODEC.encodeStart(JsonOps.INSTANCE, pos).getOrThrow()).getOrThrow());
			FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
			RegionPos.PACKET_CODEC.encode(buf, pos);
			assertEquals(pos, RegionPos.PACKET_CODEC.decode(buf));
		}
	}

	@Test
	@DisplayName("toChunks() covers every chunk of the region, once")
	void toChunksCoversRegion() {
		RegionPos region = new RegionPos(-2, 3);
		Set<ChunkPos> chunks = region.toChunks();
		assertEquals(RegionPos.CHUNK_AREA, chunks.size());
		for (ChunkPos chunk : chunks) assertEquals(region, RegionPos.of(chunk), chunk::toString);
		assertTrue(chunks.contains(new ChunkPos(region.chunkX() + RegionPos.CHUNK_SIZE - 1, region.chunkZ() + RegionPos.CHUNK_SIZE - 1)), "the far corner is part of the region");
	}

	@Test
	@DisplayName("chunksToRegions and regionsToChunks are inverses")
	void chunkSetsRoundTrip() {
		List<ChunkPos> chunks = new ArrayList<>();
		for (int x = -40; x < 40; x += 3) for (int z = -40; z < 40; z += 7) chunks.add(new ChunkPos(x, z));
		Map<RegionPos, BitSet> regions = RegionPos.chunksToRegions(chunks);
		assertEquals(Set.copyOf(chunks), RegionPos.regionsToChunks(regions));
		assertEquals(chunks.size(), regions.values().stream().mapToInt(BitSet::cardinality).sum());
	}
}
