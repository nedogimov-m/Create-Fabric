package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.contraptions.chassis.StickerBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Select the native solver's restitution from the actual contacted sticker orientation. */
@Mixin(Entity.class)
public abstract class StickerBouncinessMixin {
    @Inject(method="getBlockBounciness(Lnet/minecraft/world/level/block/Block;)D",at=@At("HEAD"),cancellable=true)
    private void create$directionalStickerRestitution(Block block,CallbackInfoReturnable<Double> callback){
        if(!(block instanceof StickerBlock))return;var entity=(Entity)(Object)this;var state=LoadedLevelAccess.blockState(entity.level(),entity.getOnPosLegacy());
        callback.setReturnValue(StickerBlock.isUpright(state)?entity instanceof LivingEntity?1d:.8d:0d);
    }
}
