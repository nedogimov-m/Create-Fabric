package com.simibubi.create.content.logistics.stockTicker;

import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.packagerLink.*;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** Shared stock access for requesters and stock keepers; only stock links supply goods. */
public abstract class StockCheckingBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements BlockEntityBehaviour.Provider {
    public final LogisticallyLinkedBehaviour behaviour=new LogisticallyLinkedBehaviour(this,false);
    private boolean initialized;
    protected StockCheckingBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state) { super(type,pos,state); }
    public void tick() {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, worldPosition, getBlockState(), this)) {
            if (!createTick.shouldTick()) return;
  
        }
    }
    public InventorySummary getRecentSummary() { return LogisticsManager.getSummaryOfNetwork(level,behaviour.freqId,false); }
    public InventorySummary getAccurateSummary() { return LogisticsManager.getSummaryOfNetwork(level,behaviour.freqId,true); }
    public boolean broadcastPackageRequest(LogisticallyLinkedBehaviour.RequestType type,PackageOrder order,IdentifiedInventory ignored,String address) { return broadcastPackageRequest(type,PackageOrderWithCrafts.simple(order.stacks()),ignored,address); }
    public boolean broadcastPackageRequest(LogisticallyLinkedBehaviour.RequestType type,PackageOrderWithCrafts order,IdentifiedInventory ignored,String address) { return LogisticsManager.broadcastPackageRequest(level,behaviour.freqId,type,order,ignored,address); }
    public void notifyUpdate() { setChanged();if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS); }
    @Override public void invalidate() { initialized=false;super.invalidate(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { var out=TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,registries);write(out, true);return out.buildResult(); }
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (behaviour != null) behaviours.add(behaviour);
    }
}
