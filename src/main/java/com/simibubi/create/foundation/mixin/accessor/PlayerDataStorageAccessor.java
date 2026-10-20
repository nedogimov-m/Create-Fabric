package com.simibubi.create.foundation.mixin.accessor;

import java.io.File;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerDataStorage.class)
public interface PlayerDataStorageAccessor {
    @Accessor("playerDir") File create$getPlayerDirectory();
}
