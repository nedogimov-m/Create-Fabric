package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.decoration.copycat.CopycatMaterialHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(LivingEntity.class)
abstract class CopycatLivingSurfaceMixin {
    @WrapOperation(method="travelInAir",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private float create$materialFriction(Block block,Operation<Float> original){return original.call(CopycatMaterialHooks.frictionBlock((LivingEntity)(Object)this,block));}
    @WrapOperation(method="playBlockFallSound",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState create$materialFallSound(Level level,BlockPos pos,Operation<BlockState> original){return CopycatMaterialHooks.material(level,pos,original.call(level,pos));}
    @ModifyArg(method="checkFallDamage(DZLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V",at=@At(value="INVOKE",target="Lnet/minecraft/core/particles/BlockParticleOption;<init>(Lnet/minecraft/core/particles/ParticleType;Lnet/minecraft/world/level/block/state/BlockState;)V"),index=1)
    private BlockState create$materialLanding(BlockState state,@Local(argsOnly=true) BlockPos pos){return CopycatMaterialHooks.material(((LivingEntity)(Object)this).level(),pos,state);}
}
