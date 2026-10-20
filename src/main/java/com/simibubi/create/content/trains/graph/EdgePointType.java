package com.simibubi.create.content.trains.graph;

import com.mojang.serialization.*;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

/**
 * Real generic point registry; a registered type supplies its complete native persistence codec.
 */
public final class EdgePointType<T extends TrackEdgePoint> {
  private static final Map<Identifier, EdgePointType<?>> MUTABLE = new LinkedHashMap<>();
  public static final Map<Identifier, EdgePointType<?>> TYPES =
      Collections.unmodifiableMap(MUTABLE);
  public static final Codec<EdgePointType<?>> CODEC =
      Identifier.CODEC.comapFlatMap(
          id -> {
            var type = MUTABLE.get(id);
            return type == null
                ? DataResult.error(() -> "Unknown railway edge point type " + id)
                : DataResult.success(type);
          },
          EdgePointType::getId);
  private final Identifier id;
  private final Supplier<T> factory;
  private final Codec<T> codec;

  private EdgePointType(Identifier id, Supplier<T> factory, Codec<T> codec) {
    this.id = id;
    this.factory = factory;
    this.codec =
        codec.xmap(
            point -> {
              point.setType(this);
              return point;
            },
            point -> point);
  }

  public static synchronized <T extends TrackEdgePoint> EdgePointType<T> register(
      Identifier id, Supplier<T> factory, Codec<T> codec) {
    if (MUTABLE.containsKey(id))
      throw new IllegalArgumentException("Duplicate railway edge point type " + id);
    var type =
        new EdgePointType<>(
            Objects.requireNonNull(id),
            Objects.requireNonNull(factory),
            Objects.requireNonNull(codec));
    MUTABLE.put(id, type);
    return type;
  }

  public T create() {
    var point = Objects.requireNonNull(factory.get());
    point.setType(this);
    return point;
  }

  public Identifier getId() {
    return id;
  }

  public Codec<T> codec() {
    return codec;
  }

  public static TrackEdgePoint read(FriendlyByteBuf buffer, DimensionPalette dimensions) {
    var type = TYPES.get(buffer.readIdentifier());
    if (type == null) throw new IllegalArgumentException("Unknown railway edge point type");
    var point = type.create();
    point.read(buffer, dimensions);
    return point;
  }
}
