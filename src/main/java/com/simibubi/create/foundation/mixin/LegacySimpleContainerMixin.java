package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.legacy.ChromaticCompoundItem;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native copyAndClear otherwise discards an old compound's amount above its dynamic slot limit. */
@Mixin(SimpleContainer.class)
abstract class LegacySimpleContainerMixin {
    @Inject(method = "moveItemToEmptySlots", at = @At("HEAD"), cancellable = true)
    private void create$preserveFiniteDynamicCompound(ItemStack remainder, CallbackInfo ci) {
        if (!(remainder.getItem() instanceof ChromaticCompoundItem))
            return;
        var container = (SimpleContainer) (Object) this;
        for (int slot = 0; slot < container.getContainerSize() && !remainder.isEmpty(); slot++) {
            if (!container.getItem(slot).isEmpty())
                continue;
            int amount = Math.min(remainder.getCount(), container.getMaxStackSize(remainder));
            if (amount <= 0)
                break;
            container.setItem(slot, remainder.split(amount));
        }
        ci.cancel();
    }
}
