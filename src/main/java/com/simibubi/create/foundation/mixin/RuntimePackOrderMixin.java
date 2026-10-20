package com.simibubi.create.foundation.mixin;

import net.minecraft.server.packs.repository.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

/** Newly discovered auto-enabled packs are appended by world loading, ignoring defaultPosition. */
@Mixin(PackRepository.class)
abstract class RuntimePackOrderMixin {
    @Inject(method = "rebuildSelected", at = @At("RETURN"), cancellable = true)
    private void create$runtimePackFirst(Collection<String> selected, CallbackInfoReturnable<List<Pack>> cir) {
        var packs = new ArrayList<>(cir.getReturnValue());
        var generated = packs.stream().filter(pack -> pack.getId().equals("create:runtime_generated")).findFirst().orElse(null);
        if (generated != null && packs.indexOf(generated) != 0) { packs.remove(generated); packs.addFirst(generated); cir.setReturnValue(List.copyOf(packs)); }
    }
}
