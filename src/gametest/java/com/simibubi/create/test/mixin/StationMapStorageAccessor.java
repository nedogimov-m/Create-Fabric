package com.simibubi.create.test.mixin;
import java.nio.file.Path;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
/** Actual native filename resolution only; no cache replacement, storage rebinding or IO mutation. */
@Mixin(SavedDataStorage.class)
public interface StationMapStorageAccessor {
    @Invoker("getDataFile") Path create$stationMapDataFile(Identifier id);
}
