package com.simibubi.create.content.fluids.particle;

import com.simibubi.create.AllParticleTypes;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.particle.ParticleProvider;

/** Actual native providers for all three original fluid particle identities. */
public final class FluidParticleProviders {
    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ParticleProvider<FluidParticleData> provider = (data, world, x, y, z, vx, vy, vz, random) ->
            FluidStackParticle.create(data.getType(), world, data.getFluid(), x, y, z, vx, vy, vz);
        var registry = ParticleProviderRegistry.getInstance();
        registry.register(AllParticleTypes.FLUID_PARTICLE, provider);
        registry.register(AllParticleTypes.BASIN_FLUID, provider);
        registry.register(AllParticleTypes.FLUID_DRIP, provider);
    }

    private FluidParticleProviders() {}
}
