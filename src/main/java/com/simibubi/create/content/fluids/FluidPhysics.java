package com.simibubi.create.content.fluids;

import com.simibubi.create.Create;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;

/** Physical immersion stays separate from water: these fluids neither extinguish nor waterlog. */
public final class FluidPhysics {
    public static final TagKey<Fluid> PLACEABLE=TagKey.create(Registries.FLUID,Create.asResource("placeable"));
    private FluidPhysics(){}
    public static boolean immersed(Entity entity){return entity.getFluidHeight(PLACEABLE)>0;}
    public static boolean eyesImmersed(Entity entity){return entity.isEyeInFluid(PLACEABLE);}
}
