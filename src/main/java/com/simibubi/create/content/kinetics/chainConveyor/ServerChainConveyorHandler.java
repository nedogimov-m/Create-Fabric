package com.simibubi.create.content.kinetics.chainConveyor;

import com.simibubi.create.Create;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Only validated ride sessions can refresh the source twenty-tick hanging state. */
public final class ServerChainConveyorHandler {
    public record Heartbeat(int session,boolean stop) implements CustomPacketPayload{
        public static final Type<Heartbeat> TYPE=new Type<>(Create.asResource("chain_conveyor_riding"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Heartbeat> CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,Heartbeat::session,ByteBufCodecs.BOOL,Heartbeat::stop,Heartbeat::new);
        @Override public Type<Heartbeat> type(){return TYPE;}
    }
    public record Update(int session,boolean active,BlockPos anchor,BlockPos connection,float position,boolean reversed,int catchingUp) implements CustomPacketPayload{
        public static final Type<Update> TYPE=new Type<>(Create.asResource("chain_conveyor_ride_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Update> CODEC=StreamCodec.of((buf,value)->{buf.writeVarInt(value.session);buf.writeBoolean(value.active);buf.writeBlockPos(value.anchor);buf.writeBlockPos(value.connection);buf.writeFloat(value.position);buf.writeBoolean(value.reversed);buf.writeVarInt(value.catchingUp);},buf->new Update(buf.readVarInt(),buf.readBoolean(),buf.readBlockPos(),buf.readBlockPos(),buf.readFloat(),buf.readBoolean(),buf.readVarInt()));
        @Override public Type<Update> type(){return TYPE;}
    }
    public record Hanging(List<UUID> players) implements CustomPacketPayload{
        public static final Type<Hanging> TYPE=new Type<>(Create.asResource("hanging_players"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Hanging> CODEC=StreamCodec.composite(UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(65536)),Hanging::players,Hanging::new);
        @Override public Type<Hanging> type(){return TYPE;}
    }
    public static final class Ride {
        public final int id;
        public final ResourceKey<Level> dimension;
        public final ChainConveyorRidingState path;
        public int ttl=20;
        private Ride(int id,Player player,ChainConveyorRidingState path){this.id=id;this.dimension=player.level().dimension();this.path=path;}
    }
    private static final AtomicInteger IDS=new AtomicInteger();
    private static final Map<MinecraftServer,WeakHashMap<Player,Ride>> SERVERS=new WeakHashMap<>();
    private static Map<Player,Ride> rides(Player player){return SERVERS.computeIfAbsent(player.level().getServer(),ignored->new WeakHashMap<>());}
    public static Ride get(Player player){return rides(player).get(player);}
    public static boolean embark(Player player,ChainConveyorBlockEntity conveyor,ChainConveyorInteraction.Selection selected){
        if(player.level().isClientSide()||!ChainConveyorRidingState.eligible(player))return false;
        var path=new ChainConveyorRidingState(conveyor.getBlockPos(),selected.connection(),selected.position(),conveyor.isReversed());if(path.connection!=null)path.edgeId=conveyor.getConnectionId(path.connection);
        var ride=new Ride(IDS.incrementAndGet(),player,path);rides(player).put(player,ride);player.resetFallDistance();if(player instanceof ServerPlayer server)server.connection.resetFlyingTicks();send(player,ride,true);sync(player.level().getServer());return true;
    }
    public static boolean heartbeat(Player player,Heartbeat packet){var ride=get(player);if(ride==null||ride.id!=packet.session)return false;if(packet.stop){stop(player,true);return true;}if(!ChainConveyorRidingState.eligible(player)||player.level().dimension()!=ride.dimension){stop(player,true);return false;}ride.ttl=20;return true;}
    public static void tickPlayer(Player player){
        var ride=get(player);if(ride==null)return;
        if(--ride.ttl<=0||player.level().dimension()!=ride.dimension||!ChainConveyorRidingState.eligible(player)||!ride.path.advance(player.level(),player.getLookAngle())){stop(player,true);return;}
        var target=ride.path.target(player.level());if(target==null||!ride.path.inReach(player,target)){stop(player,true);return;}
        if(ride.path.catchingUp>0)ride.path.catchingUp--;player.resetFallDistance();if(player instanceof ServerPlayer server)server.connection.resetFlyingTicks();
    }
    public static void stop(Player player,boolean notify){var ride=rides(player).remove(player);if(ride==null)return;if(notify){player.resetFallDistance();send(player,ride,false);}sync(player.level().getServer());}
    private static void send(Player player,Ride ride,boolean active){if(player instanceof ServerPlayer server)ServerPlayNetworking.send(server,new Update(ride.id,active,ride.path.anchor,ride.path.connection==null?BlockPos.ZERO:ride.path.connection,ride.path.position,ride.path.reversed,ride.path.catchingUp));}
    public static void tick(MinecraftServer server){var map=SERVERS.get(server);if(map==null)return;for(var player:List.copyOf(map.keySet())){tickPlayer(player);var ride=map.get(player);if(ride!=null&&server.getTickCount()%10==0)send(player,ride,true);}if(server.getTickCount()%10==0)sync(server);}
    private static void sync(MinecraftServer server){var map=SERVERS.get(server);var uuids=map==null?List.<UUID>of():map.keySet().stream().map(Player::getUUID).toList();var packet=new Hanging(uuids);for(var player:server.getPlayerList().getPlayers())ServerPlayNetworking.send(player,packet);}
    public static void unload(MinecraftServer server){SERVERS.remove(server);}
    public static void register(){
        PayloadTypeRegistry.serverboundPlay().register(Heartbeat.TYPE,Heartbeat.CODEC);PayloadTypeRegistry.clientboundPlay().register(Update.TYPE,Update.CODEC);PayloadTypeRegistry.clientboundPlay().register(Hanging.TYPE,Hanging.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Heartbeat.TYPE,(packet,context)->heartbeat(context.player(),packet));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(ServerChainConveyorHandler::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(ServerChainConveyorHandler::unload);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->stop(handler.player,false));
    }
    private ServerChainConveyorHandler(){}
}
