package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryHandler;
import com.simibubi.create.content.trains.track.TrackBlockItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;

/** Connection use bypasses BlockItem.place; source snapshots are dispatched after native debit. */
@Mixin(TrackBlockItem.class)
abstract class SymmetryTrackUseMixin {
    @WrapMethod(method = "useOn")
    private InteractionResult create$afterActualConnectionDebit(UseOnContext context, Operation<InteractionResult> original) {
        // Ordinary unselected placement already has its own exact native BlockItem receipt.
        if (!context.getItemInHand().has(AllDataComponents.TRACK_CONNECTING_FROM))
            return original.call(context);
        try (var capture = SymmetryHandler.captureMultiUse(context)) {
            var result = original.call(context);
            SymmetryHandler.completedCustomUse(capture, result.consumesAction());
            return result;
        }
    }
}
