package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.contraptions.glue.SuperGlueHandler;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class SuperGluePlacementMixin {
    @Inject(method="placeBlock",at=@At("HEAD"),cancellable=true)
    private void create$checkGlueSupport(BlockPlaceContext context,BlockState state,CallbackInfoReturnable<Boolean> ci){if(!SuperGlueHandler.canPlace(context))ci.setReturnValue(false);}
    @Inject(method="placeBlock",at=@At("RETURN"))
    private void create$autoGlue(BlockPlaceContext context,BlockState state,CallbackInfoReturnable<Boolean> ci){if(ci.getReturnValueZ())SuperGlueHandler.onBlockPlaced(context);}
}
