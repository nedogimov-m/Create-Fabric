package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.decoration.MetalScaffoldingBlock;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native26.3 hard-codes the vanilla identity here; retain source isScaffolding descent semantics. */
@Mixin(LivingEntity.class)
abstract class MetalScaffoldingClimbMixin {
    @WrapOperation(method="handleOnClimbable",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean create$metalScaffolding(BlockState state,Object tested,Operation<Boolean> original){
        return state.getBlock() instanceof MetalScaffoldingBlock || original.call(state,tested);
    }
}
