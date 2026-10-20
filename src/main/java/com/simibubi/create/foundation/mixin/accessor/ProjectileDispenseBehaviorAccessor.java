package com.simibubi.create.foundation.mixin.accessor;

import net.minecraft.core.dispenser.ProjectileDispenseBehavior;
import net.minecraft.world.item.ProjectileItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ProjectileDispenseBehavior.class)
public interface ProjectileDispenseBehaviorAccessor {
    @Accessor("projectileItem") ProjectileItem create$getProjectileItem();
    @Accessor("dispenseConfig") ProjectileItem.DispenseConfig create$getDispenseConfig();
}
