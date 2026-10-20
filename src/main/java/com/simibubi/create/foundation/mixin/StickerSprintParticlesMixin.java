package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.contraptions.chassis.StickerBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.particles.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native replacement for source addRunningEffects; cancels the default block particle only for UP. */
@Mixin(Entity.class)
public abstract class StickerSprintParticlesMixin {
    @Inject(method="spawnSprintParticle()V",at=@At("HEAD"),cancellable=true)
    private void create$uprightStickerRunningParticles(CallbackInfo callback){
        var entity=(Entity)(Object)this;var pos=entity.getOnPosLegacy();if(!StickerBlock.isUpright(LoadedLevelAccess.blockState(entity.level(),pos)))return;
        var velocity=entity.getDeltaMovement();var random=entity.getRandom();entity.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.SLIME_BLOCK.defaultBlockState()),entity.getX()+(random.nextFloat()-.5)*entity.getBbWidth(),entity.getY()+.1,entity.getZ()+(random.nextFloat()-.5)*entity.getBbWidth(),velocity.x*-4,1.5,velocity.z*-4);callback.cancel();
    }
}
