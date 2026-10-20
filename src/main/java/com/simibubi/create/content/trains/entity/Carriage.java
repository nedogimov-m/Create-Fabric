package com.simibubi.create.content.trains.entity;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.minecart.TrainCargoManager;
import com.simibubi.create.content.trains.TrainAdvancements;
import com.simibubi.create.content.trains.entity.TravellingPoint.IEdgePointListener;
import com.simibubi.create.content.trains.entity.TravellingPoint.ITrackSelector;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.RailCodecs;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.jspecify.annotations.Nullable;

/**
 * Actual source carriage wheel/portal/body lifecycle. Staging only: genuine native entity, Train,
 * Navigation and SavedData integration are still required before compilation or release.
 */
public class Carriage {
  public static final StreamCodec<RegistryFriendlyByteBuf, Carriage> STREAM_CODEC =
      StreamCodec.composite(
          CarriageBogey.STREAM_CODEC,
          carriage -> carriage.bogeys.getFirst(),
          ByteBufCodecs.optional(CarriageBogey.STREAM_CODEC)
              .map(value -> value.orElse(null), Optional::ofNullable),
          carriage -> carriage.bogeys.getSecond(),
          ByteBufCodecs.VAR_INT,
          carriage -> carriage.bogeySpacing,
          Carriage::new);

  public static final AtomicInteger netIdGenerator = new AtomicInteger();

  public Train train;
  public int id;
  public boolean blocked;
  public boolean stalled;
  public Couple<Boolean> presentConductors;

  public int bogeySpacing;
  public Couple<CarriageBogey> bogeys;
  public TrainCargoManager storage;

  CompoundTag serialisedEntity;
  Map<Integer, CompoundTag> serialisedPassengers;
  private final CarriagePassengerLifecycle passengerLifecycle;
  CarriagePassengerLifecycle passengerLifecycle(){return passengerLifecycle;}

  private Map<ResourceKey<Level>, DimensionalCarriageEntity> entities;
  CarriageRestorationState restoration;

  void preparePassengerWorldReferences(ServerLevel world,CarriageContraptionEntity front,com.simibubi.create.content.contraptions.StructureTransform transform){passengerLifecycle.worldReferences(world,front,transform);}
  boolean prepareNativePassengerUnload(CarriageContraptionEntity front){
    var dimensional=entities.get(front.level().dimension());return dimensional!=null&&dimensional.entity.get()==front&&dimensional.preparePassengerRetirement(front,false);
  }
  boolean prepareNativeNpcRootUnload(CarriageContraptionEntity front,Entity actual){
    Entity root=actual;int depth=0;while(root.getVehicle()!=front){if(root.getVehicle()==null||++depth>CarriagePassengerGraph.MAX_DEPTH)return false;root=root.getVehicle();}
    var seat=front.getContraption().getSeatMapping().get(root.getUUID());return seat!=null&&passengerLifecycle.retire(front,root,seat);
  }
  void nativeFrontendRemoved(CarriageContraptionEntity front){var dimensional=entities.get(front.level().dimension());if(dimensional!=null&&dimensional.entity.get()==front)dimensional.entity.clear();var manager=train==null?null:train.trafficManager();if(manager!=null)manager.markDirty();}

  private boolean passengerOwnerCurrent(){
    if(train==null)return false;var manager=train.trafficManager();if(manager!=null)return manager.isCurrent()&&manager.get(train.id)==train&&train.carriages.contains(this);
    if(entities!=null)for(var dimensional:entities.values()){var front=dimensional.entity.get();if(front!=null&&front.getCarriage()==this&&!front.isGloballyManaged()&&CarriagePlayerReferenceTransitions.frontCurrent(front))return true;}return false;
  }

  public boolean isRestoring() {
    return restoration != null && !restoration.restored;
  }

  public boolean isWorldRestored() {
    return restoration != null && restoration.restored;
  }

  CarriageContraptionEntity restorationFront(ServerLevel level) {
    if (isWorldRestored()
        || restoration == null
        || !restoration.dimension.equals(level.dimension())) return null;
    var dimensional = entities.get(level.dimension());
    if (dimensional == null) return null;
    var front = dimensional.entity.get();
    if (front != null
        && (front.isRemoved()
            || !CarriageEntityHandler.isActiveChunk(level, front.blockPosition()))) {
      if(!dimensional.removeAndSaveEntity(front, false))return null;
      front = null;
    }
    if (front == null) {
      dimensional.createEntity(level, true);
      front = dimensional.entity.get();
    }
    if (front != null) dimensional.updatePassengerLoadout();
    return front;
  }

  Map<UUID, Integer> pendingRestorationPlayers() {
    var front=anyAvailableEntity();return front==null?null:passengerLifecycle.pendingDirectPlayers(front);
  }

  void completeWorldRestoration(CarriageContraptionEntity front) {
    if (restoration == null
        || restoration.restored
        || !restoration.body.equals(front.getUUID())
        || !front.level().dimension().equals(restoration.dimension)
        || front.getContraption() == null
        || front.getContraption().phase() != Contraption.Phase.DISASSEMBLED
        || storage == null
        || storage.ownsContents()
        || !serialisedPassengers.isEmpty())
      throw new IllegalStateException(
          "Actual completed native body/passenger restoration required");
    // The native commit transferred the actual cargo to the world. Retain no second payload.
    storage = new TrainCargoManager();
    storage.initialize();
    serialisedEntity = new CompoundTag();
    entities.clear();
    presentConductors.replace(ignored -> false);
    restoration.restored = true;
  }

  static final int FIRST = 0, MIDDLE = 1, LAST = 2, BOTH = 3;

