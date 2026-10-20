package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.decoration.copycat.CopycatMaterialHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WitherSkull.class)
abstract class CopycatWitherSkullResistanceMixin {
    @WrapOperation(method="getBlockExplosionResistance",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/boss/wither/WitherBoss;canDestroy(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean create$materialSkullImmunity(BlockState queried,Operation<Boolean> original,Explosion explosion,BlockGetter level,BlockPos pos,BlockState state,FluidState fluid,float resistance){return original.call(CopycatMaterialHooks.material(level,pos,queried));}
}
