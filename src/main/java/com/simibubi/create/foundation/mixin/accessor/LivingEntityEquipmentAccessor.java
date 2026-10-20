package com.simibubi.create.foundation.mixin.accessor;

import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Actual native gear backend for callback-free custody transfer at section insertion. */
@Mixin(LivingEntity.class)
public interface LivingEntityEquipmentAccessor {
    @Accessor("equipment") EntityEquipment create$getEquipment();
}
