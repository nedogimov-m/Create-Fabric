package com.simibubi.create.content.contraptions.behaviour.dispenser.storage;

import com.simibubi.create.api.contraption.storage.item.simple.SimpleMountedStorageType;

public class DispenserMountedStorageType extends SimpleMountedStorageType<DispenserMountedStorage> {
    public DispenserMountedStorageType() { super(DispenserMountedStorage.CODEC, DispenserMountedStorage::new); }
}
