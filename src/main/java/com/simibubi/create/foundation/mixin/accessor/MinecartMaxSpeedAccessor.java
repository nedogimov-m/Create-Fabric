package com.simibubi.create.foundation.mixin.accessor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Actual native virtual getter, including Furnace's override and current server game rules. */
@Mixin(AbstractMinecart.class)
public interface MinecartMaxSpeedAccessor {
    @Invoker("getMaxSpeed") double create$getMaxSpeed(ServerLevel level);
}
