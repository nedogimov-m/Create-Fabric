package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.decoration.copycat.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({ItemEntity.class,ExperienceOrb.class})
abstract class CopycatLooseEntityFrictionMixin {
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private float create$materialLooseFriction(Block block,Operation<Float> original){return original.call(CopycatMaterialHooks.frictionBlock((Entity)(Object)this,block));}
}
