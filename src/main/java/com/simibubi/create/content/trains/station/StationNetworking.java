package com.simibubi.create.content.trains.station;

import com.simibubi.create.Create;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.content.trains.RailwayGraphManager;
import com.simibubi.create.content.trains.track.CurvedTrackTargetingPacket;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/** Genuine native station state/open/configuration protocol; server owns every mutation. */
public final class StationNetworking {
  public record State(
      GlobalPos owner,
      UUID point,
      long revision,
      String name,
      boolean assembling,
      DoorControl doors,
      StationAssemblyPreflight.Status preflight,
      boolean accepted,
      int length,
      Optional<Direction> direction,
      List<Integer> bogeys,
      StationTrainAssembly.Status assemblyStatus,
      Optional<UUID> presentTrain,
      boolean canDisassemble,
      Optional<net.minecraft.network.chat.Component> assemblyFailure)
      implements CustomPacketPayload {
    public State(GlobalPos owner,UUID point,long revision,String name,boolean assembling,DoorControl doors,
        StationAssemblyPreflight.Status preflight,boolean accepted,int length,Optional<Direction> direction,List<Integer> bogeys){
      this(owner,point,revision,name,assembling,doors,preflight,accepted,length,direction,bogeys,StationTrainAssembly.Status.NOT_CHECKED,Optional.empty(),false,Optional.empty());
    }
    public static final Type<State> TYPE = new Type<>(Create.asResource("station_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC =
        CustomPacketPayload.codec(
            (packet, buf) -> {
              buf.writeIdentifier(packet.owner.dimension().identifier());
              buf.writeBlockPos(packet.owner.pos());
              buf.writeUUID(packet.point);
              buf.writeVarLong(packet.revision);
              buf.writeUtf(packet.name, 256);
              buf.writeBoolean(packet.assembling);
              buf.writeVarInt(packet.doors.ordinal());
              buf.writeEnum(packet.preflight);
              buf.writeBoolean(packet.accepted);
              buf.writeVarInt(packet.length);
              buf.writeBoolean(packet.direction.isPresent());
              packet.direction.ifPresent(buf::writeEnum);
              buf.writeVarInt(packet.bogeys.size());
              for (var offset : packet.bogeys) buf.writeVarInt(offset);
              buf.writeEnum(packet.assemblyStatus);buf.writeBoolean(packet.presentTrain.isPresent());packet.presentTrain.ifPresent(buf::writeUUID);
              buf.writeBoolean(packet.canDisassemble);buf.writeBoolean(packet.assemblyFailure.isPresent());packet.assemblyFailure.ifPresent(component->net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buf,component));
            },
            buf -> {
              var owner =
                  GlobalPos.of(
                      ResourceKey.create(Registries.DIMENSION, buf.readIdentifier()),
                      buf.readBlockPos());
              var point = buf.readUUID();
              long revision = buf.readVarLong();
              String name = buf.readUtf(256);
              boolean assembling = buf.readBoolean();
              var doors = DoorControl.byId(buf.readVarInt());
              var status = buf.readEnum(StationAssemblyPreflight.Status.class);
              boolean accepted = buf.readBoolean();
              int length = buf.readVarInt();
              var direction =
                  buf.readBoolean()
                      ? Optional.of(buf.readEnum(Direction.class))
                      : Optional.<Direction>empty();
              int count = buf.readVarInt();
              if (count < 0 || count > StationAssemblyArea.MAX_LENGTH)
                throw new IllegalArgumentException("Station bogey snapshot exceeds source strip");
              var offsets = new ArrayList<Integer>();
              for (int i = 0; i < count; i++) offsets.add(buf.readVarInt());
              var assemblyStatus=buf.readEnum(StationTrainAssembly.Status.class);
              var train=buf.readBoolean()?Optional.of(buf.readUUID()):Optional.<UUID>empty();
              boolean canDisassemble=buf.readBoolean();
              var failure=buf.readBoolean()?Optional.of(net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buf)):Optional.<net.minecraft.network.chat.Component>empty();
              return new State(
                  owner,
                  point,
                  revision,
                  name,
                  assembling,
                  doors,
                  status,
                  accepted,
                  length,
                  direction,
                  offsets,assemblyStatus,train,canDisassemble,failure);
            });

    public State {
      owner = GlobalPos.of(owner.dimension(), owner.pos().immutable());
      Objects.requireNonNull(point);
      Objects.requireNonNull(name);
      Objects.requireNonNull(doors);
      Objects.requireNonNull(preflight);
      Objects.requireNonNull(direction);
      bogeys = List.copyOf(bogeys);
      Objects.requireNonNull(assemblyStatus);Objects.requireNonNull(presentTrain);Objects.requireNonNull(assemblyFailure);
      if (revision < 0
          || name.length() > 256
          || length < 0
          || length >= StationAssemblyArea.MAX_LENGTH
          || bogeys.size() > StationAssemblyArea.MAX_LENGTH
          || bogeys.stream().anyMatch(i -> i < 0 || i >= StationAssemblyArea.MAX_LENGTH))
        throw new IllegalArgumentException("Invalid actual station snapshot");
    }

    @Override
    public Type<State> type() {
      return TYPE;
    }
  }

