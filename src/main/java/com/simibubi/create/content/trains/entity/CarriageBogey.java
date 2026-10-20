package com.simibubi.create.content.trains.entity;

import static com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity.BOGEY_DATA_KEY;
import static com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity.BOGEY_STYLE_KEY;

import com.simibubi.create.AllBogeyStyles;
import com.simibubi.create.content.trains.bogey.*;
import com.simibubi.create.content.trains.graph.*;
import java.util.Objects;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.resources.*;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Actual source bogey wheel coordinates, frame/coupling math and complete native persistent data.
 * WIP: consumers require the genuine Carriage/Train/native entity classes, never facade types.
 */
public final class CarriageBogey {
  public static final StreamCodec<RegistryFriendlyByteBuf, CarriageBogey> STREAM_CODEC =
      StreamCodec.composite(
          AbstractBogeyBlock.STREAM_CODEC,
          bogey -> bogey.type,
          ByteBufCodecs.BOOL,
          bogey -> bogey.upsideDown,
          ByteBufCodecs.COMPOUND_TAG,
          bogey -> bogey.bogeyData,
          CarriageBogey::new);
  public static final String UPSIDE_DOWN_KEY = "UpsideDown";
  public Carriage carriage;
  boolean isLeading;
  public CompoundTag bogeyData;
  final AbstractBogeyBlock<?> type;
  final boolean upsideDown;
  final Couple<TravellingPoint> points;
  final LerpedFloat wheelAngle = LerpedFloat.angular(),
      yaw = LerpedFloat.angular(),
      pitch = LerpedFloat.angular();
  public final Couple<Vec3> couplingAnchors = Couple.create(null, null);
  final int derailAngle = RandomSource.create().nextInt(60) - 30;

  public CarriageBogey(AbstractBogeyBlock<?> type, boolean upsideDown, CompoundTag data) {
    this(type, upsideDown, data, new TravellingPoint(), new TravellingPoint());
  }

  public CarriageBogey(
      AbstractBogeyBlock<?> type,
      boolean upsideDown,
      CompoundTag data,
      TravellingPoint first,
      TravellingPoint second) {
    this.type = Objects.requireNonNull(type);
    this.upsideDown = type.canBeUpsideDown() && upsideDown;
    Objects.requireNonNull(first).upsideDown = this.upsideDown;
    Objects.requireNonNull(second).upsideDown = this.upsideDown;
    bogeyData = data == null || data.isEmpty() ? createBogeyData() : data.copy();
    bogeyData.putBoolean(UPSIDE_DOWN_KEY, this.upsideDown);
    points = Couple.create(first, second);
  }

  public @Nullable ResourceKey<Level> getDimension() {
    var first = leading();
    var second = trailing();
    if (first.edge == null
        || second.edge == null
        || first.edge.isInterDimensional()
        || second.edge.isInterDimensional()) return null;
    var a = first.node1.getLocation().dimension;
    var b = second.node1.getLocation().dimension;
    return a.equals(b) ? a : null;
  }

  public void updateAngles(CarriageContraptionEntity entity, double distanceMoved) {
    double angleDiff = 360 * distanceMoved / (Math.PI * 2 * type.getWheelRadius());
    float xRot = 0, yRot;
    if (leading().edge == null || carriage.train.derailed) yRot = -90 + entity.yaw - derailAngle;
    else if (!entity.level().dimension().equals(getDimension())) yRot = -90 + entity.yaw;
    else {
      var first = leading().getPosition(carriage.train.graph);
      var second = trailing().getPosition(carriage.train.graph);
      double x = first.x - second.x, y = first.y - second.y, z = first.z - second.z;
      yRot = AngleHelper.deg(Mth.atan2(z, x)) + 90;
      xRot = AngleHelper.deg(Math.atan2(y, Math.hypot(x, z)));
    }
    double next = (wheelAngle.getValue() - angleDiff) % 360;
    for (int iteration = 0; iteration < (entity.firstPositionUpdate ? 2 : 1); iteration++) {
      wheelAngle.setValue(next);
      pitch.setValue(xRot);
      yaw.setValue(-yRot);
    }
  }

  public TravellingPoint leading() {
    var point = points.getFirst();
    point.upsideDown = isUpsideDown();
    return point;
  }

  public TravellingPoint trailing() {
    var point = points.getSecond();
    point.upsideDown = isUpsideDown();
    return point;
  }

  public double getStress() {
    if (getDimension() == null || carriage.train.derailed) return 0;
    return type.getWheelPointSpacing()
        - leading()
            .getPosition(carriage.train.graph)
            .distanceTo(trailing().getPosition(carriage.train.graph));
  }

