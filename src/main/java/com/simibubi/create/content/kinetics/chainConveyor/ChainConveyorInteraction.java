package com.simibubi.create.content.kinetics.chainConveyor;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Shared picking and server-authoritative parcel attachment, pickup and chain removal. */
public final class ChainConveyorInteraction {
    public static final int ATTACH=0, TAKE=1, DISCONNECT=2, SELECT_PORT=3, RIDE=4;
    public record Selection(BlockPos connection,float position,Vec3 hit) {}
    public record Packet(BlockPos pos,BlockPos connection,float position,int action,int netId) implements CustomPacketPayload {
        public static final Type<Packet> TYPE=new Type<>(Create.asResource("chain_package_interaction"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Packet> CODEC=StreamCodec.of((buf,packet)->{
            buf.writeBlockPos(packet.pos);buf.writeBlockPos(packet.connection);buf.writeFloat(packet.position);buf.writeVarInt(packet.action);buf.writeVarInt(packet.netId);
        },buf->new Packet(buf.readBlockPos(),buf.readBlockPos(),buf.readFloat(),buf.readVarInt(),buf.readVarInt()));
        @Override public Type<Packet> type(){return TYPE;}
    }
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Packet.TYPE,Packet.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Packet.TYPE,(packet,context)->apply(context.player(),packet));
    }
    public static Selection trace(ChainConveyorBlockEntity conveyor,Vec3 from,Vec3 to) {
        var center=Vec3.atBottomCenterOf(conveyor.getBlockPos());
        var loop=new AABB(center.add(-1,0,-1),center.add(1,.5,1)).clip(from,to).orElse(null);
        Selection best=null;double nearest=Double.POSITIVE_INFINITY;
        if(loop!=null){var diff=loop.subtract(center);float angle=ChainConveyorBlockEntity.wrapAngle((float)Math.toDegrees(Math.atan2(diff.x,diff.z)));best=new Selection(BlockPos.ZERO,Math.round(angle/45)*45f,loop);nearest=from.distanceToSqr(loop);}
        for(var offset:conveyor.getConnections()){
            var stats=conveyor.getConnectionStats(offset);var direction=stats.end().subtract(stats.start()).normalize();
            var right=new Vec3(direction.z,0,-direction.x).normalize();var up=direction.cross(right).normalize();
            var relativeFrom=from.subtract(stats.start());var relativeTo=to.subtract(stats.start());
            var a=new Vec3(relativeFrom.dot(right),relativeFrom.dot(up),relativeFrom.dot(direction));
            var b=new Vec3(relativeTo.dot(right),relativeTo.dot(up),relativeTo.dot(direction));
            var hit=new AABB(-.175,-.175,0,.175,.175,stats.chainLength()).clip(a,b).orElse(null);if(hit==null)continue;
            var world=stats.start().add(right.scale(hit.x)).add(up.scale(hit.y)).add(direction.scale(hit.z));double distance=from.distanceToSqr(world);if(distance>=nearest)continue;
            int dots=(int)Math.round(Vec3.atLowerCornerOf(offset).length()-3);double margin=stats.chainLength()-dots;
            double position=Math.round(Math.clamp(hit.length()-margin,0,Math.max(0,stats.chainLength()-2*margin)))+margin+.025;
            best=new Selection(offset,(float)Math.clamp(position,0,stats.chainLength()),world);nearest=distance;
        }
        return best;
    }
    public static AABB packageBounds(ChainConveyorPackage parcel,double tolerance) {
        double radius=PackageItem.getWidth(parcel.item)/2;var bottom=parcel.worldPosition.subtract(0,PackageItem.getHookDistance(parcel.item),0);
        return new AABB(bottom.add(-radius,0,-radius),bottom.add(radius,PackageItem.getHeight(parcel.item),radius)).inflate(tolerance);
    }
    public static boolean apply(Player player,Packet packet) {
        var level=player.level();if(level.isClientSide()||player.isSpectator()||!player.mayBuild()||!Float.isFinite(packet.position)
                ||!level.mayInteract(player,packet.pos)||!(LoadedLevelAccess.blockEntity(level,packet.pos) instanceof ChainConveyorBlockEntity conveyor))return false;
        var from=player.getEyePosition();double range=player.blockInteractionRange()+1;
        if(from.distanceToSqr(Vec3.atCenterOf(packet.pos))>Math.pow((double)ChainConveyorBlockEntity.maxLength()+range+2,2))return false;
        var to=from.add(player.getLookAngle().scale(range));
        var obstacle=level.clip(new ClipContext(from,to,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        if(packet.action==TAKE){
            conveyor.updateBoxWorldPositions();var parcels=new java.util.ArrayList<>(conveyor.getLoopingPackages());conveyor.getTravellingPackages().values().forEach(parcels::addAll);
            for(var parcel:parcels)if(parcel.netId==packet.netId){
                var hit=packageBounds(parcel,.3).clip(from,to).orElse(null);if(hit==null||occluded(from,hit,obstacle,null))return false;
                var item=conveyor.takePackage(parcel.netId);if(item.isEmpty())return false;
                if(player.getMainHandItem().isEmpty())player.setItemInHand(InteractionHand.MAIN_HAND,item);
                else player.getInventory().placeItemBackInInventory(item,net.minecraft.util.Prediction.SERVER_ONLY);
                return true;
            }
            return false;
        }
        var selection=trace(conveyor,from,to);
        if(selection==null||!selection.connection.equals(packet.connection)||Math.abs(selection.position-packet.position)>.1||occluded(from,selection.hit,obstacle,packet.pos))return false;
        if(packet.action==RIDE)return ServerChainConveyorHandler.embark(player,conveyor,selection);
        if(packet.action==SELECT_PORT){if(!player.getMainHandItem().is(AllBlocks.PACKAGE_FROGPORT.asItem())||player.isShiftKeyDown())return false;com.simibubi.create.content.logistics.packagePort.PackagePortPlacement.select(player,conveyor,selection);return true;}
        if(packet.action==DISCONNECT){
            if(packet.connection.equals(BlockPos.ZERO)||!player.isShiftKeyDown()||!player.isHolding(stack->stack.is(ChainConveyorRidingState.CHAIN_RIDEABLE)))return false;
            return conveyor.disconnect(packet.connection,player);
        }
        if(packet.action!=ATTACH||!PackageItem.isPackage(player.getMainHandItem())||player.isShiftKeyDown())return false;
        var held=player.getMainHandItem();var parcel=new ChainConveyorPackage(packet.position,held);
        boolean attached=packet.connection.equals(BlockPos.ZERO)?conveyor.addLoopingPackage(parcel):conveyor.addTravellingPackage(parcel,packet.connection);
        if(attached&&!player.isCreative())held.shrink(1);return attached;
    }
    private static boolean occluded(Vec3 from,Vec3 hit,BlockHitResult obstacle,BlockPos anchor) {
        return obstacle.getType()!=HitResult.Type.MISS&&!obstacle.getBlockPos().equals(anchor)&&from.distanceToSqr(obstacle.getLocation())+.04<from.distanceToSqr(hit);
    }
    private ChainConveyorInteraction(){}
}
