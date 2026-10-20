package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.decoration.copycat.CopycatMaterialHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only the source destruction-immunity decision is wrapped; native transparent/collision/gamerule logic stays native. */
@Mixin(EnderDragon.class)
abstract class CopycatDragonDestructionMixin {
    @WrapOperation(method="checkWalls",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean create$materialDragonImmunity(BlockState state,TagKey<Block> tag,Operation<Boolean> original,@Local BlockPos pos){return original.call(tag==BlockTags.DRAGON_IMMUNE?CopycatMaterialHooks.material(((EnderDragon)(Object)this).level(),pos,state):state,tag);}
}
