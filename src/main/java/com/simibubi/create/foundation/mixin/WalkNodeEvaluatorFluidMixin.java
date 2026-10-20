package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.fluids.FluidPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WalkNodeEvaluator.class)
abstract class WalkNodeEvaluatorFluidMixin {
    @Inject(method="getPathTypeFromState",at=@At("HEAD"),cancellable=true)
    private static void create$fluidPath(BlockGetter level,BlockPos pos,CallbackInfoReturnable<PathType> callback){
        var state=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,pos);
        if(state.getBlock() instanceof com.simibubi.create.content.contraptions.actors.seat.SeatBlock)callback.setReturnValue(PathType.RAIL);
        else if(state.getFluidState().is(FluidPhysics.PLACEABLE))callback.setReturnValue(PathType.WATER);
    }
}
