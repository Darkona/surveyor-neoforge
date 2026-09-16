package folk.sisby.surveyor.packet;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.structure.StructureStartSummary;
import folk.sisby.surveyor.structure.WorldStructures;
import folk.sisby.surveyor.util.MapUtil;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

public record S2CStructuresAddedPacket(ResourceKey<Level> dimension, boolean shared, Table<ResourceKey<Structure>, ChunkPos, StructureStartSummary> starts, Map<ResourceKey<Structure>, ResourceKey<StructureType<?>>> types, Multimap<ResourceKey<Structure>, TagKey<Structure>> tags) implements S2CPacket, ShareFlagged<S2CStructuresAddedPacket> {
	public static final CustomPacketPayload.Type<S2CStructuresAddedPacket> ID = new CustomPacketPayload.Type<>(Surveyor.id("s2c_structures_added"));
	public static final StreamCodec<FriendlyByteBuf, S2CStructuresAddedPacket> CODEC = StreamCodec.composite(
		ResourceKey.streamCodec(Registries.DIMENSION), S2CStructuresAddedPacket::dimension,
		ByteBufCodecs.BOOL, S2CStructuresAddedPacket::shared,
		SurveyorPacketCodecs.STRUCTURE_SUMMARIES, S2CStructuresAddedPacket::starts,
		SurveyorPacketCodecs.STRUCTURE_TYPES, S2CStructuresAddedPacket::types,
		SurveyorPacketCodecs.STRUCTURE_TAGS, S2CStructuresAddedPacket::tags,
		S2CStructuresAddedPacket::new
	);

	public static S2CStructuresAddedPacket of(boolean shared, Multimap<ResourceKey<Structure>, ChunkPos> starts, WorldStructures structures) {
		return structures.createUpdatePacket(shared, starts);
	}

	public static S2CStructuresAddedPacket of(boolean shared, ResourceKey<Structure> key, ChunkPos pos, WorldStructures structures) {
		return of(shared, MapUtil.asMultiMap(Map.of(key, List.of(pos))), structures);
	}

	@Override
	public S2CStructuresAddedPacket withShared(boolean shared) {
		return new S2CStructuresAddedPacket(dimension, shared, starts, types, tags);
	}

	@Override
	public List<SurveyorPacket> toPayloads() {
		List<SurveyorPacket> payloads = new ArrayList<>();
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		CODEC.encode(buf, this);
		if (buf.readableBytes() < MAX_PAYLOAD_SIZE) {
			payloads.add(this);
		} else {
			Multimap<ResourceKey<Structure>, ChunkPos> keySet = MapUtil.keyMultiMap(starts);
			if (keySet.size() == 1) {
				Surveyor.LOGGER.error("Couldn't create a structure update packet for {} at {} - an individual structure would be too large to send!", keySet.keys().stream().findFirst().orElseThrow().location(), keySet.values().stream().findFirst().orElseThrow());
				return List.of();
			}
			Multimap<ResourceKey<Structure>, ChunkPos> firstHalf = HashMultimap.create();
			Multimap<ResourceKey<Structure>, ChunkPos> secondHalf = HashMultimap.create();
			keySet.forEach((key, pos) -> {
				if (firstHalf.size() < keySet.size() / 2) {
					firstHalf.put(key, pos);
				} else {
					secondHalf.put(key, pos);
				}
			});
			payloads.addAll(new S2CStructuresAddedPacket(dimension, shared, MapUtil.splitByKeyMap(starts, firstHalf), MapUtil.splitByKeySet(types, firstHalf.keySet()), MapUtil.asMultiMap(MapUtil.splitByKeySet(tags.asMap(), firstHalf.keySet()))).toPayloads());
			payloads.addAll(new S2CStructuresAddedPacket(dimension, shared, MapUtil.splitByKeyMap(starts, secondHalf), MapUtil.splitByKeySet(types, secondHalf.keySet()), MapUtil.asMultiMap(MapUtil.splitByKeySet(tags.asMap(), secondHalf.keySet()))).toPayloads());
		}
		return payloads;
	}

	@Override
	public CustomPacketPayload.Type<S2CStructuresAddedPacket> type() {
		return ID;
	}
}