  public Carriage(CarriageBogey bogey1, @Nullable CarriageBogey bogey2, int bogeySpacing) {
    if (bogeySpacing < 0 || bogey2 != null && bogeySpacing == 0)
      throw new IllegalArgumentException("Invalid actual carriage bogey spacing");
    this.bogeySpacing = bogeySpacing;
    this.bogeys = Couple.create(bogey1, bogey2);
    this.id = netIdGenerator.incrementAndGet();
    this.serialisedEntity = new CompoundTag();
    this.presentConductors = Couple.create(false, false);
    this.serialisedPassengers = new HashMap<>();
    this.passengerLifecycle=new CarriagePassengerLifecycle(new CarriagePassengerLifecycle.Owner(){
      public Map<Integer,CompoundTag> roots(){return serialisedPassengers;}
      public boolean current(){return passengerOwnerCurrent();}
      public boolean owns(CarriageContraptionEntity front){return front.getCarriage()==Carriage.this&&front.getContraption()!=null&&front.getContraption().getStorage()==storage;}
      public void dirty(){var manager=train==null?null:train.trafficManager();if(manager!=null)manager.markDirty();}
    });
    this.entities = new HashMap<>();
    this.storage = new TrainCargoManager();
    this.storage.initialize();

    bogey1.setLeading();
    bogey1.carriage = this;
    if (bogey2 != null) bogey2.carriage = this;
  }

  public boolean isOnIncompatibleTrack() {
    return isOnIncompatibleTrack(true) || isOnIncompatibleTrack(false);
  }

  private boolean isOnIncompatibleTrack(boolean leading) {
    var point = leading ? getLeadingPoint() : getTrailingPoint();
    var bogey = leading ? leadingBogey() : trailingBogey();
    // Source reports no material mismatch for an unresolved endpoint; native Train readiness
    // separately prevents travel until its real graph point resolves.
    if (train != null && train.graph != null && !point.resolve(train.graph)) return false;
    return point.edge != null
        && bogey.type.isOnIncompatibleTrack(point.edge.getTrackMaterial(), bogey.getStyle());
  }

  public void setTrain(Train train) {
    this.train = train;
  }

  public boolean presentInMultipleDimensions() {
    return entities.size() > 1;
  }

  public List<ResourceKey<Level>> getPresentDimensions() {
    return entities.keySet().stream().distinct().toList();
  }

  public Optional<BlockPos> getPositionInDimension(ResourceKey<Level> dimension) {
    return Optional.ofNullable(entities.get(dimension))
        .map(carriage -> BlockPos.containing(carriage.positionAnchor));
  }

  public void setContraption(Level level, CarriageContraption contraption) {
    if (level.isClientSide()
        || contraption.phase() != Contraption.Phase.REMOVED
        || !contraption.getStorage().ownsContents()
        || !serialisedEntity.isEmpty())
      throw new IllegalStateException(
          "Actual fresh removed Carriage body required for global adoption");
    if (!(level instanceof ServerLevel server)
        || !(contraption.entity instanceof CarriageContraptionEntity entity)
        || entity.getContraption() != contraption
        || server.getEntity(entity.getUUID()) != entity
        || entity.isRemoved())
      throw new IllegalStateException(
          "Actual accepted assembly reservation must own this removed body");
    this.storage = null;
    entity.setCarriage(this);
    updateContraptionAnchors();
    DimensionalCarriageEntity dimensional = getDimensional(level);
    dimensional.entity = new WeakReference<>(entity);
    dimensional.alignEntity(entity);entity.transferIndependentPassengers(this);
    // Captured riders are real original entities, adopted before any body serialization.
    if (!entity.adoptCapturedOriginalRoots())
      throw new IllegalStateException("Original captured carriage passengers are not yet adopted");
    serialize(entity);passengerLifecycle.capture(entity);
    entity.syncCarriage();
  }

  public DimensionalCarriageEntity getDimensional(Level level) {
    return getDimensional(level.dimension());
  }

  public DimensionalCarriageEntity getDimensional(ResourceKey<Level> dimension) {
    return entities.computeIfAbsent(dimension, $ -> new DimensionalCarriageEntity());
  }

  @Nullable
  public DimensionalCarriageEntity getDimensionalIfPresent(ResourceKey<Level> dimension) {
    return entities.get(dimension);
  }

  public double travel(
      Level level,
      TrackGraph graph,
      double distance,
      TravellingPoint toFollowForward,
      TravellingPoint toFollowBackward,
      int type) {

    Function<TravellingPoint, ITrackSelector> forwardControl =
        toFollowForward == null ? train.navigation::control : mp -> mp.follow(toFollowForward);
    Function<TravellingPoint, ITrackSelector> backwardControl =
        toFollowBackward == null ? train.navigation::control : mp -> mp.follow(toFollowBackward);

    boolean onTwoBogeys = isOnTwoBogeys();
    double stress = train.derailed ? 0 : onTwoBogeys ? bogeySpacing - getAnchorDiff() : 0;
    blocked = false;

    MutableDouble distanceMoved = new MutableDouble(distance);
    boolean iterateFromBack = distance < 0;

    for (boolean firstBogey : new boolean[] {true, false}) {
      if (!firstBogey && !onTwoBogeys) continue;

      boolean actuallyFirstBogey = !onTwoBogeys || (firstBogey ^ iterateFromBack);
      CarriageBogey bogey = bogeys.get(actuallyFirstBogey);
      double bogeyCorrection = stress * (actuallyFirstBogey ? 0.5d : -0.5d);
      double bogeyStress = bogey.getStress();

      for (boolean firstWheel : new boolean[] {true, false}) {
        boolean actuallyFirstWheel = firstWheel ^ iterateFromBack;
        TravellingPoint point = bogey.points.get(actuallyFirstWheel);
        TravellingPoint prevPoint =
            !actuallyFirstWheel
                ? bogey.points.getFirst()
                : !actuallyFirstBogey && onTwoBogeys ? bogeys.getFirst().points.getSecond() : null;
        TravellingPoint nextPoint =
            actuallyFirstWheel
                ? bogey.points.getSecond()
                : actuallyFirstBogey && onTwoBogeys ? bogeys.getSecond().points.getFirst() : null;

        double correction = bogeyStress * (actuallyFirstWheel ? 0.5d : -0.5d);
        double toMove = distanceMoved.getValue();

        ITrackSelector frontTrackSelector =
            prevPoint == null ? forwardControl.apply(point) : point.follow(prevPoint);
        ITrackSelector backTrackSelector =
            nextPoint == null ? backwardControl.apply(point) : point.follow(nextPoint);

        boolean atFront =
            (type == FIRST || type == BOTH) && actuallyFirstWheel && actuallyFirstBogey;
        boolean atBack =
            (type == LAST || type == BOTH)
                && !actuallyFirstWheel
                && (!actuallyFirstBogey || !onTwoBogeys);

        IEdgePointListener frontListener = train.frontSignalListener();
        IEdgePointListener backListener = train.backSignalListener();
        IEdgePointListener passiveListener = point.ignoreEdgePoints();

        toMove += correction + bogeyCorrection;

        ITrackSelector trackSelector = toMove > 0 ? frontTrackSelector : backTrackSelector;
        IEdgePointListener signalListener =
            toMove > 0
                ? atFront ? frontListener : atBack ? backListener : passiveListener
                : atFront ? backListener : atBack ? frontListener : passiveListener;

        double moved =
            point.travel(
                graph,
                toMove,
                trackSelector,
                signalListener,
                point.ignoreTurns(),
                c -> {
                  for (DimensionalCarriageEntity dce : entities.values())
                    if (c.either(tnl -> tnl.equalsIgnoreDim(dce.pivot))) return false;
                  if (entities.size() > 1) {
                    train.status.doublePortal();
                    return true;
                  }
                  return false;
                });

        blocked |= point.blocked;

        distanceMoved.setValue(moved);
      }
    }

    updateContraptionAnchors();
    manageEntities(level);
    return distanceMoved.getValue();
  }

