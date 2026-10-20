package com.simibubi.create.content.logistics.depot;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ProcessingInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Ordinary non-merging source Depot, retaining this port's canonical Input0/eight outputs. */
public final class DepotBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements BlockEntityBehaviour.Provider {
    public final DepotBehaviour depotBehaviour = new DepotBehaviour(this);
    public final ProcessingInventory inventory = depotBehaviour.inventory;
    public DepotBlockEntity(BlockPos pos, BlockState state) { super(AllBlockEntityTypes.DEPOT, pos, state); }
    public static void tick(Level level, BlockPos pos, BlockState state, DepotBlockEntity depot) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, depot)) {
            if (!createTick.shouldTick()) return;
  
        }
    }
    public ItemStack getHeldItem() { return depotBehaviour.getHeldItemStack(); }
    public TransportedItemStack getTransportedItem() { return depotBehaviour.getHeldItem(); }
    public void setTransportedItem(TransportedItemStack item) { depotBehaviour.setTransportMetadata(item); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return super.getUpdateTag(registries); }
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (depotBehaviour != null) {
            behaviours.add(depotBehaviour);
            behaviours.add(depotBehaviour.directInput);
            behaviours.add(depotBehaviour.transportedHandler);
        }
    }
}
