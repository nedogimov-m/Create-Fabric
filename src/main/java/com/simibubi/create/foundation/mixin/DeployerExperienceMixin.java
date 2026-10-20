package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Source deployer kills collect loot without producing player experience. */
@Mixin(LivingEntity.class)
abstract class DeployerExperienceMixin {
    @Inject(method="dropExperience",at=@At("HEAD"),cancellable=true)
    private void create$noDeployerExperience(ServerLevel level,Entity responsible,CallbackInfo callback){if(responsible instanceof DeployerFakePlayer)callback.cancel();}
}
