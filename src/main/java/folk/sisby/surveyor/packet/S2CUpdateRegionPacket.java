package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.terrain.ChunkSummary;
import folk.sisby.surveyor.terrain.RegionSummary;
import folk.sisby.surveyor.util.BitSetUtil;
import folk.sisby.surveyor.util.ListUtil;
import folk.sisby.surveyor.util.RegionPos;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.Level;

public record S2CUpdateRegionPacket(ResourceKey<Level> dimension, boolean shared, RegionPos regionPos, List<Integer> biomePalette, List<Integer> blockPalette, BitSet set, List<ChunkSummary> chunks) implements S2CPacket, ShareFlagged<S2CUpdateRegionPacket> {
	public static final CustomPacketPayload.Type<S2CUpdateRegionPacket> ID = new CustomPacketPayload.Type<>(Surveyor.id("s2c_update_region"));
	public static final StreamCodec<FriendlyByteBuf, S2CUpdateRegionPacket> CODEC = StreamCodec.composite(
		StreamCodec.composite(
			ResourceKey.streamCodec(Registries.DIMENSION), Tuple<ResourceKey<Level>, Boolean>::getA,
			ByteBufCodecs.BOOL, Tuple<ResourceKey<Level>, Boolean>::getB,
			Tuple::new
		), p -> new Tuple<>(p.dimension(), p.shared()),
		RegionPos.PACKET_CODEC, S2CUpdateRegionPacket::regionPos,
		ByteBufCodecs.INT.apply(ByteBufCodecs.list()), S2CUpdateRegionPacket::biomePalette,
		ByteBufCodecs.INT.apply(ByteBufCodecs.list()), S2CUpdateRegionPacket::blockPalette,
		ByteBufCodecs.fromCodec(ExtraCodecs.BIT_SET), S2CUpdateRegionPacket::set,
		StreamCodec.ofMember(ChunkSummary::writeBuf, ChunkSummary::new).apply(ByteBufCodecs.list()), S2CUpdateRegionPacket::chunks,
		(pair, rp, bi, bl, s, c) -> new S2CUpdateRegionPacket(pair.getA(), pair.getB(), rp, bi, bl, s, c)
	);

	public static S2CUpdateRegionPacket of(ResourceKey<Level> dimension, boolean shared, RegionPos regionPos, RegionSummary summary, BitSet keys) {
		return summary.createUpdatePacket(dimension, shared, regionPos, keys);
	}

	@Override
	public S2CUpdateRegionPacket withShared(boolean shared) {
		return new S2CUpdateRegionPacket(dimension, shared, regionPos, biomePalette, blockPalette, set, chunks);
	}

	@Override
	public List<SurveyorPacket> toPayloads() {
		List<SurveyorPacket> payloads = new ArrayList<>();
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		CODEC.encode(buf, this);
		if (buf.readableBytes() < MAX_PAYLOAD_SIZE) {
			payloads.add(this);
		} else {
			if (set.cardinality() == 1) {
				int bit = set.stream().findFirst().orElseThrow();
				Surveyor.LOGGER.error("Couldn't create a terrain update packet at {} - an individual chunk would be too large to send!", "[%d,%d]".formatted(regionPos.toChunk(bit).x, regionPos.toChunk(bit).z));
				return List.of();
			}
			for (BitSet splitChunks : BitSetUtil.half(set)) {
				payloads.addAll(new S2CUpdateRegionPacket(dimension, shared, regionPos, biomePalette, blockPalette, splitChunks, ListUtil.splitSet(chunks, splitChunks, set)).toPayloads());
			}
		}
		return payloads;
	}

	@Override
	public CustomPacketPayload.Type<S2CUpdateRegionPacket> type() {
		return ID;
	}
}
