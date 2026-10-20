package com.simibubi.create;

import com.simibubi.create.content.kinetics.steamEngine.SteamJetParticleData;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class AllParticleTypes {
    public static final net.minecraft.core.particles.SimpleParticleType SOUL=simple("soul"),SOUL_BASE=simple("soul_base"),SOUL_PERIMETER=simple("soul_perimeter"),SOUL_EXPANDING_PERIMETER=simple("soul_expanding_perimeter");
    private static net.minecraft.core.particles.SimpleParticleType simple(String name){return Registry.register(BuiltInRegistries.PARTICLE_TYPE,Create.asResource(name),FabricParticleTypes.simple());}
    public static final net.minecraft.core.particles.SimpleParticleType WIFI=Registry.register(BuiltInRegistries.PARTICLE_TYPE,Create.asResource("wifi"),FabricParticleTypes.simple());
    public static final ParticleType<SteamJetParticleData> STEAM_JET=Registry.register(BuiltInRegistries.PARTICLE_TYPE,Create.asResource("steam_jet"),FabricParticleTypes.complex(SteamJetParticleData.CODEC,SteamJetParticleData.STREAM_CODEC));
    public static final ParticleType<com.simibubi.create.content.kinetics.fan.AirFlowParticleData> AIR_FLOW=Registry.register(BuiltInRegistries.PARTICLE_TYPE,Create.asResource("air_flow"),FabricParticleTypes.complex(com.simibubi.create.content.kinetics.fan.AirFlowParticleData.CODEC,com.simibubi.create.content.kinetics.fan.AirFlowParticleData.STREAM_CODEC));
    public static final ParticleType<com.simibubi.create.foundation.particle.AirParticleData> AIR=Registry.register(BuiltInRegistries.PARTICLE_TYPE,Create.asResource("air"),FabricParticleTypes.complex(com.simibubi.create.foundation.particle.AirParticleData.CODEC,com.simibubi.create.foundation.particle.AirParticleData.STREAM_CODEC));
    public static final ParticleType<com.simibubi.create.content.trains.CubeParticleData> CUBE=Registry.register(BuiltInRegistries.PARTICLE_TYPE,Create.asResource("cube"),FabricParticleTypes.complex(com.simibubi.create.content.trains.CubeParticleData.CODEC,com.simibubi.create.content.trains.CubeParticleData.STREAM_CODEC));
    public static final ParticleType<com.simibubi.create.content.kinetics.base.RotationIndicatorParticleData> ROTATION_INDICATOR=Registry.register(BuiltInRegistries.PARTICLE_TYPE,Create.asResource("rotation_indicator"),FabricParticleTypes.complex(com.simibubi.create.content.kinetics.base.RotationIndicatorParticleData.CODEC,com.simibubi.create.content.kinetics.base.RotationIndicatorParticleData.STREAM_CODEC));
    public static final ParticleType<com.simibubi.create.content.fluids.particle.FluidParticleData> FLUID_PARTICLE=Registry.register(BuiltInRegistries.PARTICLE_TYPE,Create.asResource("fluid_particle"),FabricParticleTypes.complex(com.simibubi.create.content.fluids.particle.FluidParticleData.CODEC,com.simibubi.create.content.fluids.particle.FluidParticleData.STREAM_CODEC));
    public static final ParticleType<com.simibubi.create.content.fluids.particle.FluidParticleData> BASIN_FLUID=Registry.register(BuiltInRegistries.PARTICLE_TYPE,Create.asResource("basin_fluid"),FabricParticleTypes.complex(com.simibubi.create.content.fluids.particle.FluidParticleData.BASIN_CODEC,com.simibubi.create.content.fluids.particle.FluidParticleData.BASIN_STREAM_CODEC));
    public static final ParticleType<com.simibubi.create.content.fluids.particle.FluidParticleData> FLUID_DRIP=Registry.register(BuiltInRegistries.PARTICLE_TYPE,Create.asResource("fluid_drip"),FabricParticleTypes.complex(com.simibubi.create.content.fluids.particle.FluidParticleData.DRIP_CODEC,com.simibubi.create.content.fluids.particle.FluidParticleData.DRIP_STREAM_CODEC));
    private AllParticleTypes(){}
    public static void register(){}
}
