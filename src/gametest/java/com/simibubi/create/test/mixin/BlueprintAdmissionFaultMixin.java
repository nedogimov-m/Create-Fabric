package com.simibubi.create.test.mixin;

import com.simibubi.create.content.equipment.blueprint.BlueprintDeliveryTestFixtures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bounded rejection at the actual native API, without a fake world or replacement stock. */
@Mixin(ServerLevel.class)
public abstract class BlueprintAdmissionFaultMixin {
 @Inject(method="addFreshEntity",at=@At("HEAD"),cancellable=true)
 private void create$blueprintReject(Entity entity,CallbackInfoReturnable<Boolean> callback){if(BlueprintDeliveryTestFixtures.before((ServerLevel)(Object)this,entity))callback.setReturnValue(false);}
}
