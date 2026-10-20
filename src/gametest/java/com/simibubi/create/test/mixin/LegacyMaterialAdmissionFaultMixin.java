package com.simibubi.create.test.mixin;

import com.simibubi.create.content.legacy.LegacyMaterialTestFixtures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class LegacyMaterialAdmissionFaultMixin {
 @Inject(method="addFreshEntity",at=@At("HEAD"),cancellable=true)
 private void create$legacyReject(Entity entity,CallbackInfoReturnable<Boolean> callback){if(LegacyMaterialTestFixtures.before((ServerLevel)(Object)this,entity))callback.setReturnValue(false);}
}
