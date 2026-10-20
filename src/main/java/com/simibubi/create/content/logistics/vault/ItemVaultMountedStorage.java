package com.simibubi.create.content.logistics.vault;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllMountedStorageTypes;
import com.simibubi.create.api.contraption.storage.item.simple.SimpleMountedStorage;
import com.simibubi.create.foundation.item.MountedItemInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class ItemVaultMountedStorage extends SimpleMountedStorage {
    public static final MapCodec<ItemVaultMountedStorage> CODEC = codec(ItemVaultMountedStorage::new);
    public ItemVaultMountedStorage(MountedItemInventory inventory) { super(AllMountedStorageTypes.VAULT, inventory); }
    /** Existing overflow stays extractable on moving vaults; only active configured slots accept insertion. */
    @Override public long insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant resource,long amount,net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext tx){
        net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions.notBlankNotNegative(resource,amount);if(!isAvailable())return 0;long moved=0;
        for(int i=0;i<Math.min(wrapped.getSlotCount(),ItemVaultBlockEntity.configuredSlots())&&moved<amount;i++)moved+=super.getSlot(i).insert(resource,amount-moved,tx);return moved;
    }
    @Override public net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant> getSlot(int index){
        var actual=super.getSlot(index);return new net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage<>(){
            private boolean active(){return index<ItemVaultBlockEntity.configuredSlots();}
            @Override public boolean isResourceBlank(){return actual.isResourceBlank();}
            @Override public net.fabricmc.fabric.api.transfer.v1.item.ItemVariant getResource(){return actual.getResource();}
            @Override public long getAmount(){return actual.getAmount();}
            @Override public long getCapacity(){return active()?actual.getCapacity():actual.getAmount();}
            @Override public boolean supportsInsertion(){return active()&&actual.supportsInsertion();}
            @Override public boolean supportsExtraction(){return actual.supportsExtraction();}
            @Override public long insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant resource,long amount,net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext tx){net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions.notBlankNotNegative(resource,amount);return active()?actual.insert(resource,amount,tx):0;}
            @Override public long extract(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant resource,long amount,net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext tx){return actual.extract(resource,amount,tx);}
            @Override public long getVersion(){return isAvailable()?actual.getVersion()*31+ItemVaultBlockEntity.configuredSlots():Long.MIN_VALUE;}
        };
    }
    @Override public java.util.Iterator<net.fabricmc.fabric.api.transfer.v1.storage.StorageView<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>> iterator(){var result=new java.util.ArrayList<net.fabricmc.fabric.api.transfer.v1.storage.StorageView<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>>();for(int i=0;i<getSlotCount();i++)result.add(getSlot(i));return result.iterator();}
    @Override public long getVersion(){return isAvailable()?super.getVersion()*31+ItemVaultBlockEntity.configuredSlots():Long.MIN_VALUE;}
    @Override public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof ItemVaultBlockEntity vault) vault.restoreMounted(wrapped);
    }
    public static ItemVaultMountedStorage fromVault(ItemVaultBlockEntity vault) { return new ItemVaultMountedStorage(MountedItemInventory.copy(vault.mountedSlots())); }
}
