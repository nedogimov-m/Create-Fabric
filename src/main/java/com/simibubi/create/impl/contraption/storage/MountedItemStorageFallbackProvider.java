package com.simibubi.create.impl.contraption.storage;

import com.simibubi.create.AllMountedStorageTypes;
import com.simibubi.create.Create;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import com.simibubi.create.api.registry.SimpleRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/** Actual source blacklist and native tag reload invalidation, registered before every other provider. */
public enum MountedItemStorageFallbackProvider implements SimpleRegistry.Provider<Block, MountedItemStorageType<?>> {
    INSTANCE;
    public static final TagKey<Block> BLACKLIST = TagKey.create(Registries.BLOCK, Create.asResource("fallback_mounted_storage_blacklist"));
    @Override public @Nullable MountedItemStorageType<?> get(Block block) {
        return block.builtInRegistryHolder().is(BLACKLIST) ? null : AllMountedStorageTypes.FALLBACK;
    }
    @Override public void onRegister(Runnable invalidate) {
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> invalidate.run());
    }
}
