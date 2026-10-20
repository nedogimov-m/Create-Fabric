package com.simibubi.create.content.kinetics.belt.transport;

import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.logistics.funnel.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

/** Crossing tests preserve Create's blocking front and non-blocking side intake. */
public final class BeltFunnelInteractionHandler {
    public static float checkForFunnels(BeltBlockEntity belt,BeltInventory inventory,TransportedItemStack item,float next){
        var world=belt.getLevel();boolean positive=belt.getDirectionAwareBeltMovementSpeed()>0;
        var motion=positive?belt.getBeltFacing():belt.getBeltFacing().getOpposite();
        int step=positive?1:-1;
        for(int segment=Math.clamp((int)Math.floor(item.beltPosition),0,belt.beltLength-1);segment>=0&&segment<belt.beltLength;segment+=step){
            if(positive?segment>next:segment+1<next)break;
            var pos=BeltHelper.getPositionForOffset(belt,segment).above();var state=LoadedLevelAccess.blockState(world,pos);
            if(!(state.getBlock() instanceof BeltFunnelBlock))continue;
            var facing=state.getValue(BeltFunnelBlock.HORIZONTAL_FACING);var shape=state.getValue(BeltFunnelBlock.SHAPE);
            if(facing==motion||shape==BeltFunnelBlock.Shape.PUSHING)continue;
            boolean blocking=facing==motion.getOpposite();float entry=segment+.5f;
            if(shape==BeltFunnelBlock.Shape.EXTENDED)entry+=positive?-.499f:.499f;
            if(positive?next<=entry:next>=entry)break;
            if(!world.isClientSide()&&!state.getValue(BeltFunnelBlock.POWERED)&&LoadedLevelAccess.blockEntity(world,pos) instanceof FunnelBlockEntity funnel){
                boolean exact=funnel.isExact();int amount=exact?funnel.getAmountToExtract():item.stack.getCount();
                if(amount<=item.stack.getCount()){
                    try(var tx=Transaction.openOuter()){
                        long accepted=funnel.insert(ItemVariant.of(item.stack),amount,exact,tx);
                        if(accepted>0&&inventory.extractItem(item.id,(int)accepted,tx)==accepted)tx.commit();
                    }
                    // Transaction aborts can replace snapshot objects; use the current UUID handle.
                    var current=inventory.byId(item.id);if(current==null)return blocking?entry:next;
                    item=current;
                }
            }
            if(blocking)return entry;
        }
        return next;
    }
    private BeltFunnelInteractionHandler(){}
}
