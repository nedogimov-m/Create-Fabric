package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.decoration.copycat.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Leashable.class)
interface CopycatLeashFrictionMixin {
    @WrapOperation(method="angularFriction",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private static float create$materialLeashFriction(Block block,Operation<Float> original,Entity entity){return original.call(CopycatMaterialHooks.frictionBlock(entity,block));}
}
