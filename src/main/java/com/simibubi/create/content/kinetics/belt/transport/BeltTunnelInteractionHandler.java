package com.simibubi.create.content.kinetics.belt.transport;

import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.tunnel.*;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;

public final class BeltTunnelInteractionHandler {
    public static BeltTunnelBlockEntity getTunnelOnPosition(Level level,BlockPos beltPos){return LoadedLevelAccess.blockEntity(level,beltPos.above()) instanceof BeltTunnelBlockEntity tunnel?tunnel:null;}
    public static float checkForTunnels(BeltBlockEntity belt,BeltInventory inventory,TransportedItemStack original,float next){
        if(belt.getBlockState().getValue(BeltBlock.SLOPE)!=BeltSlope.HORIZONTAL)return next;
        boolean positive=belt.getDirectionAwareBeltMovementSpeed()>0;int step=positive?1:-1,from=(int)Math.floor(original.beltPosition),to=(int)Math.floor(next);if(!positive&&next==0)to=-1;
        var world=belt.getLevel();var movement=belt.getMovementFacing();
        for(int current=from;current!=to;current+=step){
            int upcoming=current+step;var item=inventory.byId(original.id);if(item==null)return next;
            var tunnel=getTunnelOnPosition(world,BeltHelper.getPositionForOffset(belt,upcoming));int transferred=item.stack.getCount();float stop=current+(positive?.99f:.01f);
            if(tunnel instanceof BrassTunnelBlockEntity brass){
                if(!brass.canInsert(movement.getOpposite(),item.stack))return stop;
                if(brass.hasDistributionBehaviour()){
                    if((!world.isClientSide()||belt.isVirtual()))try(var tx=Transaction.openOuter()){long accepted=brass.accept(item.stack,movement.getOpposite(),tx);if(accepted==item.stack.getCount()&&inventory.extractItem(item.id,(int)accepted,tx)==accepted){tx.addOuterCloseCallback(result->{if(result.wasCommitted())com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock.sendToGatherers(world,brass.getBlockPos(),(link,source)->source.itemReceived(link,(int)accepted),com.simibubi.create.content.redstone.displayLink.source.AccumulatedItemCountDisplaySource.class);});tx.commit();}else return stop;}
                    return stop;
                }
            }else if(tunnel!=null&&item.stack.getCount()>1&&BeltTunnelBlock.isJunction(tunnel.getBlockState())&&movement.getAxis()==tunnel.getBlockState().getValue(BeltTunnelBlock.HORIZONTAL_AXIS)&&(!world.isClientSide()||belt.isVirtual())){
                for(var side:new Direction[]{Direction.SOUTH,Direction.WEST,Direction.NORTH,Direction.EAST}){
                    var actual=inventory.byId(item.id);if(actual==null||actual.stack.getCount()<=1)break;
                    if(side.getAxis()==movement.getAxis()||!tunnel.flaps.containsKey(side))continue;
                    var target=tunnel.getBlockPos().below().relative(side);if(!world.isLoaded(target))return stop;
                    var input=BlockEntityBehaviour.get(world,target,DirectBeltInputBehaviour.TYPE);if(input==null||!input.canInsertFromSide(side))continue;
                    try(var tx=Transaction.openOuter()){
                        if(input.insert(actual.stack.copyWithCount(1),side,tx)!=1||inventory.extractItem(item.id,1,tx)!=1)return stop;
                        var flapSide=side;tx.addOuterCloseCallback(result->{if(result.wasCommitted())tunnel.flap(flapSide,false);});tx.commit();
                    }
                }
            }
            if((!world.isClientSide()||belt.isVirtual())){
                var previous=getTunnelOnPosition(world,BeltHelper.getPositionForOffset(belt,current));if(previous!=null)previous.flap(movement,false);if(tunnel!=null){tunnel.flap(movement.getOpposite(),true);com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock.sendToGatherers(world,tunnel.getBlockPos(),(link,source)->source.itemReceived(link,transferred),com.simibubi.create.content.redstone.displayLink.source.AccumulatedItemCountDisplaySource.class);}
            }
        }
        return next;
    }
    private BeltTunnelInteractionHandler(){}
}
