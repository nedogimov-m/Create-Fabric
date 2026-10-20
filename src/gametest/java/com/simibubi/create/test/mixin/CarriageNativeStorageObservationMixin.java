package com.simibubi.create.test.mixin;

import com.simibubi.create.test.CarriageNativeChunkTestFixtures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.entity.ChunkEntities;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityStorage.class)
public abstract class CarriageNativeStorageObservationMixin {
    @Shadow @Final private ServerLevel level;
    @Inject(method="storeEntities",at=@At("HEAD"))
    private void create$observeActualNativeStorage(ChunkEntities<Entity> entries,CallbackInfo callback){CarriageNativeChunkTestFixtures.storing(level,entries);}
}
