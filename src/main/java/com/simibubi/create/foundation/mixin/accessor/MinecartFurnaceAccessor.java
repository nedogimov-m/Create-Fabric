package com.simibubi.create.foundation.mixin.accessor;

import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;

/** Native26.3 remaining fuel; the original public push Vec3 is used directly. */
@Mixin(MinecartFurnace.class)
public interface MinecartFurnaceAccessor {
    @Accessor("fuel") int create$getFuel();
    @Accessor("fuel") void create$setFuel(int fuel);
    @Invoker("setHasFuel") void create$setHasFuel(boolean fuel);
}
