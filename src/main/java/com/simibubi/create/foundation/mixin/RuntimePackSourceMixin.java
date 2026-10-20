package com.simibubi.create.foundation.mixin;

import com.simibubi.create.foundation.pack.RuntimeDataPack;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Consumer;

@Mixin(BuiltInPackSource.class)
abstract class RuntimePackSourceMixin {
    @Shadow @Final private PackType packType;
    @Inject(method = "loadPacks", at = @At("TAIL"))
    private void create$runtimeData(Consumer<Pack> output, CallbackInfo ci) {
        if (packType == PackType.SERVER_DATA) output.accept(RuntimeDataPack.create());
    }
}
