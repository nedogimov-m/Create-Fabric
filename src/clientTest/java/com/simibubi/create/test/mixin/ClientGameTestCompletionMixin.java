package com.simibubi.create.test.mixin;

import com.simibubi.create.test.ClientGameTestCompletionReport;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.impl.client.gametest.FabricClientGameTestRunner;
import net.fabricmc.fabric.impl.client.gametest.context.ClientGameTestContextImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FabricClientGameTestRunner.class, remap = false)
public abstract class ClientGameTestCompletionMixin {
    @Inject(method = "start()V", at = @At("HEAD"), remap = false)
    private static void create$begin(CallbackInfo ci) { ClientGameTestCompletionReport.begin(); }
    @Inject(method = "setupInitialGameTestState", at = @At("HEAD"), remap = false)
    private static void create$started(ClientGameTestContext context, CallbackInfo ci) { ClientGameTestCompletionReport.started(); }
    @Inject(method = "setupAndCheckFinalGameTestState", at = @At("RETURN"), remap = false)
    private static void create$completed(ClientGameTestContextImpl context, CallbackInfo ci) { ClientGameTestCompletionReport.completedAfterNativeCleanup(); }
    @Inject(method = "lambda$start$0(Ljava/util/List;)V", at = @At("RETURN"), remap = false)
    private static void create$suiteReturned(List<?> tests, CallbackInfo ci) { ClientGameTestCompletionReport.suiteReturned(tests.size()); }
}
