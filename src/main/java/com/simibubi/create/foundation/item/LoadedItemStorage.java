package com.simibubi.create.foundation.item;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Shared inventory lookup without Level's loading reads, including vanilla double chests. */
public final class LoadedItemStorage {
    private record Access(Level level, BlockPos pos, Direction side) {}
    private static final ThreadLocal<Set<Access>> ACCESS = ThreadLocal.withInitial(HashSet::new);

    public static Storage<ItemVariant> find(Level level, BlockPos pos, Direction side) {
        if (!level.isLoaded(pos)) return null;
        var access = new Access(level, pos.immutable(), side);
        var active = ACCESS.get();
        if (!active.add(access)) return null;
        try {
            var state = LoadedLevelAccess.blockState(level, pos);
            var entity = LoadedLevelAccess.blockEntity(level, pos);
            if (state.getBlock() instanceof ChestBlock && entity instanceof ChestBlockEntity chest) {
                // A mod's explicit chest provider takes priority over Fabric's vanilla fallback.
                var provider = ItemStorage.SIDED.getProvider(state.getBlock());
                if (provider != null) {
                    var custom = provider.find(level, pos, state, entity, side);
                    if (custom != null) return WorldBlockEntityStorage.of(entity,custom,ItemVariant.blank());
                }
                return WorldBlockEntityStorage.of(entity,LoadedChestStorage.of(chest, side),ItemVariant.blank());
            }
            return WorldBlockEntityStorage.of(entity,ItemStorage.SIDED.find(level, pos, state, entity, side),ItemVariant.blank());
        } finally {
            active.remove(access);
            if (active.isEmpty()) ACCESS.remove();
        }
    }

    private LoadedItemStorage() {}
}
