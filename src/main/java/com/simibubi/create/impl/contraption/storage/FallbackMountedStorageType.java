package com.simibubi.create.impl.contraption.storage;

import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import com.simibubi.create.foundation.item.MountedItemInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Last-priority genuine Fabric plain-inventory lookup; unrelated/custom handlers are never guessed. */
public final class FallbackMountedStorageType extends MountedItemStorageType<FallbackMountedStorage> {
    public FallbackMountedStorageType() { super(FallbackMountedStorage.CODEC); }
    @Override public @Nullable FallbackMountedStorage mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        var actual = FallbackMountedStorage.find(level, state, pos, be);
        if (actual == null) return null;
        var snapshot = MountedItemInventory.copy(actual);
        if (!FallbackMountedStorage.isCurrent(level, state, pos, be))
            throw new IllegalStateException("Actual native fallback source changed during capture");
        return new FallbackMountedStorage(snapshot);
    }
}
