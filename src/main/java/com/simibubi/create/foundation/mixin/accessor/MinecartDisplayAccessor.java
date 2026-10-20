package com.simibubi.create.foundation.mixin.accessor;

import java.util.Optional;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Preserve native optional customization without freezing a furnace cart's dynamic default display. */
@Mixin(AbstractMinecart.class)
public interface MinecartDisplayAccessor {
    @Invoker("getCustomDisplayBlockState") Optional<BlockState> create$getCustomDisplayBlockState();
}
