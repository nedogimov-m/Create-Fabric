package com.simibubi.create.content.logistics.depot.storage;

import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class DepotMountedStorageType extends MountedItemStorageType<DepotMountedStorage> {
    public DepotMountedStorageType() { super(DepotMountedStorage.CODEC); }
    @Override public @Nullable DepotMountedStorage mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        return be instanceof DepotBlockEntity depot ? new DepotMountedStorage(depot.getHeldItem()) : null;
    }
}
