package com.simibubi.create.content.trains.signal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.UnloadedTrackException;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.function.Supplier;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Genuine typed edge point geometry/lifecycle; concrete stations/signals register their actual
 * codecs.
 */
public abstract class TrackEdgePoint {
  public static final Codec<TrackEdgePoint> CODEC =
      EdgePointType.CODEC.dispatch(
          "Type", TrackEdgePoint::getType, type -> type.codec().fieldOf("Data"));
  public UUID id = UUID.randomUUID();
  public Couple<TrackNodeLocation> edgeLocation;
  public double position;
  private EdgePointType<?> type;
  private TrackGraph graph;

  public interface MigrationReceiver {
    void invalidateEdgePoint(CompoundTag sourceData);
  }

  public interface SignalBoundaryPoint {
    UUID groupFor(TrackNode side);
  }

  public static <T extends TrackEdgePoint> Codec<T> baseCodec(Supplier<T> factory) {
    return RecordCodecBuilder.create(
        i ->
            i.group(
                    UUIDUtil.CODEC.fieldOf("Id").forGetter(point -> point.id),
                    RailCodecs.couple(TrackNodeLocation.CODEC)
                        .fieldOf("Edge")
                        .forGetter(point -> point.edgeLocation),
                    RailCodecs.FINITE_DOUBLE.fieldOf("Position").forGetter(point -> point.position))
                .apply(
                    i,
                    (id, edge, position) -> {
                      var point = factory.get();
                      point.setId(id);
                      point.setLocation(edge, position);
                      return point;
                    }));
  }

  public void setId(UUID id) {
    if (graph != null) throw new IllegalStateException("Cannot rename attached railway point");
    this.id = Objects.requireNonNull(id);
  }

  public UUID getId() {
    return id;
  }

  public void setType(EdgePointType<?> type) {
    if (graph != null && this.type != type)
      throw new IllegalStateException("Cannot change attached railway point type");
    this.type = Objects.requireNonNull(type);
  }

  public EdgePointType<?> getType() {
    return Objects.requireNonNull(type, "Unregistered railway edge point");
  }

  public void bind(TrackGraph graph) {
    if (this.graph != null && graph != null && this.graph != graph)
      throw new IllegalStateException("Railway point has another graph owner");
    this.graph = graph;
  }

  public TrackGraph graph() {
    return graph;
  }

  public abstract boolean canMerge();

  public boolean canCoexistWith(EdgePointType<?> otherType, boolean front) {
    return false;
  }

  public abstract void invalidate(LevelAccessor level);

  /**
   * Every real owning block entity which invalidate() may migrate, including other dimensions.
   * Implementations without world ownership explicitly return an empty collection.
   */
  public abstract Collection<GlobalPos> invalidationTargets(LevelAccessor level);

  public final void preflightInvalidation(LevelAccessor level) {
    var missing = new LinkedHashSet<GlobalPos>();
    Level current = level instanceof Level value ? value : null;
    for (var target : invalidationTargets(level)) {
      Level owner =
          current != null && current.dimension().equals(target.dimension())
              ? current
              : current != null && current.getServer() != null
                  ? current.getServer().getLevel(target.dimension())
                  : null;
      if (owner == null || !LoadedLevelAccess.isLoaded(owner, target.pos())) missing.add(target);
    }
    if (!missing.isEmpty()) throw new UnloadedTrackException(missing);
  }

  protected void invalidateAt(LevelAccessor level, BlockPos pos) {
    if (level instanceof Level world && !LoadedLevelAccess.isLoaded(world, pos))
      throw new UnloadedTrackException(world, pos);
    var entity = LoadedLevelAccess.blockEntity(level, pos);
    if (!(entity instanceof MigrationReceiver receiver)) return;
    var data = new CompoundTag();
    var dimensions = new DimensionPalette();
    write(data, level.registryAccess(), dimensions);
    dimensions.write(data);
    receiver.invalidateEdgePoint(data);
  }

  public abstract void blockEntityAdded(BlockEntity entity, boolean front);

  public abstract void blockEntityRemoved(BlockPos pos, boolean front);

  /** Source lifecycle extension; graph removal still owns registry/edge detachment. */
  public void onRemoved(TrackGraph graph) {
    bind(null);
  }

  public void setLocation(Couple<TrackNodeLocation> nodes, double position) {
    if (graph != null) throw new IllegalStateException("Cannot reposition attached railway point");
    if (!Double.isFinite(position) || position < 0 || nodes.getFirst().equals(nodes.getSecond()))
      throw new IllegalArgumentException("Invalid railway edge coordinate");
    edgeLocation = nodes.map(TrackNodeLocation::copy);
    this.position = position;
  }

  public double getLocationOn(TrackEdge edge) {
    return isPrimary(edge.node1) ? edge.getLength() - position : position;
  }

  public boolean canNavigateVia(TrackNode side) {
    return true;
  }

  public boolean isPrimary(TrackNode node) {
    return edgeLocation.getSecond().equals(node.getLocation());
  }

  public void read(
      CompoundTag nbt,
      HolderLookup.Provider registries,
      boolean migration,
      DimensionPalette dimensions) {
    if (migration) return;
    setId(nbt.read("Id", UUIDUtil.CODEC).orElseThrow());
    setLocation(
        RailCodecs.readCouple(nbt, "Edge", entry -> TrackNodeLocation.read(entry, dimensions)),
        nbt.getDoubleOr("Position", Double.NaN));
  }

  public void read(FriendlyByteBuf buf, DimensionPalette dimensions) {
    setId(buf.readUUID());
    setLocation(Couple.create(() -> TrackNodeLocation.receive(buf, dimensions)), buf.readDouble());
  }

  public void write(
      CompoundTag nbt, HolderLookup.Provider registries, DimensionPalette dimensions) {
    nbt.store("Id", UUIDUtil.CODEC, id);
    nbt.putDouble("Position", position);
    nbt.put("Edge", RailCodecs.writeCouple(edgeLocation, value -> value.write(dimensions)));
  }

  public void write(FriendlyByteBuf buf, DimensionPalette dimensions) {
    buf.writeIdentifier(getType().getId());
    buf.writeUUID(id);
    edgeLocation.forEach(location -> location.send(buf, dimensions));
    buf.writeDouble(position);
  }

  public abstract void tick(TrackGraph graph, boolean preTrains);

  protected void removeFromAllGraphs() {
    if (graph != null) graph.removePoint(getType(), id);
  }
}
