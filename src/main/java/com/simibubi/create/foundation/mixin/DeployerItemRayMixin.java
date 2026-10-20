package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.content.equipment.sandPaper.SandPaperItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class DeployerItemRayMixin {
    @Inject(method="getPlayerPOVHitResult",at=@At("HEAD"),cancellable=true)
    private static void create$limitActiveHandRay(Level level,Player player,ClipContext.Fluid fluid,CallbackInfoReturnable<BlockHitResult> callback){
        if(player instanceof DeployerFakePlayer deployer&&deployer.acting&&(player.getMainHandItem().getItem() instanceof BucketItem||player.getMainHandItem().getItem() instanceof SandPaperItem)){
            var start=player.getEyePosition();var end=start.add(player.getLookAngle().scale(1-2d/64));var hit=level.clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,fluid,player));
            if(hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS&&player.getMainHandItem().getItem() instanceof BucketItem bucket&&bucket.getContent()!=net.minecraft.world.level.material.Fluids.EMPTY){
                var facing=net.minecraft.core.Direction.getApproximateNearest(player.getLookAngle());var wall=deployer.workTarget.relative(facing);
                if(level.isLoaded(wall))hit=new BlockHitResult(end,facing.getOpposite(),wall,false);
            }
            callback.setReturnValue(hit);
        }
    }
}
