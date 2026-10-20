package com.simibubi.create.api.contraption.storage.item;

import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.minecraft.core.BlockPos;

public class MountedItemStorageWrapper extends CombinedStorage<ItemVariant, MountedItemStorage> {
    public final Map<BlockPos, MountedItemStorage> storages;
    public MountedItemStorageWrapper(Map<BlockPos, MountedItemStorage> storages) { super(List.copyOf(storages.values())); this.storages = Collections.unmodifiableMap(new LinkedHashMap<>(storages)); }
    @Override public long getVersion() { long version = 1; for (var storage : parts) version = 31 * version + storage.getVersion(); return version; }
}
