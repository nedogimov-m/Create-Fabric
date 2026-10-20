package com.simibubi.create.api.contraption.storage.fluid;

import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.minecraft.core.BlockPos;

public class MountedFluidStorageWrapper extends CombinedStorage<FluidVariant, MountedFluidStorage> {
    public final Map<BlockPos, MountedFluidStorage> storages;
    public MountedFluidStorageWrapper(Map<BlockPos, MountedFluidStorage> storages) { super(List.copyOf(storages.values())); this.storages = Collections.unmodifiableMap(new LinkedHashMap<>(storages)); }
    @Override public long getVersion() { long version = 1; for (var storage : parts) version = 31 * version + storage.getVersion(); return version; }
}
