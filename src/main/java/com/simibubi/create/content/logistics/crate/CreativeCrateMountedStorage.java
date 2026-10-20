package com.simibubi.create.content.logistics.crate;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllMountedStorageTypes;
import com.simibubi.create.api.contraption.storage.item.WrapperMountedItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.storage.base.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import java.util.*;

public class CreativeCrateMountedStorage extends WrapperMountedItemStorage<CreativeCrateMountedStorage.Slots> {
    public static final MapCodec<CreativeCrateMountedStorage> CODEC = ItemStack.OPTIONAL_CODEC.xmap(CreativeCrateMountedStorage::new, storage -> storage.supplied.copy()).fieldOf("value");
    private final ItemStack supplied;
    public CreativeCrateMountedStorage(ItemStack supplied) { super(AllMountedStorageTypes.CREATIVE_CRATE, new Slots(supplied.copy())); this.supplied = supplied.copy(); }
    @Override public void setStackInSlot(int slot, ItemStack stack) { Objects.checkIndex(slot, 2); }
    @Override public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) { /* Supply is immutable while mounted. */ }
    public static final class Slots extends CombinedStorage<ItemVariant, SingleSlotStorage<ItemVariant>> implements SlottedStorage<ItemVariant> {
        private Slots(ItemStack stack) {
            super(new ArrayList<>());
            for (int index = 0; index < 2; index++) {
                boolean supply = index == 0;
                parts.add(new SingleSlotStorage<>() {
                    @Override public boolean isResourceBlank() { return !supply || stack.isEmpty(); }
                    @Override public ItemVariant getResource() { return isResourceBlank() ? ItemVariant.blank() : ItemVariant.of(stack); }
                    @Override public long getAmount() { return isResourceBlank() ? 0 : stack.getMaxStackSize(); }
                    @Override public long getCapacity() { return 64; }
                    @Override public long insert(ItemVariant resource, long amount, TransactionContext tx) { StoragePreconditions.notBlankNotNegative(resource, amount); return amount; }
                    @Override public long extract(ItemVariant resource, long amount, TransactionContext tx) { StoragePreconditions.notBlankNotNegative(resource, amount); return !isResourceBlank() && getResource().equals(resource) ? Math.min(amount, stack.getMaxStackSize()) : 0; }
                });
            }
            parts = List.copyOf(parts);
        }
        @Override public int getSlotCount() { return 2; }
        @Override public SingleSlotStorage<ItemVariant> getSlot(int slot) { return parts.get(slot); }
    }
}