  public double getAnchorDiff() {
    double diff = 0;
    int entries = 0;

    TravellingPoint leadingPoint = getLeadingPoint();
    TravellingPoint trailingPoint = getTrailingPoint();
    if (leadingPoint.node1 != null && trailingPoint.node1 != null)
      if (!leadingPoint
          .node1
          .getLocation()
          .dimension
          .equals(trailingPoint.node1.getLocation().dimension)) return bogeySpacing;

    for (DimensionalCarriageEntity dce : entities.values())
      if (dce.leadingAnchor() != null && dce.trailingAnchor() != null) {
        entries++;
        diff += dce.leadingAnchor().distanceTo(dce.trailingAnchor());
      }

    if (entries == 0) return bogeySpacing;
    return diff / entries;
  }

  public void updateConductors() {
    if (anyAvailableEntity() == null || entities.size() > 1 || passengerLifecycle.hasPendingNpc())
      return;
    presentConductors.replace($ -> false);
    for (DimensionalCarriageEntity dimensionalCarriageEntity : entities.values()) {
      CarriageContraptionEntity entity = dimensionalCarriageEntity.entity.get();
      if (entity != null && entity.isAlive())
        presentConductors.replaceWithParams(
            (current, checked) -> current || checked, entity.checkConductors());
    }
  }

  private Set<ResourceKey<Level>> currentlyTraversedDimensions = new HashSet<>();

  public void manageEntities(Level level) {
    if (restoration != null) return;
    currentlyTraversedDimensions.clear();

    bogeys.forEach(
        cb -> {
          if (cb == null) return;
          cb.points.forEach(
              tp -> {
                if (tp.node1 == null) return;
                currentlyTraversedDimensions.add(tp.node1.getLocation().dimension);
              });
        });

    for (Iterator<Entry<ResourceKey<Level>, DimensionalCarriageEntity>> iterator =
            entities.entrySet().iterator();
        iterator.hasNext(); ) {
      Entry<ResourceKey<Level>, DimensionalCarriageEntity> entry = iterator.next();

      boolean discard =
          !currentlyTraversedDimensions.isEmpty()
              && !currentlyTraversedDimensions.contains(entry.getKey());

      MinecraftServer server = level.getServer();
      if (server == null) continue;
      ServerLevel currentLevel = server.getLevel(entry.getKey());
      if (currentLevel == null) continue;

      DimensionalCarriageEntity dimensionalCarriageEntity = entry.getValue();
      CarriageContraptionEntity entity = dimensionalCarriageEntity.entity.get();

      if (entity == null) {
        if (discard) iterator.remove();
        else if (dimensionalCarriageEntity.positionAnchor != null
            && CarriageEntityHandler.isActiveChunk(
                currentLevel, BlockPos.containing(dimensionalCarriageEntity.positionAnchor)))
          dimensionalCarriageEntity.createEntity(currentLevel, anyAvailableEntity() == null);

      } else {
        if (discard) {
          discard = dimensionalCarriageEntity.discardTicks > 3;
          dimensionalCarriageEntity.discardTicks++;
        } else dimensionalCarriageEntity.discardTicks = 0;

        CarriageEntityHandler.validateCarriageEntity(entity);
        if (!entity.isAlive() || entity.leftTickingChunks || discard) {
          boolean retired=dimensionalCarriageEntity.removeAndSaveEntity(entity, discard);
          if (discard&&retired) iterator.remove();
          continue;
        }
      }

      entity = dimensionalCarriageEntity.entity.get();
      if (entity != null && dimensionalCarriageEntity.positionAnchor != null) {
        dimensionalCarriageEntity.alignEntity(entity);
        entity.syncCarriage();
      }
    }
  }

