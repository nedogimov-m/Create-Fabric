package com.simibubi.create.test.mixin;

import com.simibubi.create.test.PortalFrameFaultFixtures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Test-only interruption after an actual native world write; no altered readiness/result flags. */
@Mixin(Level.class)
public abstract class PortalFrameWriteFaultMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("RETURN"))
    private void create$afterActualFrameWrite(BlockPos pos,BlockState state,int flags,int depth,CallbackInfoReturnable<Boolean> cir){PortalFrameFaultFixtures.afterNativeWrite((Level)(Object)this,pos);}
}
