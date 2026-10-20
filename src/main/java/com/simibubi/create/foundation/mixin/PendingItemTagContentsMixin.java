package com.simibubi.create.foundation.mixin;

import com.simibubi.create.foundation.data.recipe.PendingItemTagContents;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MappedRegistry.class)
abstract class PendingItemTagContentsMixin<T> {
    @Inject(method = "prepareTagReload", at = @At("RETURN"))
    private void create$capturePendingItemTags(TagLoader.LoadResult<T> loaded,
                                              CallbackInfoReturnable<Registry.PendingTags<T>> callback) {
        PendingItemTagContents.capture(loaded, callback.getReturnValue());
    }
}
