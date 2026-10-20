package com.simibubi.create.content.trains.graph;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.*;
import net.minecraft.world.level.Level;

/** Source dimension palette; invalid indices cannot silently become Overworld. */
public final class DimensionPalette {
  public static final Codec<ResourceKey<Level>> DIMENSION = ResourceKey.codec(Registries.DIMENSION);
  public static final Codec<DimensionPalette> CODEC =
      DIMENSION
          .listOf()
          .comapFlatMap(
              values -> {
                try {
                  return com.mojang.serialization.DataResult.success(new DimensionPalette(values));
                } catch (IllegalArgumentException invalid) {
                  return com.mojang.serialization.DataResult.error(invalid::getMessage);
                }
              },
              value -> List.copyOf(value.gatheredDims));
  public static final StreamCodec<FriendlyByteBuf, DimensionPalette> STREAM_CODEC =
      new StreamCodec<>() {
        public DimensionPalette decode(FriendlyByteBuf buf) {
          return receive(buf);
        }

        public void encode(FriendlyByteBuf buf, DimensionPalette palette) {
          palette.send(buf);
        }
      };
  private final List<ResourceKey<Level>> gatheredDims;

  public DimensionPalette() {
    gatheredDims = new ArrayList<>();
  }

  public DimensionPalette(List<ResourceKey<Level>> values) {
    if (new HashSet<>(values).size() != values.size() || values.size() > 4096)
      throw new IllegalArgumentException("Invalid railway dimension palette");
    gatheredDims = new ArrayList<>(values);
  }

  public int encode(ResourceKey<Level> dimension) {
    Objects.requireNonNull(dimension);
    int index = gatheredDims.indexOf(dimension);
    if (index < 0) {
      if (gatheredDims.size() >= 4096)
        throw new IllegalStateException("Railway dimension palette exhausted");
      index = gatheredDims.size();
      gatheredDims.add(dimension);
    }
    return index;
  }

  public ResourceKey<Level> decode(int index) {
    if (index < 0 || index >= gatheredDims.size())
      throw new IllegalArgumentException("Unknown railway dimension palette index " + index);
    return gatheredDims.get(index);
  }

  public void send(FriendlyByteBuf buf) {
    buf.writeInt(gatheredDims.size());
    for (var dimension : gatheredDims) buf.writeIdentifier(dimension.identifier());
  }

  public static DimensionPalette receive(FriendlyByteBuf buf) {
    int size = buf.readInt();
    if (size < 0 || size > 4096)
      throw new IllegalArgumentException("Invalid railway dimension count");
    var values = new ArrayList<ResourceKey<Level>>();
    for (int i = 0; i < size; i++)
      values.add(ResourceKey.create(Registries.DIMENSION, buf.readIdentifier()));
    return new DimensionPalette(values);
  }

  public void write(CompoundTag tag) {
    var list = new ListTag();
    for (var dimension : gatheredDims) {
      var entry = new CompoundTag();
      entry.putString("Id", dimension.identifier().toString());
      list.add(entry);
    }
    tag.put("DimensionPalette", list);
  }

  public static DimensionPalette read(CompoundTag tag) {
    var dimensions = new ArrayList<ResourceKey<Level>>();
    for (var value : tag.getListOrEmpty("DimensionPalette")) {
      if (!(value instanceof CompoundTag entry))
        throw new IllegalArgumentException("Invalid railway dimension entry");
      dimensions.add(
          ResourceKey.create(Registries.DIMENSION, Identifier.parse(entry.getStringOr("Id", ""))));
    }
    return new DimensionPalette(dimensions);
  }
}
