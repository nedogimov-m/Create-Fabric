package com.simibubi.create.foundation.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.decoration.copycat.CopycatMaterialHooks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
abstract class CopycatBreakingSoundMixin {
    @WrapOperation(method="playBreakingSound",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType create$materialBreakingSound(BlockState queried,Operation<SoundType> original,BlockPos pos,BlockState state){return original.call(CopycatMaterialHooks.material((ClientLevel)(Object)this,pos,queried));}
}
