package folk.sisby.surveyor.packet;

import com.google.common.collect.Table;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.RegionPos;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.BitSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

public record S2CGroupChangedPacket(Map<UUID, PlayerSummary> players, Table<ResourceKey<Level>, RegionPos, BitSet> chunks, Table<ResourceKey<Level>, ResourceKey<Structure>, LongSet> starts) implements S2CPacket {
	public static final CustomPacketPayload.Type<S2CGroupChangedPacket> ID = new CustomPacketPayload.Type<>(Surveyor.id("s2c_group_changed"));
	public static final StreamCodec<RegistryFriendlyByteBuf, S2CGroupChangedPacket> CODEC = StreamCodec.composite(
		SurveyorPacketCodecs.GROUP_SUMMARIES, S2CGroupChangedPacket::players,
		SurveyorPacketCodecs.TERRAIN_KEYS, S2CGroupChangedPacket::chunks,
		SurveyorPacketCodecs.STRUCTURE_KEYS_LONG_SET, S2CGroupChangedPacket::starts,
		S2CGroupChangedPacket::new
	);

	@Override
	public CustomPacketPayload.Type<S2CGroupChangedPacket> type() {
		return ID;
	}
}
