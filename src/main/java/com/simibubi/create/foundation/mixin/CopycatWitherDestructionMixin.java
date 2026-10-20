package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.content.decoration.copycat.CopycatMaterialHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WitherBoss.class)
abstract class CopycatWitherDestructionMixin {
    @WrapOperation(method="customServerAiStep",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState create$captureWitherMaterial(ServerLevel level,BlockPos pos,Operation<BlockState> original,@Share("create$copycatMaterial") LocalRef<BlockState> material){var state=original.call(level,pos);material.set(CopycatMaterialHooks.material(level,pos,state));return state;}
    @WrapOperation(method="customServerAiStep",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/boss/wither/WitherBoss;canDestroy(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean create$materialWitherDestruction(BlockState state,Operation<Boolean> original,@Share("create$copycatMaterial") LocalRef<BlockState> material){return original.call(material.get()==null?state:material.get());}
}