  private static boolean ownsPoint(StationBlockEntity station) {
    var point = station.getStation();
    return point != null
        && point.ownedBy(station)
        && point.graph() != null
        && point.graph().manager() == RailwayGraphManager.get(station.getLevel())
        && point.graph().getPoint(StationEdgePoints.STATION, point.id) == point;
  }

  public static State snapshot(StationBlockEntity station, boolean accepted) {
    var present=station.getStation()==null?null:station.getStation().getPresentTrain();
    return new State(
        GlobalPos.of(station.getLevel().dimension(), station.getBlockPos()),
        station.targeting().id(),
        station.configurationRevision(),
        station.stationName(),
        station.isAssembling(),
        station.doorControl(),
        station.lastStatus(),
        accepted,
        station.publishedAreaLength(),
        Optional.ofNullable(station.publishedDirection()),
        station.publishedBogeys(),station.trainAssemblyStatus(),Optional.ofNullable(present).map(train->train.id),
        present!=null&&present.canDisassemble(),Optional.ofNullable(station.trainAssemblyFailure()).map(failure->failure.component));
  }

  public static void open(ServerPlayer player, StationBlockEntity station) {
    if (StationPermissions.canEdit(player, station, true) && ownsPoint(station))
      ServerPlayNetworking.send(player, snapshot(station, true));
  }

  public static boolean apply(ServerPlayer player, StationEditPacket packet) {
    var level = player.level();
    if (!LoadedLevelAccess.isLoaded(level, packet.pos())
        || !(LoadedLevelAccess.blockEntity(level, packet.pos())
            instanceof StationBlockEntity station)
        || !station.targeting().id().equals(packet.point())
        || !StationPermissions.canEdit(player, station, true)
        || !ownsPoint(station)) return false;
    if(packet.action()==StationEditPacket.Action.REFRESH){ServerPlayNetworking.send(player,snapshot(station,true));return true;}
    if (station.configurationRevision() != packet.expectedRevision()) {
      ServerPlayNetworking.send(player, snapshot(station, false));
      return false;
    }
    boolean accepted;
    if (packet.action() == StationEditPacket.Action.PREFLIGHT) {
      station.preflight(player);
      accepted = true;
    }else if(packet.action()==StationEditPacket.Action.ASSEMBLE)accepted=station.tryAssemble(player);
    else if(packet.action()==StationEditPacket.Action.DISASSEMBLE)accepted=station.tryDisassemble(player);
    else accepted=station.configure(player,packet.name(),packet.assemblyMode(),packet.doors());
    ServerPlayNetworking.send(player, snapshot(station, accepted));
    return accepted;
  }

  public static void register() {
    StationEdgePoints.register();
    PayloadTypeRegistry.serverboundPlay().register(StationEditPacket.TYPE, StationEditPacket.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
    ServerPlayNetworking.registerGlobalReceiver(
        StationEditPacket.TYPE, (packet, context) -> apply(context.player(), packet));
    CurvedTrackTargetingPacket.register();
    StationAssemblyAreas.register();
  }

  private StationNetworking() {}
}
