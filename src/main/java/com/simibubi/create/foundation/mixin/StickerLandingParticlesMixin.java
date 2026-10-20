package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.contraptions.chassis.StickerBlock;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Replace only the landing particle's material, preserving native fall-power/count/position calculation. */
@Mixin(LivingEntity.class)
public abstract class StickerLandingParticlesMixin {
    @ModifyArg(method="checkFallDamage(DZLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V",at=@At(value="INVOKE",target="Lnet/minecraft/core/particles/BlockParticleOption;<init>(Lnet/minecraft/core/particles/ParticleType;Lnet/minecraft/world/level/block/state/BlockState;)V"),index=1)
    private BlockState create$uprightStickerLandingMaterial(BlockState state){return StickerBlock.isUpright(state)?Blocks.SLIME_BLOCK.defaultBlockState():state;}
}
