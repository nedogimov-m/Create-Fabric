package com.simibubi.create.foundation.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemEntity.class)
public interface ItemEntityPickupAccessor {
    @Accessor("pickupDelay") int create$pickupDelay();
}
