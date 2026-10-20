package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.decoration.copycat.CopycatMaterialHooks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ExplosionDamageCalculator.class)
abstract class CopycatExplosionResistanceMixin {
    @WrapMethod(method="getBlockExplosionResistance")
    private Optional<Float> create$materialResistance(Explosion explosion,BlockGetter level,BlockPos pos,BlockState state,FluidState fluid,Operation<Optional<Float>> original){return original.call(explosion,level,pos,CopycatMaterialHooks.material(level,pos,state),fluid);}
}
