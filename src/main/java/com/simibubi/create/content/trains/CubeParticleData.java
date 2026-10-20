package com.simibubi.create.content.trains;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllParticleTypes;
import net.minecraft.core.particles.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;

/** Source cube color/size/lifetime/heat, encoded through genuine native particle registries. */
public record CubeParticleData(float r, float g, float b, float scale, int avgAge, boolean hot)
    implements ParticleOptions {
  public CubeParticleData {
    for (float value : new float[] {r, g, b})
      if (!Float.isFinite(value) || value < 0 || value > 1)
        throw new IllegalArgumentException("Invalid native cube particle color");
    if (!Float.isFinite(scale) || scale < 0 || scale > 64 || avgAge < 0 || avgAge > 65536)
      throw new IllegalArgumentException("Invalid native cube particle size/lifetime");
  }

  public static final MapCodec<CubeParticleData> CODEC =
      RecordCodecBuilder.mapCodec(
          i ->
              i.group(
                      Codec.floatRange(0, 1).fieldOf("r").forGetter(CubeParticleData::r),
                      Codec.floatRange(0, 1).fieldOf("g").forGetter(CubeParticleData::g),
                      Codec.floatRange(0, 1).fieldOf("b").forGetter(CubeParticleData::b),
                      Codec.floatRange(0, 64).fieldOf("scale").forGetter(CubeParticleData::scale),
                      Codec.intRange(0, 65536)
                          .fieldOf("avg_age")
                          .forGetter(CubeParticleData::avgAge),
                      Codec.BOOL.fieldOf("hot").forGetter(CubeParticleData::hot))
                  .apply(i, CubeParticleData::new));
  public static final StreamCodec<RegistryFriendlyByteBuf, CubeParticleData> STREAM_CODEC =
      StreamCodec.composite(
          ByteBufCodecs.FLOAT,
          CubeParticleData::r,
          ByteBufCodecs.FLOAT,
          CubeParticleData::g,
          ByteBufCodecs.FLOAT,
          CubeParticleData::b,
          ByteBufCodecs.FLOAT,
          CubeParticleData::scale,
          ByteBufCodecs.INT,
          CubeParticleData::avgAge,
          ByteBufCodecs.BOOL,
          CubeParticleData::hot,
          CubeParticleData::new);

  @Override
  public ParticleType<?> getType() {
    return AllParticleTypes.CUBE;
  }
}
