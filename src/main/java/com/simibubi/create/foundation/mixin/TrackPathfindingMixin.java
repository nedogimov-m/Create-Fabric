package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Actual native replacement of source Track/FakeTrack getBlockPathType, retaining normal mob malus rules. */
@Mixin(WalkNodeEvaluator.class)
abstract class TrackPathfindingMixin {
    @Inject(method="getPathTypeFromState",at=@At("HEAD"),cancellable=true)
    private static void create$trackPathType(BlockGetter level,BlockPos pos,CallbackInfoReturnable<PathType> callback){var block=LoadedLevelAccess.blockState(level,pos).getBlock();if(block instanceof FakeTrackBlock)callback.setReturnValue(PathType.DAMAGING);else if(block instanceof ITrackBlock)callback.setReturnValue(PathType.RAIL);}
}
