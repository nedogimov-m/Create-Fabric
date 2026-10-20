package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.decoration.copycat.CopycatMaterialHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve actual native sound/volume/pitch decisions; resolve only the queried Copycat sound. */
@Mixin({Entity.class,AbstractHorse.class})
abstract class CopycatStepSoundMixin {
    @WrapOperation(method="playStepSound",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType create$materialStep(BlockState queried,Operation<SoundType> original,BlockPos pos,BlockState floor){return original.call(CopycatMaterialHooks.material(((Entity)(Object)this).level(),pos,queried));}
}
