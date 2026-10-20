package com.simibubi.create.test.mixin;

import com.simibubi.create.test.CartAcceptanceTestFixtures;
import net.minecraft.world.level.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No acceptance is fabricated: only the actual native onCreated invocation is observed. */
@Mixin(PersistentEntitySectionManager.class)
public abstract class CartNativeCreationObservationMixin {
    @Inject(method="addEntity",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;addEntityUuid(Lnet/minecraft/world/level/entity/EntityAccess;)Z"))
    private void create$actualUuidCheck(EntityAccess actual,boolean loadedFromDisk,CallbackInfoReturnable<Boolean> ci){CartAcceptanceTestFixtures.atNativeUuidCheck(actual);}
    @Inject(method="addEntity",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/entity/LevelCallback;onCreated(Ljava/lang/Object;)V"))
    private void create$actualCreationBoundary(EntityAccess actual,boolean loadedFromDisk,CallbackInfoReturnable<Boolean> ci){CartAcceptanceTestFixtures.atNativeCallsite(actual);}
}
