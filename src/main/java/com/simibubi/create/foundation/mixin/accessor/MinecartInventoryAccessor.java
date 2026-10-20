package com.simibubi.create.foundation.mixin.accessor;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Real native backing replacement is restricted by the caller's cart-forest commit proof. */
@Mixin(AbstractMinecartContainer.class)
public interface MinecartInventoryAccessor {
    @Accessor("itemStacks")
    void create$setItemStacks(NonNullList<ItemStack> items);
}
