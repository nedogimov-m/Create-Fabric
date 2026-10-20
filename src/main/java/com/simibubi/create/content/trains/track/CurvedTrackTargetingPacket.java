package com.simibubi.create.content.trains.track;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/** Actual current server ray/reciprocal primary curve selection; client segment is only a hint. */
public record CurvedTrackTargetingPacket(
    BlockPos owner, BlockPos target, int segment, InteractionHand hand)
    implements CustomPacketPayload {
  public static final Type<CurvedTrackTargetingPacket> TYPE =
      new Type<>(Create.asResource("select_targeted_curve"));
  public static final StreamCodec<RegistryFriendlyByteBuf, CurvedTrackTargetingPacket> CODEC =
      CustomPacketPayload.codec(
          (packet, buf) -> {
            buf.writeBlockPos(packet.owner);
            buf.writeBlockPos(packet.target);
            buf.writeVarInt(packet.segment);
            buf.writeEnum(packet.hand);
          },
          buf ->
              new CurvedTrackTargetingPacket(
                  buf.readBlockPos(),
                  buf.readBlockPos(),
                  buf.readVarInt(),
                  buf.readEnum(InteractionHand.class)));

  public CurvedTrackTargetingPacket {
    owner = owner.immutable();
    target = target.immutable();
    if (segment < 0 || segment >= 65536)
      throw new IllegalArgumentException("Invalid selected curve segment");
  }

  @Override
  public Type<CurvedTrackTargetingPacket> type() {
    return TYPE;
  }

  public static boolean apply(Player player, CurvedTrackTargetingPacket packet) {
    if (!(player.level() instanceof ServerLevel level)
        || !player.isAlive()
        || player.isSpectator()
        || !player.mayBuild()
        || !level.mayInteract(player, packet.owner)
        || !level.mayInteract(player, packet.target)
        || !LoadedLevelAccess.isLoaded(level, packet.owner)
        || !LoadedLevelAccess.isLoaded(level, packet.target)
        || !(player.getItemInHand(packet.hand).getItem() instanceof TrackTargetingBlockItem item))
      return false;
    if (!(LoadedLevelAccess.blockEntity(level, packet.owner) instanceof TrackBlockEntity first)
        || !(LoadedLevelAccess.blockEntity(level, packet.target)
            instanceof TrackBlockEntity second)) return false;
    var curve = first.getConnections().get(packet.target);
    var reverse = second.getConnections().get(packet.owner);
    if (curve == null
        || !curve.isPrimary()
        || reverse == null
        || reverse.isPrimary()
        || curve.getMaterial() != reverse.getMaterial()
        || !curve.equalsSansMaterial(reverse)) return false;
    var from = player.getEyePosition();
    var to = from.add(player.getLookAngle().scale(player.blockInteractionRange() + 1));
    var hit = TrackCurveSelection.trace(curve, from, to);
    if (hit == null || Math.abs(hit.segment() - packet.segment) > 2) return false;
    try {
      if (!TrackCurveSelection.visible(
          from, hit, TrackCurveSelection.obstacle(level, from, to, player))) return false;
    } catch (UnloadedTrackException missing) {
      return false;
    }
    var selected =
        new TrackTargetingSelection(
            GlobalPos.of(level.dimension(), packet.owner),
            player.getLookAngle().dot(hit.direction()) < 0,
            hit.direction(),
            Optional.of(new BezierTrackPointLocation(packet.target, hit.segment())));
    return item.select(player, packet.hand, selected);
  }

  public static void register() {
    PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
    ServerPlayNetworking.registerGlobalReceiver(
        TYPE, (packet, context) -> apply(context.player(), packet));
  }
}
