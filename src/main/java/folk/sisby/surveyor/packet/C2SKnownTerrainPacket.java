package folk.sisby.surveyor.packet;

import com.google.common.collect.Table;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.util.RegionPos;
import java.util.BitSet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record C2SKnownTerrainPacket(Table<ResourceKey<Level>, RegionPos, BitSet> chunks) implements C2SPacket {
	public static final CustomPacketPayload.Type<C2SKnownTerrainPacket> ID = new CustomPacketPayload.Type<>(Surveyor.id("known_terrain"));
	public static final StreamCodec<FriendlyByteBuf, C2SKnownTerrainPacket> CODEC = SurveyorPacketCodecs.TERRAIN_KEYS.map(C2SKnownTerrainPacket::new, C2SKnownTerrainPacket::chunks);

	@Override
	public CustomPacketPayload.Type<C2SKnownTerrainPacket> type() {
		return ID;
	}
}
