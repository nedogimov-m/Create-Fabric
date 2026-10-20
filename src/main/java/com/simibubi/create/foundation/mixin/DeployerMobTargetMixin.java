package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pinned target-change policy on the actual native Mob target mutation. */
@Mixin(Mob.class)
abstract class DeployerMobTargetMixin {
    @Inject(method="setTarget",at=@At("HEAD"),cancellable=true)
    private void create$creepersIgnoreDeployers(LivingEntity target,CallbackInfo callback){if(!(target instanceof DeployerFakePlayer))return;var setting=com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.ignoreDeployerAttacks.get();if(setting==com.simibubi.create.infrastructure.config.CKinetics.DeployerAggroSetting.ALL||setting==com.simibubi.create.infrastructure.config.CKinetics.DeployerAggroSetting.CREEPERS&&(Object)this instanceof Creeper)callback.cancel();}
}
