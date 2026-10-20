package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.equipment.symmetryWand.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockItem.class)
public abstract class SymmetryBlockItemMixin {
    @WrapMethod(method="place")
    private InteractionResult create$symmetryAfterNativeConsumption(BlockPlaceContext context,Operation<InteractionResult> original){try(var capture=SymmetryHandler.capture(context)){var result=original.call(context);SymmetryHandler.completed(capture,result);return result;}}
    @WrapOperation(method="place",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/BlockItem;placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean create$captureActualNativePlacement(BlockItem item,BlockPlaceContext context,BlockState state,Operation<Boolean> original){boolean result=SymmetryPlacement.nativePlace(context,state,()->original.call(item,context,state));SymmetryHandler.placedByNative(context,state,result);return result;}
    @WrapOperation(method="getPlacementState",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;getStateForPlacement(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState create$transformedBeforeNativeValidation(Block block,BlockPlaceContext context,Operation<BlockState> original){return SymmetryPlacement.placementState(context,original.call(block,context));}
}
