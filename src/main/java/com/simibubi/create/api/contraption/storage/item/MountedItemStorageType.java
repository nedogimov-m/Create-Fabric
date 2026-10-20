package com.simibubi.create.api.contraption.storage.item;

import com.mojang.serialization.*;
import com.simibubi.create.impl.contraption.storage.MountedItemStorageFallbackProvider;
import com.simibubi.create.api.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public abstract class MountedItemStorageType<T extends MountedItemStorage> {
    public static final Codec<MountedItemStorageType<?>> CODEC = CreateBuiltInRegistries.MOUNTED_ITEM_STORAGE_TYPE.byNameCodec();
    public static final SimpleRegistry<Block, MountedItemStorageType<?>> REGISTRY = createRegistry();
    private static SimpleRegistry<Block,MountedItemStorageType<?>> createRegistry(){
        var registry=SimpleRegistry.<Block,MountedItemStorageType<?>>create();
        // The first provider is always last priority; all explicit/later addon providers win.
        registry.registerProvider(MountedItemStorageFallbackProvider.INSTANCE);
        return registry;
    }
    public final MapCodec<? extends T> codec;
    protected MountedItemStorageType(MapCodec<? extends T> codec) { this.codec = codec; }
    public final boolean is(TagKey<MountedItemStorageType<?>> tag) {
        var key = CreateBuiltInRegistries.MOUNTED_ITEM_STORAGE_TYPE.getResourceKey(this);
        return key.isPresent() && CreateBuiltInRegistries.MOUNTED_ITEM_STORAGE_TYPE.getOrThrow(key.get()).is(tag);
    }
    public abstract @Nullable T mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be);
}
