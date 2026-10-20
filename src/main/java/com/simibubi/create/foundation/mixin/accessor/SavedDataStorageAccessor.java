package com.simibubi.create.foundation.mixin.accessor;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read the installed owner without triggering SavedDataStorage.get's disk/codec fallback. */
@Mixin(SavedDataStorage.class)
public interface SavedDataStorageAccessor {
    @Accessor("cache") Map<SavedDataType<?>,Optional<SavedData>> create$getCache();
    @Accessor("dataFolder") Path create$getDataFolder();
}
