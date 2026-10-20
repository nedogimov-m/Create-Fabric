package com.simibubi.create.impl.contraption.storage;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllMountedStorageTypes;
import com.simibubi.create.api.contraption.storage.item.simple.SimpleMountedStorage;
import com.simibubi.create.foundation.item.MountedItemInventory;
import com.simibubi.create.foundation.mixin.accessor.FabricContainerStorageAccessor;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.impl.transfer.item.ContainerStorageImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Real fallback for explicitly plain native provider inventories, preserving source narrow eligibility. */
public final class FallbackMountedStorage extends SimpleMountedStorage {
    public static final MapCodec<FallbackMountedStorage> CODEC = codec(FallbackMountedStorage::new);
    public FallbackMountedStorage(MountedItemInventory actual) { super(AllMountedStorageTypes.FALLBACK, actual); }

    public static boolean isValid(Storage<ItemVariant> actual) {
        if (actual == null) return false;
        if (actual.getClass() == MountedItemInventory.class) return true;
        if (actual.getClass() != ContainerStorageImpl.class) return false;
        if (!(actual instanceof FabricContainerStorageAccessor access))
            throw new IllegalStateException("Actual native Fabric container identity accessor was not applied");
        return access.create$getContainer().getClass() == SimpleContainer.class;
    }

    static boolean isCurrent(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        return be != null && !be.isRemoved() && be.getLevel() == level && be.getBlockPos().equals(pos)
            && LoadedLevelAccess.isLoaded(level, pos) && LoadedLevelAccess.blockEntity(level, pos) == be
            && state.getBlock() == LoadedLevelAccess.blockState(level, pos).getBlock();
    }
    static @Nullable SlottedStorage<ItemVariant> find(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (!isCurrent(level, state, pos, be)) return null;
        var currentState = LoadedLevelAccess.blockState(level, pos);
        var actual = ItemStorage.SIDED.find(level, pos, currentState, be, null);
        if (!isCurrent(level, state, pos, be) || !currentState.equals(LoadedLevelAccess.blockState(level, pos))) throw new IllegalStateException("Actual native block owner changed during fallback lookup");
        return isValid(actual) ? (SlottedStorage<ItemVariant>) actual : null;
    }

    @Override public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (!isAvailable()) throw new IllegalStateException("Inactive mounted fallback cannot restore live stock");
        var actual = find(level, state, pos, be);
        if (actual == null || actual.getSlotCount() != wrapped.getSlotCount())
            throw new IllegalStateException("Original fallback stock has no matching real plain native destination");
        // The real native slots stage all removals/insertions in one outer transaction and defer
        // native container notifications until commit. Refusal aborts the entire real transfer.
        wrapped.applyTo(actual.getSlots());
        if (be != null) be.setChanged();
    }
}
