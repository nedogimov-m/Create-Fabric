package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.contraptions.mounted.CartAssemblerBlockItem;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryHandler;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;

/** Source custom-use bypasses BlockItem.place; mirror only its real accepted native section write. */
@Mixin(CartAssemblerBlockItem.class)
abstract class SymmetryCartAssemblerUseMixin {
    @WrapMethod(method = "tryPlaceAssembler")
    private boolean create$afterActualSourceDebit(UseOnContext context, Operation<Boolean> original) {
        try (var capture = SymmetryHandler.captureCustomUse(context)) {
            boolean result = original.call(context);
            SymmetryHandler.completedCustomUse(capture, result);
            return result;
        }
    }
}
