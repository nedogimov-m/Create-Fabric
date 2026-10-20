package com.simibubi.create.content.processing.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;

public record FluidOutput(Fluid fluid, long amount, DataComponentPatch components) {
    public static final Codec<FluidOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(FluidOutput::fluid),
            FluidIngredient.AMOUNT_CODEC.fieldOf("amount").forGetter(FluidOutput::amount),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(FluidOutput::components)
    ).apply(i, FluidOutput::new));
    public FluidVariant variant() { return FluidVariant.of(fluid, components); }
}
