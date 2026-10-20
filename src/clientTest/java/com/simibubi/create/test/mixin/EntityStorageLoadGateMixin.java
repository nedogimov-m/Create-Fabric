package com.simibubi.create.test.mixin;

import com.simibubi.create.test.NativeEntityLoadGate;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.entity.ChunkEntities;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityStorage.class)
abstract class EntityStorageLoadGateMixin {
    @Shadow @Final private ServerLevel level;
    @Inject(method="loadEntities",at=@At("RETURN"),cancellable=true)
    private void createTest$holdRealEntityPayload(ChunkPos pos,CallbackInfoReturnable<CompletableFuture<ChunkEntities<Entity>>> cir) {
        cir.setReturnValue(NativeEntityLoadGate.wrap(level,pos,cir.getReturnValue()));
    }
}
