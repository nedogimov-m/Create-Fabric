package com.simibubi.create.content.trains.graph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.trains.track.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Source half-block x/z identity, integral y and independent pixel smoothing, including dimension.
 */
public class TrackNodeLocation extends Vec3i {
  public ResourceKey<Level> dimension;
  public int yOffsetPixels;
  public static final Codec<TrackNodeLocation> CODEC =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      Codec.INT.fieldOf("X").forGetter(TrackNodeLocation::getX),
                      Codec.INT.fieldOf("Y").forGetter(TrackNodeLocation::getY),
                      Codec.INT.fieldOf("Z").forGetter(TrackNodeLocation::getZ),
                      DimensionPalette.DIMENSION
                          .fieldOf("Dimension")
                          .forGetter(value -> value.dimension),
                      Codec.intRange(-32768, 32767)
                          .optionalFieldOf("YO", 0)
                          .forGetter(value -> value.yOffsetPixels))
                  .apply(i, TrackNodeLocation::packed));

  public TrackNodeLocation(Vec3 value) {
    this(value.x, value.y, value.z);
  }

  public TrackNodeLocation(double x, double y, double z) {
    super((int) Math.round(x * 2), Mth.floor(y) * 2, (int) Math.round(z * 2));
    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
      throw new IllegalArgumentException("Nonfinite railway node");
  }

  private TrackNodeLocation(int x, int y, int z) {
    super(x, y, z);
  }

  private static TrackNodeLocation packed(
      int x, int y, int z, ResourceKey<Level> dimension, int pixels) {
    var value = new TrackNodeLocation(x, y, z);
    value.dimension = Objects.requireNonNull(dimension);
    value.yOffsetPixels = pixels;
    return value;
  }

  public static TrackNodeLocation from(Vec3 vector) {
    return new TrackNodeLocation(vector);
  }

  public TrackNodeLocation in(Level level) {
    return in(level.dimension());
  }

  public TrackNodeLocation in(ResourceKey<Level> dimension) {
    this.dimension = Objects.requireNonNull(dimension);
    return this;
  }

  public TrackNodeLocation copy() {
    return packed(getX(), getY(), getZ(), dimension, yOffsetPixels);
  }

  public Vec3 getLocation() {
    return new Vec3(getX() / 2d, getY() / 2d + yOffsetPixels / 16d, getZ() / 2d);
  }

  public ResourceKey<Level> getDimension() {
    return dimension;
  }

  @Override
  public boolean equals(Object other) {
    return equalsIgnoreDim(other)
        && other instanceof TrackNodeLocation location
        && Objects.equals(dimension, location.dimension);
  }

  public boolean equalsIgnoreDim(Object other) {
    return other instanceof TrackNodeLocation location
        && super.equals(other)
        && yOffsetPixels == location.yOffsetPixels;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getX(), getY(), getZ(), yOffsetPixels, dimension);
  }

  public CompoundTag write(DimensionPalette dimensions) {
    var tag = new CompoundTag();
    tag.store("Pos", BlockPos.CODEC, new BlockPos(getX(), getY(), getZ()));
    if (dimensions != null) tag.putInt("D", dimensions.encode(dimension));
    else if (dimension != null) tag.putString("Dimension", dimension.identifier().toString());
    if (yOffsetPixels != 0) tag.putInt("YO", yOffsetPixels);
    return tag;
  }

  public static TrackNodeLocation read(CompoundTag tag, DimensionPalette dimensions) {
    var pos =
        tag.read("Pos", BlockPos.CODEC)
            .orElseGet(
                () -> {
                  var legacy = tag.getCompoundOrEmpty("Pos");
                  return new BlockPos(
                      legacy.getIntOr("X", tag.getIntOr("X", 0)),
                      legacy.getIntOr("Y", tag.getIntOr("Y", 0)),
                      legacy.getIntOr("Z", tag.getIntOr("Z", 0)));
                });
    var dimension =
        dimensions != null
            ? dimensions.decode(tag.getIntOr("D", -1))
            : net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.Identifier.parse(
                    tag.getStringOr("Dimension", "minecraft:overworld")));
    return packed(pos.getX(), pos.getY(), pos.getZ(), dimension, tag.getIntOr("YO", 0));
  }

  public void send(FriendlyByteBuf buf, DimensionPalette dimensions) {
    buf.writeVarInt(getX());
    buf.writeInt(getY());
    buf.writeVarInt(getZ());
    buf.writeVarInt(yOffsetPixels);
    buf.writeVarInt(dimensions.encode(dimension));
  }

  public static TrackNodeLocation receive(FriendlyByteBuf buf, DimensionPalette dimensions) {
    int x = buf.readVarInt(), y = buf.readInt(), z = buf.readVarInt(), pixels = buf.readVarInt();
    if (pixels < -32768 || pixels > 32767)
      throw new IllegalArgumentException("Invalid railway smoothing offset");
    return packed(x, y, z, dimensions.decode(buf.readVarInt()), pixels);
  }

  public Collection<BlockPos> allAdjacent() {
    var result = new LinkedHashSet<BlockPos>();
    var origin = getLocation().subtract(0, yOffsetPixels / 16d, 0);
    for (int x : new int[] {-1, 1})
      for (int y : new int[] {-1, 1})
        for (int z : new int[] {-1, 1})
          result.add(BlockPos.containing(origin.add(x / 8d, y / 8d, z / 8d)));
    return result;
  }

  public static final class DiscoveredLocation extends TrackNodeLocation {
    BezierConnection turn;
    boolean forceNode;
    Vec3 direction;
    Vec3 normal = new Vec3(0, 1, 0);
    TrackMaterial materialA = TrackMaterial.ANDESITE, materialB = TrackMaterial.ANDESITE;

    public DiscoveredLocation(Level level, double x, double y, double z) {
      super(x, y, z);
      in(level);
    }

    public DiscoveredLocation(ResourceKey<Level> dimension, Vec3 vector) {
      super(vector);
      in(dimension);
    }

    public DiscoveredLocation(Level level, Vec3 vector) {
      this(level.dimension(), vector);
    }

    public DiscoveredLocation materialA(TrackMaterial material) {
      materialA = Objects.requireNonNull(material);
      return this;
    }

    public DiscoveredLocation materialB(TrackMaterial material) {
      materialB = Objects.requireNonNull(material);
      return this;
    }

    public DiscoveredLocation materials(TrackMaterial first, TrackMaterial second) {
      return materialA(first).materialB(second);
    }

    public TrackMaterial getMaterialA() {
      return materialA;
    }

    public TrackMaterial getMaterialB() {
      return materialB;
    }

    public DiscoveredLocation viaTurn(BezierConnection connection) {
      turn = connection;
      if (connection != null) forceNode();
      return this;
    }

    public BezierConnection getTurn() {
      return turn;
    }

    public boolean connectedViaTurn() {
      return turn != null;
    }

    public DiscoveredLocation withNormal(Vec3 normal) {
      this.normal = RailCodecs.nonzero(normal).normalize();
      return this;
    }

    public Vec3 getNormal() {
      return normal;
    }

    public DiscoveredLocation withDirection(Vec3 direction) {
      this.direction = direction == null ? null : RailCodecs.nonzero(direction);
      return this;
    }

    public Vec3 getDirection() {
      return direction;
    }

    public DiscoveredLocation withYOffset(int pixels) {
      if (pixels < -32768 || pixels > 32767)
        throw new IllegalArgumentException("Invalid railway smoothing");
      yOffsetPixels = pixels;
      return this;
    }

    public DiscoveredLocation forceNode() {
      forceNode = true;
      return this;
    }

    public boolean shouldForceNode() {
      return forceNode;
    }

    public boolean differentMaterials() {
      return materialA != materialB;
    }

    public boolean notInLineWith(Vec3 axis) {
      return direction != null && Math.abs(axis.normalize().dot(direction.normalize())) < 7 / 8f;
    }
  }
}
