package com.simibubi.create.content.trains.track;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.trains.graph.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.*;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.*;

/**
 * Actual source cubic railway geometry, with detached iteration and strict native codecs. World
 * placement/refunds/particles and client baked rail/girder transforms belong to the track layer.
 */
public class BezierConnection implements Iterable<BezierConnection.Segment> {
  private record Data(
      Couple<BlockPos> positions,
      Couple<Vec3> starts,
      Couple<Vec3> axes,
      Couple<Vec3> normals,
      boolean primary,
      boolean girder,
      TrackMaterial material,
      Optional<Couple<Integer>> smoothing) {}

  private static final Codec<Data> DATA =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      RailCodecs.couple(BlockPos.CODEC)
                          .fieldOf("Positions")
                          .forGetter(Data::positions),
                      RailCodecs.couple(RailCodecs.VECTOR)
                          .fieldOf("Starts")
                          .forGetter(Data::starts),
                      RailCodecs.couple(RailCodecs.VECTOR).fieldOf("Axes").forGetter(Data::axes),
                      RailCodecs.couple(RailCodecs.VECTOR)
                          .fieldOf("Normals")
                          .forGetter(Data::normals),
                      Codec.BOOL.fieldOf("Primary").forGetter(Data::primary),
                      Codec.BOOL.fieldOf("Girder").forGetter(Data::girder),
                      TrackMaterial.CODEC.fieldOf("Material").forGetter(Data::material),
                      RailCodecs.couple(Codec.intRange(-32768, 32767))
                          .optionalFieldOf("Smoothing")
                          .forGetter(Data::smoothing))
                  .apply(i, Data::new));
  public static final Codec<BezierConnection> CODEC =
      DATA.flatXmap(
          data -> {
            try {
              var curve =
                  new BezierConnection(
                      data.positions,
                      data.starts,
                      data.axes,
                      data.normals,
                      data.primary,
                      data.girder,
                      data.material);
              curve.smoothing = data.smoothing.orElse(null);
              return DataResult.success(curve);
            } catch (IllegalArgumentException invalid) {
              return DataResult.error(invalid::getMessage);
            }
          },
          curve ->
              DataResult.success(
                  new Data(
                      curve.bePositions,
                      curve.starts,
                      curve.axes,
                      curve.normals,
                      curve.primary,
                      curve.hasGirder,
                      curve.trackMaterial,
                      Optional.ofNullable(curve.smoothing))));
  public final Couple<BlockPos> bePositions;
  public final Couple<Vec3> starts, axes, normals;
  public Couple<Integer> smoothing;
  public final boolean primary, hasGirder;
  protected TrackMaterial trackMaterial;
  private final AtomicReference<Runtime> lazyRuntime = new AtomicReference<>();

  public BezierConnection(
      Couple<BlockPos> positions,
      Couple<Vec3> starts,
      Couple<Vec3> axes,
      Couple<Vec3> normals,
      boolean primary,
      boolean girder,
      TrackMaterial material) {
    bePositions = positions.map(BlockPos::immutable);
    this.starts = starts.copy();
    this.axes = axes.copy();
    this.normals = normals.copy();
    this.primary = primary;
    hasGirder = girder;
    trackMaterial = Objects.requireNonNull(material);
    if (!starts.both(RailCodecs::finite)
        || starts.getFirst().distanceToSqr(starts.getSecond()) < 1e-12)
      throw new IllegalArgumentException("Curve requires distinct finite endpoints");
    axes.forEach(RailCodecs::nonzero);
    normals.forEach(RailCodecs::nonzero);
  }

  public BezierConnection secondary() {
    var result =
        new BezierConnection(
            bePositions.swap(),
            starts.swap(),
            axes.swap(),
            normals.swap(),
            !primary,
            hasGirder,
            trackMaterial);
    if (smoothing != null) result.smoothing = smoothing.swap();
    return result;
  }

  @Override
  public BezierConnection clone() {
    var result =
        new BezierConnection(bePositions, starts, axes, normals, primary, hasGirder, trackMaterial);
    if (smoothing != null) result.smoothing = smoothing.copy();
    return result;
  }

  private static boolean near(Couple<?> a, Couple<?> b) {
    return Objects.equals(a.getFirst(), b.getFirst())
            && Objects.equals(a.getSecond(), b.getSecond())
        || a.getFirst() instanceof Vec3 af
            && a.getSecond() instanceof Vec3 as
            && b.getFirst() instanceof Vec3 bf
            && b.getSecond() instanceof Vec3 bs
            && af.closerThan(bf, 1e-6)
            && as.closerThan(bs, 1e-6);
  }

  public boolean equalsSansMaterial(BezierConnection other) {
    return other != null && (same(other) || same(other.secondary()));
  }

  private boolean same(BezierConnection other) {
    return this == other
        || near(bePositions, other.bePositions)
            && near(starts, other.starts)
            && near(axes, other.axes)
            && near(normals, other.normals)
            && hasGirder == other.hasGirder;
  }

  private static BlockPos legacyPos(CompoundTag entry) {
    return entry
        .read("Pos", BlockPos.CODEC)
        .orElseGet(
            () -> {
              var pos = entry.getCompoundOrEmpty("Pos");
              return new BlockPos(pos.getIntOr("X", 0), pos.getIntOr("Y", 0), pos.getIntOr("Z", 0));
            });
  }

  private static Vec3 legacyVector(CompoundTag entry) {
    var values = entry.getListOrEmpty("V");
    if (values.size() != 3) throw new IllegalArgumentException("Invalid source railway vector");
    return new Vec3(
        values.getDoubleOr(0, Double.NaN),
        values.getDoubleOr(1, Double.NaN),
        values.getDoubleOr(2, Double.NaN));
  }

  private static BezierConnection decodeTag(CompoundTag tag) {
    var positions = tag.getListOrEmpty("Positions");
    if (!positions.isEmpty() && positions.get(0) instanceof CompoundTag) {
      var result =
          new BezierConnection(
              RailCodecs.readCouple(tag, "Positions", BezierConnection::legacyPos),
              RailCodecs.readCouple(tag, "Starts", BezierConnection::legacyVector),
              RailCodecs.readCouple(tag, "Axes", BezierConnection::legacyVector),
              RailCodecs.readCouple(tag, "Normals", BezierConnection::legacyVector),
              tag.getBooleanOr("Primary", false),
              tag.getBooleanOr("Girder", false),
              TrackMaterial.deserialize(tag.getStringOr("Material", "")));
      if (tag.contains("Smoothing"))
        result.smoothing = RailCodecs.readCouple(tag, "Smoothing", entry -> entry.getIntOr("V", 0));
      return result;
    }
    return CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
  }

  private BezierConnection(BezierConnection relative, BlockPos localTo) {
    this(
        relative.bePositions.map(pos -> pos.offset(localTo)),
        relative.starts.map(vector -> vector.add(Vec3.atLowerCornerOf(localTo))),
        relative.axes,
        relative.normals,
        relative.primary,
        relative.hasGirder,
        relative.trackMaterial);
    smoothing = relative.smoothing == null ? null : relative.smoothing.copy();
  }

  public BezierConnection(CompoundTag tag, BlockPos localTo) {
    this(decodeTag(tag), localTo);
  }

  public CompoundTag write(BlockPos localTo) {
    var relative =
        new BezierConnection(
            bePositions.map(pos -> pos.subtract(localTo)),
            starts.map(vector -> vector.subtract(Vec3.atLowerCornerOf(localTo))),
            axes,
            normals,
            primary,
            hasGirder,
            trackMaterial);
    relative.smoothing = smoothing == null ? null : smoothing.copy();
    return (CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, relative).getOrThrow();
  }

  private static Vec3 readVector(FriendlyByteBuf buf) {
    return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
  }

  private static void writeVector(FriendlyByteBuf buf, Vec3 vector) {
    buf.writeDouble(vector.x);
    buf.writeDouble(vector.y);
    buf.writeDouble(vector.z);
  }

  public BezierConnection(FriendlyByteBuf buf) {
    this(
        Couple.create(buf::readBlockPos),
        Couple.create(() -> readVector(buf)),
        Couple.create(() -> readVector(buf)),
        Couple.create(() -> readVector(buf)),
        buf.readBoolean(),
        buf.readBoolean(),
        TrackMaterial.deserialize(buf.readUtf(256)));
    if (buf.readBoolean()) smoothing = Couple.create(buf::readVarInt);
  }

  public void write(FriendlyByteBuf buf) {
    bePositions.forEach(buf::writeBlockPos);
    starts.forEach(value -> writeVector(buf, value));
    axes.forEach(value -> writeVector(buf, value));
    normals.forEach(value -> writeVector(buf, value));
    buf.writeBoolean(primary);
    buf.writeBoolean(hasGirder);
    buf.writeUtf(trackMaterial.id.toString(), 256);
    buf.writeBoolean(smoothing != null);
    if (smoothing != null) smoothing.forEach(buf::writeVarInt);
  }

  public BlockPos getKey() {
    return bePositions.getSecond();
  }

  public boolean isPrimary() {
    return primary;
  }

  public int yOffsetAt(Vec3 end) {
    if (smoothing == null) return 0;
    for (boolean first : new boolean[] {true, false})
      if (new TrackNodeLocation(starts.get(first))
              .getLocation()
              .multiply(1, 0, 1)
              .distanceToSqr(new TrackNodeLocation(end).getLocation().multiply(1, 0, 1))
          < 1 / 512f) return smoothing.get(first);
    return 0;
  }

  public double getLength() {
    return resolve().length;
  }

  public float[] getStepLUT() {
    return resolve().stepLUT.clone();
  }

  public int getSegmentCount() {
    return resolve().segments;
  }

  public Vec3 getPosition(double t) {
    var runtime = resolve();
    return RailMath.bezier(
        starts.getFirst(), starts.getSecond(), runtime.finish1, runtime.finish2, (float) t);
  }

  public double getRadius() {
    return resolve().radius;
  }

  public double getHandleLength() {
    return resolve().handleLength;
  }

  public float getSegmentT(int index) {
    return resolve().getSegmentT(index);
  }

  public double incrementT(double currentT, double distance) {
    var runtime = resolve();
    double derivative =
        RailMath.bezierDerivative(
                    starts.getFirst(),
                    starts.getSecond(),
                    runtime.finish1,
                    runtime.finish2,
                    (float) currentT)
                .length()
            / getLength();
    return currentT + distance / Math.max(1e-12, derivative);
  }

  public AABB getBounds() {
    return resolve().bounds;
  }

  public Vec3 getNormal(double t) {
    var runtime = resolve();
    var derivative =
        RailMath.bezierDerivative(
                starts.getFirst(), starts.getSecond(), runtime.finish1, runtime.finish2, (float) t)
            .normalize();
    var a = normals.getFirst();
    var b = normals.getSecond();
    var face = a.equals(b) ? a : RailMath.slerp((float) t, a, b);
    return derivative.cross(face.cross(derivative).normalize());
  }

  private Runtime resolve() {
    var runtime = lazyRuntime.get();
    if (runtime == null) {
      runtime = new Runtime(starts, axes);
      lazyRuntime.compareAndSet(null, runtime);
    }
    return lazyRuntime.get();
  }

  @Override
  public Iterator<Segment> iterator() {
    return new Bezierator(
        this, Vec3.atLowerCornerOf(bePositions.getFirst()).scale(-1).add(0, 3 / 16f, 0));
  }

  public int getTrackItemCost() {
    return (getSegmentCount() + 1) / 2;
  }

  public int getGirderItemCost() {
    return hasGirder ? getTrackItemCost() * 2 : 0;
  }

  public TrackMaterial getMaterial() {
    return trackMaterial;
  }

  public void setMaterial(TrackMaterial material) {
    trackMaterial = Objects.requireNonNull(material);
  }

  private static class Runtime {
    private final Vec3 finish1;
    private final Vec3 finish2;
    private final double length;
    private final float[] stepLUT;
    private final int segments;

    private double radius;
    private double handleLength;

    private final AABB bounds;

    private Runtime(Couple<Vec3> starts, Couple<Vec3> axes) {
      Vec3 end1 = starts.getFirst();
      Vec3 end2 = starts.getSecond();
      Vec3 axis1 = axes.getFirst().normalize();
      Vec3 axis2 = axes.getSecond().normalize();

      determineHandles(end1, end2, axis1, axis2);

      finish1 = axis1.scale(handleLength).add(end1);
      finish2 = axis2.scale(handleLength).add(end2);

      int scanCount = 16;

      this.length = computeLength(finish1, finish2, end1, end2, scanCount);

      if (!Double.isFinite(length) || length <= 1e-8 || length * 2 > 65536)
        throw new IllegalArgumentException("Invalid or excessive source curve length");
      segments = Math.max(1, (int) (length * 2));
      stepLUT = new float[segments + 1];
      stepLUT[0] = 1;
      float combinedDistance = 0;

      AABB bounds = new AABB(end1, end2);

      // determine step lut
      {
        Vec3 previous = end1;
        for (int i = 0; i <= segments; i++) {
          float t = i / (float) segments;
          Vec3 result = RailMath.bezier(end1, end2, finish1, finish2, t);
          bounds = bounds.minmax(new AABB(result, result));
          if (i > 0) {
            combinedDistance += result.distanceTo(previous) / length;
            stepLUT[i] = (float) (t / combinedDistance);
          }
          previous = result;
        }
      }

      this.bounds = bounds.inflate(1.375f);
    }

    private static double computeLength(
        Vec3 finish1, Vec3 finish2, Vec3 end1, Vec3 end2, int scanCount) {
      double length = 0;

      Vec3 previous = end1;
      for (int i = 0; i <= scanCount; i++) {
        float t = i / (float) scanCount;
        Vec3 result = RailMath.bezier(end1, end2, finish1, finish2, t);
        if (previous != null) length += result.distanceTo(previous);
        previous = result;
      }
      return length;
    }

    public float getSegmentT(int index) {
      if (index < 0 || index > segments) throw new IndexOutOfBoundsException(index);
      return index == segments ? 1 : index * stepLUT[index] / segments;
    }

    private void determineHandles(Vec3 end1, Vec3 end2, Vec3 axis1, Vec3 axis2) {
      Vec3 cross1 = axis1.cross(new Vec3(0, 1, 0));
      Vec3 cross2 = axis2.cross(new Vec3(0, 1, 0));

      radius = 0;
      double a1 = Mth.atan2(-axis2.z, -axis2.x);
      double a2 = Mth.atan2(axis1.z, axis1.x);
      double angle = a1 - a2;

      float circle = 2 * Mth.PI;
      angle = (angle + circle) % circle;
      if (Math.abs(circle - angle) < Math.abs(angle)) angle = circle - angle;

      if (Mth.equal(angle, 0)) {
        double[] intersect = RailMath.intersect(end1, end2, axis1, cross2, Axis.Y);
        if (intersect != null) {
          double t = Math.abs(intersect[0]);
          double u = Math.abs(intersect[1]);
          double min = Math.min(t, u);
          double max = Math.max(t, u);

          if (min > 1.2 && max / min > 1 && max / min < 3) {
            handleLength = (max - min);
            return;
          }
        }

        handleLength = end2.distanceTo(end1) / 3;
        return;
      }

      double n = circle / angle;
      double factor = 4 / 3d * Math.tan(Math.PI / (2 * n));
      double[] intersect = RailMath.intersect(end1, end2, cross1, cross2, Axis.Y);

      if (intersect == null) {
        handleLength = end2.distanceTo(end1) / 3;
        return;
      }

      radius = Math.abs(intersect[1]);
      handleLength = radius * factor;
      if (Mth.equal(handleLength, 0)) handleLength = 1;
    }
  }

  public static class Segment {

    public int index;
    public Vec3 position;
    public Vec3 derivative;
    public Vec3 faceNormal;
    public Vec3 normal;
  }

  private static class Bezierator implements Iterator<Segment> {
    private final Segment segment;
    private final Vec3 end1;
    private final Vec3 end2;
    private final Vec3 finish1;
    private final Vec3 finish2;
    private final Vec3 faceNormal1;
    private final Vec3 faceNormal2;
    private final Runtime runtime;

    private Bezierator(BezierConnection bc, Vec3 offset) {
      runtime = bc.resolve();

      end1 = bc.starts.getFirst().add(offset);
      end2 = bc.starts.getSecond().add(offset);

      finish1 = bc.axes.getFirst().scale(runtime.handleLength).add(end1);
      finish2 = bc.axes.getSecond().scale(runtime.handleLength).add(end2);

      faceNormal1 = bc.normals.getFirst();
      faceNormal2 = bc.normals.getSecond();
      segment = new Segment();
      segment.index = -1; // will get incremented to 0 in #next()
    }

    @Override
    public boolean hasNext() {
      return segment.index + 1 <= runtime.segments;
    }

    @Override
    public Segment next() {
      if (!hasNext()) throw new NoSuchElementException();
      segment.index++;
      float t = runtime.getSegmentT(segment.index);
      segment.position = RailMath.bezier(end1, end2, finish1, finish2, t);
      segment.derivative = RailMath.bezierDerivative(end1, end2, finish1, finish2, t).normalize();
      segment.faceNormal =
          faceNormal1.equals(faceNormal2)
              ? faceNormal1
              : RailMath.slerp(t, faceNormal1, faceNormal2);
      segment.normal = segment.faceNormal.cross(segment.derivative).normalize();
      var result = new Segment();
      result.index = segment.index;
      result.position = segment.position;
      result.derivative = segment.derivative;
      result.faceNormal = segment.faceNormal;
      result.normal = segment.normal;
      return result;
    }
  }

  public Map<Pair<Integer, Integer>, Double> rasterise() {
    Map<Pair<Integer, Integer>, Double> yLevels = new HashMap<>();
    BlockPos tePosition = bePositions.getFirst();
    Vec3 end1 = starts.getFirst().subtract(Vec3.atLowerCornerOf(tePosition)).add(0, 3 / 16f, 0);
    Vec3 end2 = starts.getSecond().subtract(Vec3.atLowerCornerOf(tePosition)).add(0, 3 / 16f, 0);
    Vec3 axis1 = axes.getFirst();
    Vec3 axis2 = axes.getSecond();

    double handleLength = getHandleLength();
    Vec3 finish1 = axis1.scale(handleLength).add(end1);
    Vec3 finish2 = axis2.scale(handleLength).add(end2);

    Vec3 faceNormal1 = normals.getFirst();
    Vec3 faceNormal2 = normals.getSecond();

    int segCount = getSegmentCount();
    float[] lut = getStepLUT();
    Vec3[] samples = new Vec3[segCount];

    for (int i = 0; i < segCount; i++) {
      float t = Mth.clamp((i + 0.5f) * lut[i] / segCount, 0, 1);
      Vec3 result = RailMath.bezier(end1, end2, finish1, finish2, t);
      Vec3 derivative = RailMath.bezierDerivative(end1, end2, finish1, finish2, t).normalize();
      Vec3 faceNormal =
          faceNormal1.equals(faceNormal2)
              ? faceNormal1
              : RailMath.slerp(t, faceNormal1, faceNormal2);
      Vec3 normal = faceNormal.cross(derivative).normalize();
      Vec3 below = result.add(faceNormal.scale(-.25f));
      Vec3 rail1 = below.add(normal.scale(.05f));
      Vec3 rail2 = below.subtract(normal.scale(.05f));
      Vec3 railMiddle = rail1.add(rail2).scale(.5);
      samples[i] = railMiddle;
    }

    Vec3 center = end1.add(end2).scale(0.5);

    Pair<Integer, Integer> prev = null;
    Pair<Integer, Integer> prev2 = null;
    Pair<Integer, Integer> prev3 = null;

    for (int i = 0; i < segCount; i++) {
      Vec3 railMiddle = samples[i];
      BlockPos pos = BlockPos.containing(railMiddle);
      Pair<Integer, Integer> key = Pair.of(pos.getX(), pos.getZ());
      boolean alreadyPresent = yLevels.containsKey(key);
      if (alreadyPresent && yLevels.get(key) <= railMiddle.y) continue;
      yLevels.put(key, railMiddle.y);
      if (alreadyPresent) continue;

      if (prev3 != null) { // Remove obsolete pixels
        boolean doubledViaPrev = isLineDoubled(prev2, prev, key);
        boolean doubledViaPrev2 = isLineDoubled(prev3, prev2, prev);
        boolean prevCloser = diff(prev, center) > diff(prev2, center);

        if (doubledViaPrev2 && (!doubledViaPrev || !prevCloser)) {
          yLevels.remove(prev2);
          prev2 = prev;
          prev = key;
          continue;

        } else if (doubledViaPrev && doubledViaPrev2 && prevCloser) {
          yLevels.remove(prev);
          prev = key;
          continue;
        }
      }

      prev3 = prev2;
      prev2 = prev;
      prev = key;
    }

    return yLevels;
  }

  private double diff(Pair<Integer, Integer> pFrom, Vec3 to) {
    return to.distanceToSqr(pFrom.getFirst() + 0.5, to.y, pFrom.getSecond() + 0.5);
  }

  private boolean isLineDoubled(
      Pair<Integer, Integer> pFrom, Pair<Integer, Integer> pVia, Pair<Integer, Integer> pTo) {
    int diff1x = pVia.getFirst() - pFrom.getFirst();
    int diff1z = pVia.getSecond() - pFrom.getSecond();
    int diff2x = pTo.getFirst() - pVia.getFirst();
    int diff2z = pTo.getSecond() - pVia.getSecond();
    return Math.abs(diff1x) + Math.abs(diff1z) == 1
        && Math.abs(diff2x) + Math.abs(diff2z) == 1
        && diff1x != diff2x
        && diff1z != diff2z;
  }
}
