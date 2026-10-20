package com.simibubi.create.foundation.mixin.accessor;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Actual native canonical nine-slot list, after native getRandomSlot unpacked any real loot table. */
@Mixin(DispenserBlockEntity.class)
public interface DispenserInventoryAccessor {
    @Accessor("items") NonNullList<ItemStack> create$getItems();
}
