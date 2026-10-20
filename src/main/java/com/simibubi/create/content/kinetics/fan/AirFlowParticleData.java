package com.simibubi.create.content.kinetics.fan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllParticleTypes;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Source fan position payload; particle factories live exclusively in the native client source set. */
public class AirFlowParticleData implements ParticleOptions {
    public static final MapCodec<AirFlowParticleData> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(
        Codec.INT.fieldOf("x").forGetter(data->data.posX),
        Codec.INT.fieldOf("y").forGetter(data->data.posY),
        Codec.INT.fieldOf("z").forGetter(data->data.posZ)).apply(instance,AirFlowParticleData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,AirFlowParticleData> STREAM_CODEC=StreamCodec.composite(
        ByteBufCodecs.INT,data->data.posX,ByteBufCodecs.INT,data->data.posY,ByteBufCodecs.INT,data->data.posZ,
        AirFlowParticleData::new);

    final int posX,posY,posZ;
    public AirFlowParticleData(Vec3i pos) { this(pos.getX(),pos.getY(),pos.getZ()); }
    public AirFlowParticleData(int posX,int posY,int posZ) { this.posX=posX;this.posY=posY;this.posZ=posZ; }
    public AirFlowParticleData() { this(0,0,0); }
    @Override public ParticleType<?> getType() { return AllParticleTypes.AIR_FLOW; }
    public MapCodec<AirFlowParticleData> getCodec(ParticleType<AirFlowParticleData> type) { return CODEC; }
    public StreamCodec<? super RegistryFriendlyByteBuf,AirFlowParticleData> getStreamCodec() { return STREAM_CODEC; }
}
