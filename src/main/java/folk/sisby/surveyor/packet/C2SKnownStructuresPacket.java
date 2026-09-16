package folk.sisby.surveyor.packet;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

public record C2SKnownStructuresPacket(Map<ResourceKey<Level>, Multimap<ResourceKey<Structure>, ChunkPos>> starts) implements C2SPacket {
	public static final CustomPacketPayload.Type<C2SKnownStructuresPacket> ID = new CustomPacketPayload.Type<>(Surveyor.id("c2s_known_structures"));
	public static final StreamCodec<FriendlyByteBuf, C2SKnownStructuresPacket> CODEC = SurveyorPacketCodecs.STRUCTURE_KEYS.map(C2SKnownStructuresPacket::new, C2SKnownStructuresPacket::starts);

	@Override
	public CustomPacketPayload.Type<C2SKnownStructuresPacket> type() {
		return ID;
	}
}
