package com.simibubi.create.content.logistics.depot.storage;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllMountedStorageTypes;
import com.simibubi.create.api.contraption.storage.SyncedMountedStorage;
import com.simibubi.create.api.contraption.storage.item.simple.SimpleMountedStorage;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.item.MountedItemInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class DepotMountedStorage extends SimpleMountedStorage implements SyncedMountedStorage {
    public static final MapCodec<DepotMountedStorage> CODEC = ItemStack.OPTIONAL_CODEC.xmap(DepotMountedStorage::new, DepotMountedStorage::savedItem).fieldOf("value");
    private boolean dirty;
    public DepotMountedStorage(ItemStack stack) {
        super(AllMountedStorageTypes.DEPOT, new MountedItemInventory(1));
        wrapped.setStack(0, stack); wrapped.onChange(() -> dirty = true);
    }
    private ItemStack savedItem() { return wrapped.getStack(0); }
    public ItemStack getItem() { return getStackInSlot(0); }
    public void setItem(ItemStack stack) { setStackInSlot(0, stack); }
    @Override public boolean isDirty() { return dirty; }
    @Override public void markClean() { dirty = false; }
    @Override public void afterSync(com.simibubi.create.content.contraptions.Contraption contraption, BlockPos localPos) {
        if (contraption.getBlockEntityClientSide(localPos) instanceof DepotBlockEntity depot) wrapped.applyTo(depot.inventory.inputs);
    }
    @Override public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof DepotBlockEntity depot) wrapped.applyTo(depot.inventory.inputs);
    }
}
