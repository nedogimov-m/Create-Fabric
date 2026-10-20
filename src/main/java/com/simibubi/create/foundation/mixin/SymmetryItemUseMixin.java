package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryHandler;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;

/** Pinned all-item-use source event, after actual native item debit and component side effects. */
@Mixin(ItemStack.class)
abstract class SymmetryItemUseMixin {
    @WrapMethod(method = "useOn")
    private InteractionResult create$afterActualItemUse(UseOnContext context, Operation<InteractionResult> original) {
        try (var capture = SymmetryHandler.captureItemUse(context)) {
            var result = original.call(context);
            SymmetryHandler.completedItemUse(capture, result);
            return result;
        }
    }
}