  public void updateContraptionAnchors() {
    CarriageBogey leadingBogey = leadingBogey();
    if (leadingBogey.points.either(t -> t.edge == null)) return;
    CarriageBogey trailingBogey = trailingBogey();
    if (trailingBogey.points.either(t -> t.edge == null)) return;

    ResourceKey<Level> leadingBogeyDim = leadingBogey.getDimension();
    ResourceKey<Level> trailingBogeyDim = trailingBogey.getDimension();
    double leadingWheelSpacing = leadingBogey.type.getWheelPointSpacing();
    double trailingWheelSpacing = trailingBogey.type.getWheelPointSpacing();

    boolean leadingUpsideDown = leadingBogey.isUpsideDown();
    boolean trailingUpsideDown = trailingBogey.isUpsideDown();

    for (boolean leading : new boolean[] {true, false}) {
      TravellingPoint point = leading ? getLeadingPoint() : getTrailingPoint();
      TravellingPoint otherPoint = !leading ? getLeadingPoint() : getTrailingPoint();
      ResourceKey<Level> dimension = point.node1.getLocation().dimension;
      ResourceKey<Level> otherDimension = otherPoint.node1.getLocation().dimension;

      if (dimension.equals(otherDimension) && leading) {
        getDimensional(dimension).discardPivot();
        continue;
      }

      DimensionalCarriageEntity dce = getDimensional(dimension);

      dce.positionAnchor =
          dimension.equals(leadingBogeyDim)
              ? leadingBogey.getAnchorPosition()
              : pivoted(
                  dce,
                  dimension,
                  point,
                  leading ? leadingWheelSpacing / 2 : bogeySpacing + trailingWheelSpacing / 2,
                  leadingUpsideDown,
                  trailingUpsideDown);

      boolean backAnchorFlip = trailingBogey.isUpsideDown() ^ leadingBogey.isUpsideDown();

      if (isOnTwoBogeys()) {
        dce.rotationAnchors.setFirst(
            dimension.equals(leadingBogeyDim)
                ? leadingBogey.getAnchorPosition()
                : pivoted(
                    dce,
                    dimension,
                    point,
                    leading ? leadingWheelSpacing / 2 : bogeySpacing + trailingWheelSpacing / 2,
                    leadingUpsideDown,
                    trailingUpsideDown));
        dce.rotationAnchors.setSecond(
            dimension.equals(trailingBogeyDim)
                ? trailingBogey.getAnchorPosition(backAnchorFlip)
                : pivoted(
                    dce,
                    dimension,
                    point,
                    leading ? leadingWheelSpacing / 2 + bogeySpacing : trailingWheelSpacing / 2,
                    leadingUpsideDown,
                    trailingUpsideDown));

      } else {
        if (dimension.equals(otherDimension)) {
          dce.rotationAnchors = leadingBogey.points.map(tp -> tp.getPosition(train.graph));
        } else {
          dce.rotationAnchors.setFirst(
              leadingBogey.points.getFirst() == point
                  ? point.getPosition(train.graph)
                  : pivoted(
                      dce,
                      dimension,
                      point,
                      leadingWheelSpacing,
                      leadingUpsideDown,
                      trailingUpsideDown));
          dce.rotationAnchors.setSecond(
              leadingBogey.points.getSecond() == point
                  ? point.getPosition(train.graph)
                  : pivoted(
                      dce,
                      dimension,
                      point,
                      leadingWheelSpacing,
                      leadingUpsideDown,
                      trailingUpsideDown));
        }
      }

      int prevmin = dce.minAllowedLocalCoord();
      int prevmax = dce.maxAllowedLocalCoord();

      dce.updateCutoff(leading);

      if (prevmin != dce.minAllowedLocalCoord() || prevmax != dce.maxAllowedLocalCoord()) {
        dce.updateRenderedCutoff();
        dce.updatePassengerLoadout();
      }
    }
  }

  private Vec3 pivoted(
      DimensionalCarriageEntity dce,
      ResourceKey<Level> dimension,
      TravellingPoint start,
      double offset,
      boolean leadingUpsideDown,
      boolean trailingUpsideDown) {
    if (train.graph == null) return dce.pivot == null ? null : dce.pivot.getLocation();
    TrackNodeLocation pivot = dce.findPivot(dimension, start == getLeadingPoint());
    if (pivot == null) return null;
    boolean flipped = start != getLeadingPoint() && (leadingUpsideDown != trailingUpsideDown);
    Vec3 startVec = start.getPosition(train.graph, flipped);
    Vec3 portalVec = pivot.getLocation().add(0, leadingUpsideDown ? -1.0 : 1.0, 0);
    double length = startVec.distanceTo(portalVec);
    if (length < 1e-12) return startVec;
    return startVec.lerp(portalVec, offset / length);
  }

  public void alignEntity(Level level) {
    DimensionalCarriageEntity dimensionalCarriageEntity = entities.get(level.dimension());
    if (dimensionalCarriageEntity != null) {
      CarriageContraptionEntity entity = dimensionalCarriageEntity.entity.get();
      if (entity != null) dimensionalCarriageEntity.alignEntity(entity);
    }
  }

  public TravellingPoint getLeadingPoint() {
    return leadingBogey().leading();
  }

  public TravellingPoint getTrailingPoint() {
    return trailingBogey().trailing();
  }

  public CarriageBogey leadingBogey() {
    return bogeys.getFirst();
  }

  public CarriageBogey trailingBogey() {
    return isOnTwoBogeys() ? bogeys.getSecond() : leadingBogey();
  }

  public boolean isOnTwoBogeys() {
    return bogeys.getSecond() != null;
  }

  public CarriageContraptionEntity anyAvailableEntity() {
    for (DimensionalCarriageEntity dimensionalCarriageEntity : entities.values()) {
      CarriageContraptionEntity entity = dimensionalCarriageEntity.entity.get();
      if (entity != null) return entity;
    }
    return null;
  }

  public Pair<ResourceKey<Level>, DimensionalCarriageEntity> anyAvailableDimensionalCarriage() {
    for (Entry<ResourceKey<Level>, DimensionalCarriageEntity> entry : entities.entrySet())
      if (entry.getValue().entity.get() != null) return Pair.of(entry.getKey(), entry.getValue());
    return null;
  }

  public void forEachPresentEntity(Consumer<CarriageContraptionEntity> callback) {
    for (DimensionalCarriageEntity dimensionalCarriageEntity : entities.values()) {
      CarriageContraptionEntity entity = dimensionalCarriageEntity.entity.get();
      if (entity != null) callback.accept(entity);
    }
  }

  public record CustodyIdentities(Set<UUID> bodies, Set<UUID> passengers) {
    public CustodyIdentities {
      bodies = Set.copyOf(bodies);
      passengers = Set.copyOf(passengers);
    }
  }

