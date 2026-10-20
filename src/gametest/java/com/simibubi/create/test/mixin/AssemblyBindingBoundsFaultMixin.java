package com.simibubi.create.test.mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.test.AssemblyBindingTestFixtures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the actual ordinary source bounds callback; no acceptance or binding result is injected. */
@Mixin(AbstractContraptionEntity.class)
public abstract class AssemblyBindingBoundsFaultMixin {
    @Inject(method="updateStructureBounds",at=@At("HEAD"))
    private void create$actualOwnedBoundsBoundary(CallbackInfo ci){AssemblyBindingTestFixtures.actualBounds((AbstractContraptionEntity)(Object)this);}
}
