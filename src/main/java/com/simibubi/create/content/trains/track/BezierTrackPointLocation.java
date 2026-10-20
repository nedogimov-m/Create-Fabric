package com.simibubi.create.content.trains.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Source half-block curve segment targeting, bounded before it can address graph geometry. */
public record BezierTrackPointLocation(BlockPos curveTarget, int segment) {
  public BezierTrackPointLocation {
    curveTarget = curveTarget.immutable();
    if (segment < 0 || segment >= 65536)
      throw new IllegalArgumentException("Invalid railway curve segment");
  }

  public static final Codec<BezierTrackPointLocation> CODEC =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      BlockPos.CODEC
                          .fieldOf("curve_target")
                          .forGetter(BezierTrackPointLocation::curveTarget),
                      Codec.intRange(0, 65535)
                          .fieldOf("segment")
                          .forGetter(BezierTrackPointLocation::segment))
                  .apply(i, BezierTrackPointLocation::new));
  public static final StreamCodec<ByteBuf, BezierTrackPointLocation> STREAM_CODEC =
      StreamCodec.composite(
          BlockPos.STREAM_CODEC,
          BezierTrackPointLocation::curveTarget,
          ByteBufCodecs.INT,
          BezierTrackPointLocation::segment,
          BezierTrackPointLocation::new);
}