  /**
   * Actual original identities only; no entity or cargo copies are exposed to the global manager.
   */
  public CustodyIdentities custodyIdentities() {
    var bodies = new HashSet<UUID>();
    var passengers = new HashSet<UUID>();
    if (isWorldRestored()) bodies.add(restoration.body);
    if (!serialisedEntity.isEmpty())
      bodies.add(serialisedEntity.read("UUID", UUIDUtil.CODEC).orElseThrow());
    for(var data:serialisedPassengers.values())for(var id:CarriagePassengerGraph.fromLegacy(data,UUID.randomUUID()).identities())passengers.add(id);
    for (var dimensional : entities.values()) {
      var front = dimensional.entity.get();
      if (front == null || front.isRemoved()) continue;
      bodies.add(front.getUUID());
      for (var passenger : front.getPassengers())
        passenger.getSelfAndPassengers().forEach(actual -> passengers.add(actual.getUUID()));
    }
    if (!Collections.disjoint(bodies, passengers))
      throw new IllegalStateException("An actual Carriage body cannot also be its passenger");
    return new CustodyIdentities(bodies, passengers);
  }

  private static void collectPassengerIdentities(CompoundTag data, Set<UUID> identities) {
    if (!identities.add(data.read("UUID", UUIDUtil.CODEC).orElseThrow()))
      throw new IllegalArgumentException("Duplicate actual persisted carriage passenger UUID");
    for (var value : data.getListOrEmpty("Passengers")) {
      if (!(value instanceof CompoundTag child))
        throw new IllegalArgumentException("Invalid actual nested carriage passenger");
      collectPassengerIdentities(child, identities);
    }
  }

  public CompoundTag write(DimensionPalette dimensions, HolderLookup.Provider registries) {
    if (storage == null || (!storage.ownsContents() && !isWorldRestored()))
      throw new IllegalStateException(
          "Actual cargo custody or a consumed world receipt is required");
    if (isWorldRestored()
        && (storage.ownsContents()
            || !serialisedEntity.isEmpty()
            || !serialisedPassengers.isEmpty()
            || !entities.isEmpty()
            || !storage.getAllItemStorages().isEmpty()
            || !storage.getFluids().storages.isEmpty()))
      throw new IllegalStateException(
          "Consumed carriage receipt cannot retain a cargo/body/passenger payload");
    CompoundTag tag = new CompoundTag();
    tag.put("FirstBogey", leadingBogey().write(dimensions));
    if (isOnTwoBogeys()) tag.put("SecondBogey", trailingBogey().write(dimensions));
    tag.putInt("Spacing", bogeySpacing);
    tag.putBoolean("FrontConductor", presentConductors.getFirst());
    tag.putBoolean("BackConductor", presentConductors.getSecond());
    tag.putBoolean("Stalled", stalled);
    if (restoration != null) tag.put("Restoration", restoration.write(dimensions));
    if (isWorldRestored()) return tag;
    CarriageContraptionEntity witness=null;
    for(var dimensional:entities.values()){
      var front=dimensional.entity.get();if(front==null||front.isRemoved())continue;serialize(front);passengerLifecycle.capture(front);witness=front;
    }
    var traffic=train==null?null:train.trafficManager();
    Map<Integer,CompoundTag> passengerMap=witness==null?passengerLifecycle.writeWithoutFront(traffic==null?null:traffic.server(),serialisedEntity.read("UUID",net.minecraft.core.UUIDUtil.CODEC).orElse(null)):passengerLifecycle.write(witness);
    tag.put("Entity", serialisedEntity.copy());
    var passengers = new CompoundTag();
    passengerMap.forEach((seat, data) -> passengers.put("Seat" + seat, data.copy()));
    tag.put("Passengers", passengers);
    var positioning = new ListTag();
    for (var entry : entities.entrySet()) {
      var data = entry.getValue().write(registries);
      data.putInt("Dim", dimensions.encode(entry.getKey()));
      positioning.add(data);
    }
    tag.put("EntityPositioning", positioning);
    // Source wrote this same manager in every dimensional positioning entry. Persist once.
    tag.putInt("CargoVersion", 1);
    var cargo = new CompoundTag();
    storage.write(cargo, registries, false);
    tag.put("Cargo", cargo);
    return tag;
  }

  private static void putPassenger(Map<Integer, CompoundTag> into, int seat, CompoundTag data) {
    if (seat < 0) throw new IllegalArgumentException("Negative carriage passenger seat");
    var previous = into.putIfAbsent(seat, data.copy());
    if (previous != null && !previous.equals(data))
      throw new IllegalStateException("Two actual passengers cannot own one carriage seat");
  }

  private void serialize(Entity entity) {
    if (!(entity instanceof CarriageContraptionEntity front) || front.getCarriage() != this)
      throw new IllegalArgumentException("Only this actual Carriage front can provide body state");
    if (!front.adoptCapturedOriginalRoots())
      throw new IllegalStateException("Cannot persist unadopted original captured passengers");
    var problems = new ProblemReporter.Collector(entity.problemPath());
    var output = TagValueOutput.createWithContext(problems, entity.registryAccess());
    // Native front entities are globally owned and intentionally not also saved by the chunk.
    entity.saveWithoutId(output);
    if (!problems.isEmpty())
      throw new IllegalStateException(
          "Actual carriage body could not be persisted: " + problems.getReport());
    var snapshot = output.buildResult();
    snapshot.putString("id", EntityType.getKey(entity.getType()).toString());
    snapshot.remove("Passengers");
    snapshot.remove("IndependentCarriage");
    snapshot.remove("IndependentPassengers");snapshot.remove("PendingCapturedRoots");
    snapshot.store("TrainId", UUIDUtil.CODEC, train.id);
    snapshot.putInt("CarriageIndex", train.carriages.indexOf(this));
    var body = snapshot.getCompound("Contraption").orElseThrow();
    body.remove("Passengers");
    // Before manager publication the same accepted front also supports genuine native recovery.
    // A Carriage snapshot always stores this exact shared manager separately, once at row scope.
    body.remove("NativeStandaloneCargo");
    body.remove("items");
    body.remove("fluids");
    body.remove("TicksSinceLastExchange");
    if (body.contains("items") || body.contains("fluids"))
      throw new IllegalStateException("Actual Carriage body cannot duplicate globally owned cargo");
    serialisedEntity = snapshot;
  }

