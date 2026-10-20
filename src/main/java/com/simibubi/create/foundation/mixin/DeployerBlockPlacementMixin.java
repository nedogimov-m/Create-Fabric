package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native placement must stay in the hand's work cell, even when its clicked block is solid. */
@Mixin(BlockItem.class)
public abstract class DeployerBlockPlacementMixin {
    @Inject(method="placeBlock",at=@At("HEAD"),cancellable=true)
    private void create$keepPlacementInWorkCell(BlockPlaceContext context,BlockState state,CallbackInfoReturnable<Boolean> callback){
        if(context.getPlayer() instanceof DeployerFakePlayer player&&player.acting&&player.workTarget!=null
                &&!context.getClickedPos().equals(player.workTarget))callback.setReturnValue(false);
    }
}
