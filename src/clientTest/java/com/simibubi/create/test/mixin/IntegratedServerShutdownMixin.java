package com.simibubi.create.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 26.3 halt queues player cleanup; blocking here can deadlock Fabric's three-thread test barrier. */
@Mixin(IntegratedServer.class)
abstract class IntegratedServerShutdownMixin {
    @WrapOperation(method = "halt", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;executeBlocking(Ljava/lang/Runnable;)V"))
    private void createTest$queueCleanup(IntegratedServer server, Runnable cleanup, Operation<Void> original) {
        server.execute(cleanup);
    }
}
