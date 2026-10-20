package com.simibubi.create.api.contraption.storage.item.chest;

import com.simibubi.create.api.contraption.storage.item.simple.SimpleMountedStorageType;

public class ChestMountedStorageType extends SimpleMountedStorageType<ChestMountedStorage> {
    public ChestMountedStorageType() { super(ChestMountedStorage.CODEC, ChestMountedStorage::new); }
}
