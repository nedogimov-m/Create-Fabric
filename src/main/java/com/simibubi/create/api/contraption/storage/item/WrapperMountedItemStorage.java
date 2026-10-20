package com.simibubi.create.api.contraption.storage.item;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;

public abstract class WrapperMountedItemStorage<T extends SlottedStorage<ItemVariant>> extends MountedItemStorage {
    protected final T wrapped;
    protected WrapperMountedItemStorage(MountedItemStorageType<?> type, T wrapped) { super(type); this.wrapped = wrapped; }
    @Override protected final T delegate() { return wrapped; }
}
