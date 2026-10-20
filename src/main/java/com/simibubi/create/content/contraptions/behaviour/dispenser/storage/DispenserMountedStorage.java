package com.simibubi.create.content.contraptions.behaviour.dispenser.storage;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllMountedStorageTypes;
import com.simibubi.create.api.contraption.storage.item.simple.SimpleMountedStorage;
import com.simibubi.create.foundation.item.MountedItemInventory;

/** Dispenser contents are internal; movement behavior consumes these slots directly. */
public class DispenserMountedStorage extends SimpleMountedStorage {
    public static final MapCodec<DispenserMountedStorage> CODEC = codec(DispenserMountedStorage::new);
    public DispenserMountedStorage(MountedItemInventory inventory) { super(AllMountedStorageTypes.DISPENSER, inventory); }
    @Override public net.minecraft.world.MenuProvider createMenuProvider(net.minecraft.network.chat.Component name, java.util.List<com.simibubi.create.api.contraption.storage.item.MountedItemStorage> storages, java.util.function.Predicate<net.minecraft.world.entity.player.Player> stillValid, java.util.function.Consumer<net.minecraft.world.entity.player.Player> onClose) {
        return com.simibubi.create.api.contraption.storage.item.menu.MountedStorageMenus.createGeneric9x9(name, storages, stillValid, onClose);
    }
}
