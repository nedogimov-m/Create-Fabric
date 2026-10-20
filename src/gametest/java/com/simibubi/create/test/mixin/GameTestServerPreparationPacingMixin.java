package com.simibubi.create.test.mixin;

import com.simibubi.create.test.NativeGameTestPreparationWait;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Headless sprinting must give asynchronous native preparation the ordinary tick's task window. */
@Mixin(GameTestServer.class)
public abstract class GameTestServerPreparationPacingMixin {
    @Inject(method = "waitUntilNextTick", at = @At("TAIL"))
    private void create$awaitNativePreparationWork(CallbackInfo ci) {
        var server = (GameTestServer) (Object) this;
        if (!NativeGameTestPreparationWait.consume(server)) return;
        // Vanilla GameTestServer just drained runAllTasks(). managedBlock keeps polling actual
        // chunk/generation/light tasks while background workers run; the next real level tick
        // processes entity I/O. Ready fixtures keep vanilla's full-speed headless behavior.
        long deadline = Util.getNanos() + server.tickRateManager().nanosecondsPerTick();
        server.managedBlock(() -> Util.getNanos() - deadline >= 0);
    }
}
