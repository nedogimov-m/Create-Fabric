package com.simibubi.create.content.contraptions;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.foundation.damageTypes.CreateDamageSources;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Source local-player collision report; it can hurt only its real native sending player. */
public record TrainCollisionPacket(int damage, int contraptionEntityId, UUID contraptionUuid)
    implements CustomPacketPayload {
  public static final Type<TrainCollisionPacket> TYPE =
      new Type<>(Create.asResource("train_collision"));
  public static final StreamCodec<RegistryFriendlyByteBuf, TrainCollisionPacket> CODEC =
      CustomPacketPayload.codec((packet, buffer) -> {
        buffer.writeInt(packet.damage); buffer.writeVarInt(packet.contraptionEntityId);
        buffer.writeUUID(packet.contraptionUuid);
      }, buffer -> new TrainCollisionPacket(buffer.readInt(), buffer.readVarInt(), buffer.readUUID()));
  @Override public Type<TrainCollisionPacket> type() { return TYPE; }
  public void handle(ServerPlayer player) {
    var level = player.level();
    if (damage <= 0 || !(level.getEntity(contraptionEntityId) instanceof CarriageContraptionEntity owner)
        || !owner.getUUID().equals(contraptionUuid)) return;
    player.hurtServer(level, CreateDamageSources.runOver(level, owner), damage);
    level.playSound(player, owner.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
        SoundSource.NEUTRAL, 1, .75f);
  }
}
