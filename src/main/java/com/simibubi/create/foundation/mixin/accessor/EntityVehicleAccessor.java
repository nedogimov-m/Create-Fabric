package com.simibubi.create.foundation.mixin.accessor;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Roll back only an unadmitted prospective vehicle pointer before native parent-list adoption. */
@Mixin(Entity.class)
public interface EntityVehicleAccessor {
    @Accessor("vehicle") void create$setProspectiveVehicle(Entity entity);
}