  public @Nullable Vec3 getAnchorPosition() {
    return getAnchorPosition(false);
  }

  public @Nullable Vec3 getAnchorPosition(boolean flipUpsideDown) {
    if (leading().edge == null || trailing().edge == null) return null;
    return leading()
        .getPosition(carriage.train.graph, flipUpsideDown)
        .add(trailing().getPosition(carriage.train.graph, flipUpsideDown))
        .scale(.5);
  }

  public void updateCouplingAnchor(
      Vec3 entityPos,
      float entityXRot,
      float entityYRot,
      int spacing,
      float partial,
      boolean leading) {
    boolean down = isUpsideDown(), leadingDown = carriage.leadingBogey().isUpsideDown();
    var offset = type.getConnectorAnchorOffset(down).multiply(1, 1, leading ? -1 : 1);
    offset = rotate(offset, pitch.getValue(partial), Axis.X);
    offset = rotate(offset, yaw.getValue(partial), Axis.Y);
    offset = rotate(offset, -entityYRot - 90, Axis.Y);
    offset = rotate(offset, entityXRot, Axis.X);
    offset = rotate(offset, -180, Axis.Y);
    offset = offset.add(0, 0, leading ? 0 : -spacing);
    offset = rotate(offset, 180, Axis.Y);
    offset = rotate(offset, -entityXRot, Axis.X);
    offset = rotate(offset, entityYRot + 90, Axis.Y);
    if (down != leadingDown) offset = offset.add(0, down ? -2 : 2, 0);
    couplingAnchors.set(leading, entityPos.add(offset));
  }

  static Vec3 rotate(Vec3 value, double degrees, Axis axis) {
    if (degrees == 0 || value == Vec3.ZERO) return value;
    float angle = (float) (degrees / 180f * Math.PI);
    double sin = Mth.sin(angle), cos = Mth.cos(angle);
    return switch (axis) {
      case X -> new Vec3(value.x, value.y * cos - value.z * sin, value.z * cos + value.y * sin);
      case Y -> new Vec3(value.x * cos + value.z * sin, value.y, value.z * cos - value.x * sin);
      case Z -> new Vec3(value.x * cos - value.y * sin, value.y * cos + value.x * sin, value.z);
    };
  }

  public CompoundTag write(DimensionPalette dimensions) {
    var result = new CompoundTag();
    result.putString("Type", BuiltInRegistries.BLOCK.getKey((Block) type).toString());
    result.put("Points", RailCodecs.writeCouple(points, point -> point.write(dimensions)));
    result.putBoolean(UPSIDE_DOWN_KEY, upsideDown);
    var data = bogeyData.copy();
    data.putBoolean(UPSIDE_DOWN_KEY, upsideDown);
    if (!data.contains(BOGEY_STYLE_KEY)) data.putString(BOGEY_STYLE_KEY, getStyle().id.toString());
    result.put(BOGEY_DATA_KEY, data);
    return result;
  }

  public static CarriageBogey read(
      CompoundTag data, TrackGraph graph, DimensionPalette dimensions) {
    var id = Identifier.parse(data.getStringOr("Type", ""));
    var actual = BuiltInRegistries.BLOCK.getValue(id);
    if (!(actual instanceof AbstractBogeyBlock<?> type))
      throw new IllegalArgumentException("Unknown actual carriage bogey block: " + id);
    var points =
        RailCodecs.readCouple(
            data, "Points", point -> TravellingPoint.read(point, graph, dimensions));
    return new CarriageBogey(
        type,
        data.getBooleanOr(UPSIDE_DOWN_KEY, false),
        data.getCompoundOrEmpty(BOGEY_DATA_KEY),
        points.getFirst(),
        points.getSecond());
  }

  public BogeyStyle getStyle() {
    var id = Identifier.tryParse(bogeyData.getStringOr(BOGEY_STYLE_KEY, ""));
    return AllBogeyStyles.BOGEY_STYLES.getOrDefault(id, AllBogeyStyles.STANDARD);
  }

  public BogeySizes.BogeySize getSize() {
    return type.getSize();
  }

  private CompoundTag createBogeyData() {
    var data = type.getDefaultStyle().defaultData.copy();
    data.putString(BOGEY_STYLE_KEY, type.getDefaultStyle().id.toString());
    data.putBoolean(UPSIDE_DOWN_KEY, isUpsideDown());
    return data;
  }

  void setLeading() {
    isLeading = true;
  }

  public boolean isUpsideDown() {
    return type.canBeUpsideDown() && upsideDown;
  }
}
