package com.simibubi.create.test.mixin;

import com.simibubi.create.test.ClientGameTestCompletionReport;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ClientGameTestCompletionShutdownMixin {
    @Inject(method = "stop", at = @At("HEAD"))
    private void create$stop(CallbackInfo ci) { ClientGameTestCompletionReport.onClientStop(); }
}
