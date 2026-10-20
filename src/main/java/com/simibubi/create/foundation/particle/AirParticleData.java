package com.simibubi.create.foundation.particle;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllParticleTypes;
import net.minecraft.core.particles.*;
import net.minecraft.network.codec.*;

public record AirParticleData(float drag,float speed) implements ParticleOptions {
    public static final MapCodec<AirParticleData> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(Codec.FLOAT.fieldOf("drag").forGetter(AirParticleData::drag),Codec.FLOAT.fieldOf("speed").forGetter(AirParticleData::speed)).apply(instance,AirParticleData::new));
    public static final StreamCodec<io.netty.buffer.ByteBuf,AirParticleData> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.FLOAT,AirParticleData::drag,ByteBufCodecs.FLOAT,AirParticleData::speed,AirParticleData::new);
    @Override public ParticleType<?> getType(){return AllParticleTypes.AIR;}
}
