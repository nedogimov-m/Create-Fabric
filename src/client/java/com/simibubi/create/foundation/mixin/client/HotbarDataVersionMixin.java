package com.simibubi.create.foundation.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.DataFixer;
import net.minecraft.client.HotbarManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The vanilla hotbar schema contains item lists; its version envelope is not a tenth hotbar. */
@Mixin(HotbarManager.class)
public abstract class HotbarDataVersionMixin {
    @WrapOperation(method = "load", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/util/datafix/DataFixTypes;updateToCurrentVersion(Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/nbt/CompoundTag;I)Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag create$fixHotbarContents(DataFixTypes type, DataFixer fixer, CompoundTag saved, int version,
            Operation<CompoundTag> original) {
        var contents = saved.copy();
        contents.remove("DataVersion");
        return original.call(type, fixer, contents, version);
    }
}
