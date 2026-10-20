package com.simibubi.create.content.contraptions.actors.seat;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

/** Original escape onto a solid surface intersected by an occupied moving seat. */
public class SeatMovementBehaviour implements MovementBehaviour {
    @Override public void startMoving(MovementContext context){context.data.putInt("SeatIndex",context.contraption.getSeats().indexOf(context.localPos));}
    @Override public void visitNewPosition(MovementContext context,BlockPos pos){
        if(context.world.isClientSide()||!LoadedLevelAccess.isLoaded(context.world,pos))return;var owner=context.contraption.entity;if(owner==null)return;int index=context.data.getIntOr("SeatIndex",-1);if(index<0)return;
        var state=LoadedLevelAccess.blockState(context.world,pos);boolean slab=state.getBlock() instanceof SlabBlock&&state.getValue(SlabBlock.TYPE)==SlabType.BOTTOM;
        if(!state.canOcclude()&&!slab)return;
        for(var passenger:java.util.List.copyOf(owner.getPassengers()))if(java.util.Objects.equals(context.contraption.getSeatMapping().get(passenger.getUUID()),index)){
            var target=Vec3.atCenterOf(pos).add(0,slab?.5:1,0);passenger.stopRiding();owner.clearDismountLocation(passenger);passenger.teleportTo(target.x,target.y,target.z);com.simibubi.create.content.contraptions.ContraptionNetworking.syncSeats(owner,passenger,target);break;
        }
    }
}
