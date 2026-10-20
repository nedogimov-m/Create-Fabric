package com.simibubi.create.content.trains.graph;

import com.mojang.serialization.*;
import java.util.*;
import java.util.function.*;
import net.createmod.catnip.data.Couple;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;

/** Shared strict finite geometry and source-shaped NBT, with no registry or world fallback. */
public final class RailCodecs {
  public static final Codec<Double> FINITE_DOUBLE =
      Codec.DOUBLE.validate(
          value ->
              Double.isFinite(value)
                  ? DataResult.success(value)
                  : DataResult.error(() -> "Nonfinite railway geometry"));
  public static final Codec<Vec3> VECTOR =
      Vec3.CODEC.validate(
          vector ->
              finite(vector)
                  ? DataResult.success(vector)
                  : DataResult.error(() -> "Nonfinite railway vector"));

  public static <T> Codec<Couple<T>> couple(Codec<T> codec) {
    return codec
        .listOf()
        .comapFlatMap(
            list ->
                list.size() == 2
                    ? DataResult.success(Couple.create(list.get(0), list.get(1)))
                    : DataResult.error(() -> "Expected exactly two railway endpoints"),
            pair -> List.of(pair.getFirst(), pair.getSecond()));
  }

  public static boolean finite(Vec3 value) {
    return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
  }

  public static Vec3 nonzero(Vec3 value) {
    if (!finite(value) || value.lengthSqr() < 1e-16)
      throw new IllegalArgumentException("Railway vector requires a finite nonzero direction");
    return value;
  }

  public static <T> ListTag writeCouple(Couple<T> values, Function<T, CompoundTag> encoder) {
    var list = new ListTag();
    values.forEach(value -> list.add(encoder.apply(value)));
    return list;
  }

  public static <T> Couple<T> readCouple(
      CompoundTag tag, String key, Function<CompoundTag, T> decoder) {
    var list = tag.getListOrEmpty(key);
    if (list.size() != 2
        || !(list.get(0) instanceof CompoundTag first)
        || !(list.get(1) instanceof CompoundTag second))
      throw new IllegalArgumentException("Expected railway endpoint pair: " + key);
    return Couple.create(decoder.apply(first), decoder.apply(second));
  }

  public static CompoundTag vector(Vec3 value) {
    var tag = new CompoundTag();
    tag.putDouble("X", value.x);
    tag.putDouble("Y", value.y);
    tag.putDouble("Z", value.z);
    return tag;
  }

  public static Vec3 vector(CompoundTag tag) {
    var value =
        new Vec3(
            tag.getDoubleOr("X", Double.NaN),
            tag.getDoubleOr("Y", Double.NaN),
            tag.getDoubleOr("Z", Double.NaN));
    if (!finite(value)) throw new IllegalArgumentException("Invalid railway vector");
    return value;
  }

  private RailCodecs() {}
}
