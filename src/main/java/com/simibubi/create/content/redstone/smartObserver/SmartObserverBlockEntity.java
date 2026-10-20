package com.simibubi.create.content.redstone.smartObserver;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour;
import com.simibubi.create.foundation.fluid.FluidStack;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public final class SmartObserverBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements BlockEntityBehaviour.Provider,Clearable {
    public static final int DEFAULT_DELAY=6;
    public final VersionedInventoryTrackerBehaviour inventoryTracker=new VersionedInventoryTrackerBehaviour(this);
    public final FilteringBehaviour filtering=new FilteringBehaviour(this).withCallback(stack->resetInventoryTracking());
    public int turnOffTicks;
    private boolean sustainSignal;
    private BlockPos lastTarget;
    public SmartObserverBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.SMART_OBSERVER,pos,state);}
    public void resetInventoryTracking(){inventoryTracker.reset();sustainSignal=false;}
    public static Storage<ItemVariant> findItems(Level level,BlockPos pos){return com.simibubi.create.content.logistics.funnel.FunnelTransfer.find(level,pos,null);}
    public static Storage<FluidVariant> findFluids(Level level,BlockPos pos){if(!level.isLoaded(pos))return null;return FluidStorage.SIDED.find(level,pos,LoadedLevelAccess.blockState(level,pos),LoadedLevelAccess.blockEntity(level,pos),null);}
    public static void tick(Level level,BlockPos pos,BlockState state,SmartObserverBlockEntity observer){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, observer)) {
            if (!createTick.shouldTick()) return;

        if(level.isClientSide())return;
        if(observer.turnOffTicks>0){observer.turnOffTicks--;observer.setChanged();if(observer.turnOffTicks==0)level.scheduleTick(pos,state.getBlock(),1);}
        var target=pos.relative(SmartObserverBlock.getTargetDirection(state));
        if(!target.equals(observer.lastTarget)){observer.lastTarget=target;observer.resetInventoryTracking();}
        if(!level.isLoaded(target)){observer.resetInventoryTracking();return;}
        var targetState=LoadedLevelAccess.blockState(level,target);
        if(!observer.filtering.getFilter().isEmpty()&&observer.filtering.test(new ItemStack(targetState.getBlock()))){observer.activate(3);return;}
        var transported=BlockEntityBehaviour.get(level,target,TransportedItemStackHandlerBehaviour.TYPE);
        if(transported!=null){transported.handleCenteredProcessingOnAllItems(.45f,stack->{if(observer.filtering.test(stack.stack)&&observer.turnOffTicks!=DEFAULT_DELAY)observer.activate();return TransportedItemStackHandlerBehaviour.TransportedResult.doNothing();});return;}
        var pipe=FluidPropagator.getPipe(level,target);
        if(pipe!=null){for(var side:Direction.values()){var flow=pipe.getFlow(side);if(flow!=null&&flow.inbound&&flow.complete&&observer.filtering.test(flow.fluid)){observer.activate();return;}}return;}
        if(LoadedLevelAccess.blockEntity(level,target) instanceof com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity conveyor){
            for(var parcel:conveyor.getLoopingPackages())if(observer.filtering.test(parcel.item)){observer.activate();return;}
            return;
        }
        var inventory=findItems(level,target);
        if(inventory!=null){
            boolean waiting=observer.inventoryTracker.stillWaiting(inventory);observer.inventoryTracker.awaitNewVersion(inventory);
            if(waiting&&observer.sustainSignal)observer.turnOffTicks=DEFAULT_DELAY;
            if(!waiting){observer.sustainSignal=false;if(observer.containsExtractableItem(inventory)){observer.sustainSignal=true;observer.activate();return;}}
        }else observer.inventoryTracker.reset();
        var fluids=findFluids(level,target);if(fluids!=null&&observer.containsExtractableFluid(fluids))observer.activate();
    
        }
    }
    private boolean containsExtractableItem(Storage<ItemVariant> inventory){
        var variants=new LinkedHashSet<ItemVariant>();for(var view:inventory)if(!view.isResourceBlank()&&view.getAmount()>0)variants.add(view.getResource());
        for(var variant:variants){if(!filtering.test(variant.toStack()))continue;try(var tx=Transaction.openOuter()){if(inventory.extract(variant,1,tx)>0)return true;}}return false;
    }
    private boolean containsExtractableFluid(Storage<FluidVariant> inventory){
        var variants=new LinkedHashSet<FluidVariant>();for(var view:inventory)if(!view.isResourceBlank()&&view.getAmount()>0)variants.add(view.getResource());
        for(var variant:variants){if(!filtering.test(new FluidStack(variant,1)))continue;try(var tx=Transaction.openOuter()){if(inventory.extract(variant,Long.MAX_VALUE,tx)>0)return true;}}return false;
    }
    public void activate(){activate(DEFAULT_DELAY);}
    public void activate(int ticks){
        if(level==null||level.isClientSide()||isRemoved()||!(getBlockState().getBlock() instanceof SmartObserverBlock))return;
        turnOffTicks=ticks;setChanged();var state=getBlockState();if(state.getValue(SmartObserverBlock.POWERED))return;
        level.setBlockAndUpdate(worldPosition,state.setValue(SmartObserverBlock.POWERED,true));level.updateNeighborsAt(worldPosition,state.getBlock());
    }
    @Override public void clearContent(){filtering.setFilter(ItemStack.EMPTY);}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level!=null&&!level.isClientSide()&&state.getValue(SmartObserverBlock.POWERED))level.updateNeighborsAt(pos,state.getBlock());super.preRemoveSideEffects(pos,state);}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.putInt("TurnOff",turnOffTicks);}
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);turnOffTicks=Math.max(0,in.getIntOr("TurnOff",0));resetInventoryTracking();lastTarget=null;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (inventoryTracker != null) behaviours.add(inventoryTracker);
        if (filtering != null) behaviours.add(filtering);
    }
}
