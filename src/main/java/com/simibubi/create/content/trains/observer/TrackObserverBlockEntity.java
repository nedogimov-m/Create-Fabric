package com.simibubi.create.content.trains.observer;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** True native detector owner, shared source filter interaction and actual redstone/display output. */
public final class TrackObserverBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements TransformableBlockEntity,Clearable,
    TrackEdgePoint.MigrationReceiver,TrackTargetingBlockItem.Target,BlockEntityBehaviour.Provider {
  private final TrackTargetingBehaviour<TrackObserver> targeting=new TrackTargetingBehaviour<>(this,ObserverEdgePoints.OBSERVER,this::sync);
  public final FilteringBehaviour filtering=new FilteringBehaviour(this).setLabel(Component.translatable("create.logistics.train_observer.cargo_filter")).withCallback(this::onFilterChanged);
  public TrackObserverBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.TRACK_OBSERVER,pos,state);}
  @Override public TrackTargetingBehaviour<TrackObserver> targeting(){return targeting;}
  @Override public boolean setTarget(TrackTargetingSelection selected){boolean accepted=targeting.configure(selected);if(accepted)targeting.resolveTick();return accepted;}
  public TrackObserver getObserver(){return targeting.getEdgePoint();}
  public ItemStack getFilter(){return filtering.getFilter();}
  public boolean isBlockPowered(){return getBlockState().getValue(TrackObserverBlock.POWERED);}
  private void onFilterChanged(ItemStack filter){if(level==null||level.isClientSide())return;var observer=getObserver();if(observer!=null)observer.setFilterAndNotify(level,filter);}
  private void sync(){setChanged();if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
  public static void tick(Level level,BlockPos pos,BlockState state,TrackObserverBlockEntity entity){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, entity)) {
            if (!createTick.shouldTick()) return;

    var observer=entity.targeting.getEdgePoint();if(level.isClientSide())return;
    boolean powered=observer!=null&&observer.isActivated();
    if(powered==entity.isBlockPowered())return;
    level.setBlock(pos,state.setValue(TrackObserverBlock.POWERED,powered),Block.UPDATE_ALL);
    com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock.notifyGatherers(level,pos);
  
        }
    }
  @Override public void clearContent(){filtering.setFilter(ItemStack.EMPTY);}
  @Override public void transform(BlockEntity entity,StructureTransform transform){targeting.transform(entity,transform);}
  @Override public void invalidateEdgePoint(CompoundTag source){targeting.invalidateEdgePoint(source);}
  @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
  @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        if (targeting != null) behaviours.add(targeting);
        super.addNativeBehaviours(behaviours);
        if (filtering != null) behaviours.add(filtering);
    }
}
