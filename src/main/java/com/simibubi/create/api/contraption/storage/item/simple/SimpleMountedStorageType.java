package com.simibubi.create.api.contraption.storage.item.simple;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import com.simibubi.create.foundation.item.MountedItemInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class SimpleMountedStorageType<T extends SimpleMountedStorage> extends MountedItemStorageType<T> {
    private final java.util.function.Function<MountedItemInventory, T> factory;
    public SimpleMountedStorageType(MapCodec<T> codec, java.util.function.Function<MountedItemInventory, T> factory) { super(codec); this.factory = factory; }
    @Override public @Nullable T mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        return be instanceof Container container ? factory.apply(MountedItemInventory.copy(container)) : null;
    }
    public static final class Impl extends SimpleMountedStorageType<SimpleMountedStorage> {
        public Impl() { super(SimpleMountedStorage.CODEC, SimpleMountedStorage::new); }
    }
}
