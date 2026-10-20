package com.simibubi.create.api.effect;

import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;

/** Behaviour for committed fluid spilling out of an open pipe. Amounts use Fabric units. */
@FunctionalInterface
public interface OpenPipeEffectHandler {
    SimpleRegistry<Fluid,OpenPipeEffectHandler> REGISTRY=SimpleRegistry.create();
    void apply(Level level,AABB area,FluidStack fluid);
}
