package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BucketItem.class)
public abstract class DeployerBucketMixin {
    @Inject(method="emptyContents",at=@At("HEAD"),cancellable=true)
    private void create$containBucketPlacement(LivingEntity actor,Level level,BlockPos pos,BlockHitResult hit,CallbackInfoReturnable<Boolean> callback){
        if(actor instanceof DeployerFakePlayer hand&&hand.acting&&(!pos.equals(hand.workTarget)||!level.isLoaded(pos)))callback.setReturnValue(false);
    }
}