  public static Carriage read(
      CompoundTag tag,
      HolderLookup.Provider registries,
      TrackGraph graph,
      DimensionPalette dimensions) {
    var first = CarriageBogey.read(tag.getCompound("FirstBogey").orElseThrow(), graph, dimensions);
    var second =
        tag.contains("SecondBogey")
            ? CarriageBogey.read(tag.getCompound("SecondBogey").orElseThrow(), graph, dimensions)
            : null;
    int spacing = tag.getIntOr("Spacing", -1);
    if (spacing < 0 || second != null && spacing == 0)
      throw new IllegalArgumentException("Invalid actual carriage bogey spacing");
    var carriage = new Carriage(first, second, spacing);
    carriage.stalled = tag.getBooleanOr("Stalled", false);
    carriage.presentConductors =
        Couple.create(
            tag.getBooleanOr("FrontConductor", false), tag.getBooleanOr("BackConductor", false));
    if (tag.contains("Restoration"))
      carriage.restoration =
          CarriageRestorationState.read(tag.getCompound("Restoration").orElseThrow(), dimensions);
    if (carriage.isWorldRestored()) {
      for (var forbidden :
          List.of("Entity", "Passengers", "Cargo", "CargoVersion", "EntityPositioning"))
        if (tag.contains(forbidden))
          throw new IllegalArgumentException(
              "Consumed actual carriage receipt retains private payload");
      return carriage;
    }
    carriage.serialisedEntity = tag.getCompound("Entity").orElseThrow().copy();
    if (carriage.restoration != null) {
      var actualId = carriage.serialisedEntity.read("UUID", UUIDUtil.CODEC).orElseThrow();
      if (!carriage.restoration.body.equals(actualId))
        throw new IllegalArgumentException("Actual restoration body identity mismatch");
      var actualCells =
          CarriageCargoPersistence.bodyPositions(
                  carriage.serialisedEntity.getCompound("Contraption").orElseThrow())
              .stream()
              .map(carriage.restoration.transform()::apply)
              .collect(java.util.stream.Collectors.toSet());
      if (!actualCells.equals(carriage.restoration.cells))
        throw new IllegalArgumentException(
            "Actual restoration extent disagrees with its owned body");
    }
    var seenDimensions = new HashSet<ResourceKey<Level>>();
    for (var value : tag.getListOrEmpty("EntityPositioning")) {
      if (!(value instanceof CompoundTag data))
        throw new IllegalArgumentException("Malformed actual carriage dimensional state");
      var dimension = dimensions.decode(data.getInt("Dim").orElseThrow());
      if (!seenDimensions.add(dimension))
        throw new IllegalArgumentException("Duplicate actual carriage dimensional state");
      carriage.getDimensional(dimension).read(data, registries);
    }
    if (carriage.restoration != null
        && (seenDimensions.size() != 1 || !seenDimensions.contains(carriage.restoration.dimension)))
      throw new IllegalArgumentException(
          "Actual restoring carriage must have one original dimension");
    var passengers=tag.getCompound("Passengers").orElseThrow();var rows=new HashMap<Integer,CompoundTag>();
    for(var key:passengers.keySet()){
      if(!key.startsWith("Seat"))throw new IllegalArgumentException("Unknown carriage passenger entry");int seat=Integer.parseInt(key.substring(4));
      if(!key.equals("Seat"+seat)||seat<0||rows.putIfAbsent(seat,passengers.getCompound(key).orElseThrow().copy())!=null)throw new IllegalArgumentException("Duplicate or noncanonical carriage passenger seat");
    }
    CarriagePassengerLifecycle.validateSavedSeatBounds(carriage.serialisedEntity.getCompound("Contraption").orElseThrow(),rows.keySet());
    carriage.passengerLifecycle.read(rows,Set.of(carriage.serialisedEntity.read("UUID",UUIDUtil.CODEC).orElseThrow()));
    // Decode one actual cargo manager only, after full source/native body geometry validation.
    var cargo = CarriageCargoPersistence.read(tag, carriage.serialisedEntity);
    carriage.storage.read(cargo, registries, false, null);
    carriage.storage.setOwned(true);
    return carriage;
  }

  private TravellingPoint portalScout = new TravellingPoint();

  public class DimensionalCarriageEntity {
    public Vec3 positionAnchor;
    public Couple<Vec3> rotationAnchors;
    public WeakReference<CarriageContraptionEntity> entity;

    public TrackNodeLocation pivot;
    int discardTicks;

    // 0 == whole, 0..1 = fading out, -1..0 = fading in
    public float cutoff;

    // client
    public boolean pointsInitialised;

    public DimensionalCarriageEntity() {
      this.entity = new WeakReference<>(null);
      this.rotationAnchors = Couple.create(null, null);
      this.pointsInitialised = false;
    }

    public void discardPivot() {
      int prevmin = minAllowedLocalCoord();
      int prevmax = maxAllowedLocalCoord();

      cutoff = 0;
      pivot = null;

      if ((!serialisedPassengers.isEmpty() && entity.get() != null)
          || prevmin != minAllowedLocalCoord()
          || prevmax != maxAllowedLocalCoord()) {
        updatePassengerLoadout();
        updateRenderedCutoff();
      }
    }

