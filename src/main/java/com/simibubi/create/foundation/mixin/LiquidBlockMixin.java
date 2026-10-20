package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.fluids.FluidReactions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LiquidBlock.class)
abstract class LiquidBlockMixin {
    @Inject(method="onPlace",at=@At("TAIL"))
    private void create$placedFluid(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moved,CallbackInfo callback){FluidReactions.reactWorldLava(level,pos);}
    @Inject(method="neighborChanged",at=@At("TAIL"))
    private void create$neighbourFluid(BlockState state,Level level,BlockPos pos,Block block,Orientation orientation,boolean moved,CallbackInfo callback){FluidReactions.reactWorldLava(level,pos);}
}
