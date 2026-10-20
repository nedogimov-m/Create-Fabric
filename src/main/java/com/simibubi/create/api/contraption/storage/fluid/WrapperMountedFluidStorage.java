package com.simibubi.create.api.contraption.storage.fluid;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

public abstract class WrapperMountedFluidStorage<T extends Storage<FluidVariant>> extends MountedFluidStorage {
    protected final T wrapped;
    protected WrapperMountedFluidStorage(MountedFluidStorageType<?> type, T wrapped) { super(type); this.wrapped = wrapped; }
    @Override protected final T delegate() { return wrapped; }
}
