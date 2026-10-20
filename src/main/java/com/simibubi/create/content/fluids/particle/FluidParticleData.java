package com.simibubi.create.content.fluids.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllParticleTypes;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Genuine source particle identity/payload, using component-aware native Fabric fluid units. */
public class FluidParticleData implements ParticleOptions {
    private ParticleType<FluidParticleData> type;
    private FluidStack fluid;

    private static final Codec<FluidStack> STACK_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        FluidVariant.CODEC.fieldOf("variant").forGetter(FluidStack::variant),
        Codec.LONG.fieldOf("amount").forGetter(FluidStack::getAmount)
    ).apply(instance, FluidStack::new));
    private static final StreamCodec<RegistryFriendlyByteBuf, FluidStack> STACK_STREAM_CODEC = StreamCodec.composite(
        FluidVariant.PACKET_CODEC, FluidStack::variant,
        ByteBufCodecs.VAR_LONG, FluidStack::getAmount, FluidStack::new);

    public FluidParticleData() {}

    @SuppressWarnings("unchecked")
    public FluidParticleData(ParticleType<?> type, FluidStack fluid) {
        this.type = (ParticleType<FluidParticleData>) type;
        this.fluid = fluid;
    }

    @Override public ParticleType<FluidParticleData> getType() { return type; }
    public FluidStack getFluid() { return fluid; }

    public static final MapCodec<FluidParticleData> CODEC = STACK_CODEC.fieldOf("fluid")
        .xmap(stack -> new FluidParticleData(AllParticleTypes.FLUID_PARTICLE, stack), data -> data.fluid);
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidParticleData> STREAM_CODEC = STACK_STREAM_CODEC
        .map(stack -> new FluidParticleData(AllParticleTypes.FLUID_PARTICLE, stack), data -> data.fluid);
    public static final MapCodec<FluidParticleData> BASIN_CODEC = STACK_CODEC.fieldOf("fluid")
        .xmap(stack -> new FluidParticleData(AllParticleTypes.BASIN_FLUID, stack), data -> data.fluid);
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidParticleData> BASIN_STREAM_CODEC = STACK_STREAM_CODEC
        .map(stack -> new FluidParticleData(AllParticleTypes.BASIN_FLUID, stack), data -> data.fluid);
    public static final MapCodec<FluidParticleData> DRIP_CODEC = STACK_CODEC.fieldOf("fluid")
        .xmap(stack -> new FluidParticleData(AllParticleTypes.FLUID_DRIP, stack), data -> data.fluid);
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidParticleData> DRIP_STREAM_CODEC = STACK_STREAM_CODEC
        .map(stack -> new FluidParticleData(AllParticleTypes.FLUID_DRIP, stack), data -> data.fluid);

    public MapCodec<FluidParticleData> getCodec(ParticleType<FluidParticleData> type) {
        if (type == AllParticleTypes.BASIN_FLUID) return BASIN_CODEC;
        if (type == AllParticleTypes.FLUID_DRIP) return DRIP_CODEC;
        return CODEC;
    }

    public StreamCodec<? super RegistryFriendlyByteBuf, FluidParticleData> getStreamCodec() {
        if (type == AllParticleTypes.BASIN_FLUID) return BASIN_STREAM_CODEC;
        if (type == AllParticleTypes.FLUID_DRIP) return DRIP_STREAM_CODEC;
        return STREAM_CODEC;
    }
}
