package com.simibubi.create.content.kinetics.steamEngine;

import com.mojang.serialization.*;
import com.simibubi.create.AllParticleTypes;
import net.minecraft.core.particles.*;
import net.minecraft.network.codec.*;

public record SteamJetParticleData(float speed) implements ParticleOptions {
    public static final MapCodec<SteamJetParticleData> CODEC=Codec.FLOAT.fieldOf("speed").xmap(SteamJetParticleData::new,SteamJetParticleData::speed);
    public static final StreamCodec<io.netty.buffer.ByteBuf,SteamJetParticleData> STREAM_CODEC=ByteBufCodecs.FLOAT.map(SteamJetParticleData::new,SteamJetParticleData::speed);
    @Override public ParticleType<?> getType(){return AllParticleTypes.STEAM_JET;}
}