    public void updateCutoff(boolean leadingIsCurrent) {
      Vec3 leadingAnchor = rotationAnchors.getFirst();
      Vec3 trailingAnchor = rotationAnchors.getSecond();

      if (leadingAnchor == null || trailingAnchor == null) return;
      if (pivot == null) {
        cutoff = 0;
        return;
      }

      Vec3 pivotLoc = pivot.getLocation().add(0, 1, 0);

      double leadingSpacing = leadingBogey().type.getWheelPointSpacing() / 2;
      double trailingSpacing = trailingBogey().type.getWheelPointSpacing() / 2;
      double anchorSpacing = leadingSpacing + bogeySpacing + trailingSpacing;

      if (isOnTwoBogeys()) {
        Vec3 diff = trailingAnchor.subtract(leadingAnchor).normalize();
        trailingAnchor = trailingAnchor.add(diff.scale(trailingSpacing));
        leadingAnchor = leadingAnchor.add(diff.scale(-leadingSpacing));
      }

      double leadingDiff = leadingAnchor.distanceTo(pivotLoc);
      double trailingDiff = trailingAnchor.distanceTo(pivotLoc);

      leadingDiff /= anchorSpacing;
      trailingDiff /= anchorSpacing;

      if (leadingIsCurrent && leadingDiff > trailingDiff && leadingDiff > 1) cutoff = 0;
      else if (leadingIsCurrent && leadingDiff < trailingDiff && trailingDiff > 1) cutoff = 1;
      else if (!leadingIsCurrent && leadingDiff > trailingDiff && leadingDiff > 1) cutoff = -1;
      else if (!leadingIsCurrent && leadingDiff < trailingDiff && trailingDiff > 1) cutoff = 0;
      else
        cutoff =
            (float) Mth.clamp(1 - (leadingIsCurrent ? leadingDiff : trailingDiff), 0, 1)
                * (leadingIsCurrent ? 1 : -1);
    }

    public TrackNodeLocation findPivot(ResourceKey<Level> dimension, boolean leading) {
      if (pivot != null) return pivot;

      TravellingPoint start = leading ? getLeadingPoint() : getTrailingPoint();
      TravellingPoint end = !leading ? getLeadingPoint() : getTrailingPoint();

      if (train.graph == null || !start.resolve(train.graph) || !end.resolve(train.graph))
        return null;
      var scoutLocation = new com.simibubi.create.content.trains.graph.TrackGraphLocation();
      scoutLocation.graph = train.graph;
      scoutLocation.edge = Couple.create(start.node1.getLocation(), start.node2.getLocation());
      scoutLocation.position = start.position;
      portalScout.migrateTo(new java.util.ArrayList<>(List.of(scoutLocation)));

      ITrackSelector trackSelector = portalScout.follow(end);
      int distance = bogeySpacing + 10;
      int direction = leading ? -1 : 1;

      portalScout.travel(
          train.graph,
          direction * distance,
          trackSelector,
          portalScout.ignoreEdgePoints(),
          portalScout.ignoreTurns(),
          nodes -> {
            for (boolean b : new boolean[] {true, false})
              if (nodes.get(b).dimension.equals(dimension)) pivot = nodes.get(b);
            return true;
          });

      return pivot;
    }

    public CompoundTag write(HolderLookup.Provider registries) {
      var tag = new CompoundTag();
      tag.putFloat("Cutoff", cutoff);
      tag.putInt("DiscardTicks", discardTicks);
      if (pivot != null) tag.put("Pivot", pivot.write(null));
      if (positionAnchor != null) tag.store("PositionAnchor", RailCodecs.VECTOR, positionAnchor);
      if (rotationAnchors.both(Objects::nonNull))
        tag.put("RotationAnchors", RailCodecs.writeCouple(rotationAnchors, RailCodecs::vector));
      return tag;
    }

    public void read(CompoundTag tag, HolderLookup.Provider registries) {
      float savedCutoff = tag.getFloatOr("Cutoff", 0);
      int savedDiscard = tag.getIntOr("DiscardTicks", 0);
      if (!Float.isFinite(savedCutoff) || savedCutoff < -1 || savedCutoff > 1 || savedDiscard < 0)
        throw new IllegalArgumentException("Invalid actual carriage portal state");
      var savedPivot =
          tag.contains("Pivot")
              ? TrackNodeLocation.read(tag.getCompound("Pivot").orElseThrow(), null)
              : null;
      var savedPosition =
          tag.contains("PositionAnchor")
              ? tag.read("PositionAnchor", RailCodecs.VECTOR).orElseThrow()
              : null;
      Couple<Vec3> savedRotation =
          tag.contains("RotationAnchors")
              ? RailCodecs.readCouple(tag, "RotationAnchors", RailCodecs::vector)
              : Couple.create(null, null);
      cutoff = savedCutoff;
      discardTicks = savedDiscard;
      pivot = savedPivot;
      if (positionAnchor == null) {
        positionAnchor = savedPosition;
        rotationAnchors = savedRotation;
      }
    }

    public Vec3 leadingAnchor() {
      return isOnTwoBogeys() ? rotationAnchors.getFirst() : positionAnchor;
    }

    public Vec3 trailingAnchor() {
      return isOnTwoBogeys() ? rotationAnchors.getSecond() : positionAnchor;
    }

    public int minAllowedLocalCoord() {
      if (cutoff <= 0) return Integer.MIN_VALUE;
      if (cutoff >= 1) return Integer.MAX_VALUE;
      return Mth.floor(-bogeySpacing + -1 + (2 + bogeySpacing) * cutoff);
    }

    public int maxAllowedLocalCoord() {
      if (cutoff >= 0) return Integer.MAX_VALUE;
      if (cutoff <= -1) return Integer.MIN_VALUE;
      return Mth.ceil(-bogeySpacing + -1 + (2 + bogeySpacing) * (cutoff + 1));
    }

    public void updatePassengerLoadout() {
      var front = entity.get();
      if (front == null
          || !(front.level() instanceof ServerLevel server)
          || !front.hasOwnedStructure()
          || !CarriageEntityHandler.isActiveChunk(server, front.blockPosition())) return;
      var body = front.getContraption();
      int min = minAllowedLocalCoord(), max = maxAllowedLocalCoord();
      passengerLifecycle.load(front,positionAnchor,min,max);
      for (var passenger : List.copyOf(front.getPassengers())) {
        var seat = body.getSeatMapping().get(passenger.getUUID());
        if (seat == null || front.isLocalCoordWithin(body.getSeats().get(seat), min, max)) continue;
        if (passenger instanceof ServerPlayer player) {
          dismountPlayer(server, player, seat, true);
          continue;
        }
        passengerLifecycle.retire(front,passenger,seat);
      }
    }

