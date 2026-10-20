package com.simibubi.create.foundation.mixin.accessor;

import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Installs only a prepared ordering of the same genuinely mounted native child objects. */
@Mixin(Entity.class)
public interface EntityPassengersAccessor {
    @Accessor("passengers") void create$setActualPassengerOrder(ImmutableList<Entity> passengers);
}