    private void dismountPlayer(
        ServerLevel source, ServerPlayer player, Integer seat, boolean capture) {
      if (!capture) {
        player.stopRiding();
        return;
      }
      var data = new CompoundTag();
      data.store("PlayerPassenger", UUIDUtil.CODEC, player.getUUID());
      serialisedPassengers.put(seat,data);
      // This actual lifecycle owner just captured the one direct source reference.
      // Preserve only this exact native retirement unlink, never a later voluntary action.
      try(var sourceEdge=CarriagePlayerReferenceTransitions.preserve(player,null)){player.stopRiding();}
      var front = entity.get();
      if (front != null) front.clearDismountLocation(player);
      for (var entry : entities.entrySet()) {
        var other = entry.getValue();
        if (other == this || player.level().dimension().equals(entry.getKey())) continue;
        var target = source.getServer().getLevel(entry.getKey());
        var position = other.pivot == null ? other.positionAnchor : other.pivot.getLocation();
        if (target == null
            || position == null
            || !CarriageEntityHandler.isActiveChunk(target, BlockPos.containing(position)))
          continue;
        if (player.teleportTo(
            target,
            position.x,
            position.y,
            position.z,
            Set.of(),
            player.getYRot(),
            player.getXRot(),
            false)) {
          player.setPortalCooldown();
          TrainAdvancements.TRAIN_PORTAL.awardTo(player);
          break;
        }
      }
    }

    public void updateRenderedCutoff() {
      var front = entity.get();
      if (front == null || !(front.getContraption() instanceof CarriageContraption body)) return;
      body.portalCutoffMin = minAllowedLocalCoord();
      body.portalCutoffMax = maxAllowedLocalCoord();
      if (front.level().isClientSide()) front.updateRenderedPortalCutoff();
    }

    private void createEntity(Level level, boolean loadPassengers) {
      if (!(level instanceof ServerLevel server)
          || positionAnchor == null
          || entity.get() != null
          || !CarriageEntityHandler.isActiveChunk(server, BlockPos.containing(positionAnchor)))
        return;
      var snapshot = serialisedEntity.copy();
      snapshot.store("Pos", RailCodecs.VECTOR, positionAnchor);
      var problems = new ProblemReporter.Collector();
      var input = TagValueInput.create(problems, server.registryAccess(), snapshot);
      var loaded =
          EntityType.create(input, server, new EntitySpawnRequest(EntitySpawnReason.LOAD, false))
              .orElse(null);
      if (!problems.isEmpty() || !(loaded instanceof CarriageContraptionEntity front)) {
        train.invalid = true;
        return;
      }
      if (server.getEntity(front.getUUID()) != null) return;
      front.snapTo(positionAnchor);
      front.setCarriage(Carriage.this);
      alignEntity(front);
      // Publish only after actual native registration succeeds; a refused spawn keeps source state.
      if (!server.addFreshEntity(front)) return;
      entity = new WeakReference<>(front);
      front.syncCarriage();
      if (loadPassengers) updatePassengerLoadout();
    }

    private boolean removeAndSaveEntity(CarriageContraptionEntity front,boolean portal){
      if(!preparePassengerRetirement(front,portal))return false;
      front.retireToCarriage(Carriage.this);if(this.entity.get()==front)this.entity.clear();return true;
    }

    private boolean preparePassengerRetirement(CarriageContraptionEntity front,boolean portal){
      if(front.getCarriage()!=Carriage.this||front.getContraption()==null)throw new IllegalStateException("Cannot retire a foreign actual Carriage body");
      if(!passengerOwnerCurrent()||!CarriagePlayerReferenceTransitions.frontCurrent(front))return false;
      serialize(front);passengerLifecycle.capture(front);
      for(var passenger:List.copyOf(front.getPassengers())){
        var seat=front.getContraption().getSeatMapping().get(passenger.getUUID());
        if(passenger instanceof ServerPlayer player){if(seat!=null)dismountPlayer((ServerLevel)front.level(),player,seat,true);else player.stopRiding();}
        else if(seat!=null){if(!passengerLifecycle.retire(front,passenger,seat))return false;}
        else {try(var transfer=CarriagePlayerReferenceTransitions.preserveRootUnlink(passenger)){transfer.unlink();}}
      }
      return front.getPassengers().isEmpty();
    }

    public void alignEntity(CarriageContraptionEntity entity) {
      if (positionAnchor == null || rotationAnchors.either(Objects::isNull)) return;

      Vec3 positionVec = rotationAnchors.getFirst();
      Vec3 coupledVec = rotationAnchors.getSecond();

      double diffX = positionVec.x - coupledVec.x;
      double diffY = positionVec.y - coupledVec.y;
      double diffZ = positionVec.z - coupledVec.z;

      entity.prevYaw = entity.yaw;
      entity.prevPitch = entity.pitch;

      if (!entity.level().isClientSide()) {
        Vec3 lookahead =
            positionAnchor.add(positionAnchor.subtract(entity.position()).normalize().scale(16));

        for (Entity e : entity.getPassengers()) {
          if (!(e instanceof Player)) continue;
          if (e.distanceToSqr(entity) > 32 * 32) continue;
          if (CarriageEntityHandler.isActiveChunk(entity.level(), BlockPos.containing(lookahead)))
            break;
          train.carriageWaitingForChunks = id;
          return;
        }

        if (entity.getPassengers().stream().anyMatch(p -> p instanceof Player)) {}

        if (train.carriageWaitingForChunks == id) train.carriageWaitingForChunks = -1;

        entity.setServerSidePrevPosition();
      }

      entity.setPos(positionAnchor);
      entity.yaw = (float) (Mth.atan2(diffZ, diffX) * 180 / Math.PI) + 180;
      entity.pitch =
          (float) (Math.atan2(diffY, Math.sqrt(diffX * diffX + diffZ * diffZ)) * 180 / Math.PI)
              * -1;
      entity.setAngles(entity.yaw, entity.pitch);

      if (!entity.firstPositionUpdate) return;

      entity.xo = entity.getX();
      entity.yo = entity.getY();
      entity.zo = entity.getZ();
      entity.prevYaw = entity.yaw;
      entity.prevPitch = entity.pitch;
    }
  }
}
