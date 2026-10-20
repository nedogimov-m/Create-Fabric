package com.simibubi.create.content.trains.entity;

import com.google.common.base.Strings;
import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionAssemblyGraph;
import com.simibubi.create.content.contraptions.ContraptionNetworking;
import com.simibubi.create.content.contraptions.ContraptionOrientation;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.trains.TrainConfig;
import com.simibubi.create.content.trains.TrainHUDUpdatePacket;
import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.content.trains.entity.Carriage.DimensionalCarriageEntity;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphHelper;
import com.simibubi.create.content.trains.station.GlobalStation;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashSet;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;
import net.createmod.catnip.data.Couple;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Genuine source train frontend with native poses/custody and public graph-point snapshots.
 * Staging: native client effects/render/HUD/controls actor registration remain in progress.
 */
public class CarriageContraptionEntity extends OrientedContraptionEntity {
  private static final EntityDataAccessor<String> TRAIN_ID =
      SynchedEntityData.defineId(CarriageContraptionEntity.class, EntityDataSerializers.STRING);
  private static final EntityDataAccessor<Integer> CARRIAGE_INDEX =
      SynchedEntityData.defineId(CarriageContraptionEntity.class, EntityDataSerializers.INT);
  public float yaw, pitch, prevYaw, prevPitch;
  private TrainClientRegistry.ClientEffects clientEffects;
  private CarriageSyncData appliedData;
  private TrackGraph appliedGraph;
  private Carriage appliedCarriage;
  private UUID activeDriver;
  private BlockPos activeControls;
  private long lastControlTick = Long.MIN_VALUE;

  private static final EntityDataAccessor<CarriageSyncData> CARRIAGE_DATA =
      SynchedEntityData.defineId(CarriageContraptionEntity.class, CarriageSyncData.SERIALIZER);
  private static final EntityDataAccessor<String> TRACK_GRAPH =
      SynchedEntityData.defineId(CarriageContraptionEntity.class, EntityDataSerializers.STRING);
  private static final EntityDataAccessor<Boolean> SCHEDULED =
      SynchedEntityData.defineId(CarriageContraptionEntity.class, EntityDataSerializers.BOOLEAN);

  public UUID trainId;
  public int carriageIndex;

  private Carriage carriage;
  public boolean validForRender;
  public boolean movingBackwards;

  public boolean leftTickingChunks;
  public boolean firstPositionUpdate;

  private boolean arrivalSoundPlaying;
  private boolean arrivalSoundReversed;
  private int arrivalSoundTicks;

  private Vec3 serverPrevPos;

  public CarriageContraptionEntity(
      EntityType<? extends CarriageContraptionEntity> type, Level world) {
    super(type, world);
    validForRender = false;
    firstPositionUpdate = true;
    arrivalSoundTicks = Integer.MIN_VALUE;
  }

  @Override
  protected boolean isLocalClientAuthoritative() {
    return true;
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(CARRIAGE_DATA, new CarriageSyncData());
    builder.define(TRACK_GRAPH, "");
    builder.define(TRAIN_ID, "");
    builder.define(CARRIAGE_INDEX, -1);
    builder.define(SCHEDULED, false);
  }

  public void syncCarriage() {
    CarriageSyncData carriageData = getCarriageData();
    if (carriageData == null) return;
    if (carriage == null) return;
    carriageData.update(this, carriage);
  }

  @Override
  public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
    super.onSyncedDataUpdated(key);

    if (!level().isClientSide()) return;

    if (getContraption() instanceof CarriageContraption) bindCarriage();

    if (TRACK_GRAPH.equals(key)) updateTrackGraph();

    if (CARRIAGE_DATA.equals(key)) {
      CarriageSyncData carriageData = getCarriageData();
      if (carriageData == null) return;
      if (carriage == null) return;
      applyCarriageData();
    }
  }

  public CarriageSyncData getCarriageData() {
    return entityData.get(CARRIAGE_DATA);
  }

  public boolean hasSchedule() {
    return entityData.get(SCHEDULED);
  }

  public void setServerSidePrevPosition() {
    serverPrevPos = position();
  }

  public Vec3 getPrevPositionVec() {
    if (!level().isClientSide() && serverPrevPos != null) return serverPrevPos;
    return new Vec3(xo, yo, zo);
  }

  public boolean isLocalCoordWithin(BlockPos localPos, int min, int max) {
    if (!(getContraption() instanceof CarriageContraption cc)) return false;
    Direction facing = cc.getAssemblyDirection();
    Axis axis = facing.getClockWise().getAxis();
    int coord =
        axis.choose(localPos.getZ(), localPos.getY(), localPos.getX())
            * -facing.getAxisDirection().getStep();
    return coord >= min && coord <= max;
  }

  /** Empty accepted root; native forest removal binds the real captured body afterwards. */
  public static CarriageContraptionEntity reserve(
      net.minecraft.server.level.ServerLevel world, CarriageContraption contraption) {
    if (contraption.phase() != Contraption.Phase.CAPTURED)
      throw new IllegalArgumentException("Actual freshly captured carriage body required");
    var entity = new CarriageContraptionEntity(AllEntityTypes.CARRIAGE_CONTRAPTION, world);
    entity.setInitialOrientation(contraption.getAssemblyDirection().getClockWise());
    entity.setAngles(entity.getInitialYaw(), 0);
    entity.snapTo(Vec3.atLowerCornerOf(contraption.anchor).add(.5, 0, .5));
    if (!world.addFreshEntity(entity) || world.getEntity(entity.getUUID()) != entity) return null;
    return entity;
  }

  public static CarriageContraptionEntity create(Level world, CarriageContraption contraption) {
    CarriageContraptionEntity entity =
        new CarriageContraptionEntity(AllEntityTypes.CARRIAGE_CONTRAPTION, world);
    entity.setContraption(contraption);
    entity.setInitialOrientation(contraption.getAssemblyDirection().getClockWise());
    entity.setAngles(entity.getInitialYaw(), 0);
    entity.prevYaw = entity.yaw;
    entity.prevPitch = entity.pitch;
    return entity;
  }

  @Override
  public void tick() {
    super.tick();
    if(!level().isClientSide()&&isIndependentNativeOwner()&&hasOwnedStructure()){
      capturedRootReferences.adopt();passengerLifecycleForNative().load(this,position(),Integer.MIN_VALUE,Integer.MAX_VALUE);
    }

    if (contraption instanceof CarriageContraption cc)
      for (Entity entity : getPassengers()) {
        if (entity instanceof Player) continue;
        BlockPos seatOf = cc.getSeatOf(entity.getUUID());
        if (seatOf == null) continue;
        if (cc.conductorSeats.get(seatOf) == null) continue;
        alignPassenger(entity);
      }
  }

  public void setBlock(BlockPos localPos, StructureBlockInfo newInfo) {
    if (level().isClientSide()
        || carriage == null
        || !localPos.equals(newInfo.pos())
        || !hasOwnedStructure()) return;
    if (!getContraption().getBlocks().containsKey(localPos))
      throw new IllegalArgumentException("Cannot update a block outside the actual Carriage body");
    carriage.forEachPresentEntity(
        front -> {
          var body = front.getContraption();
          if (body == null || !body.getBlocks().containsKey(localPos))
            throw new IllegalStateException("Actual dimensional Carriage bodies disagree");
          body.getBlocks()
              .put(
                  localPos,
                  new StructureBlockInfo(
                      newInfo.pos(),
                      newInfo.state(),
                      newInfo.nbt() == null ? null : newInfo.nbt().copy()));
          for (var watcher : PlayerLookup.tracking(front))
            ContraptionNetworking.sendFull(front, watcher);
        });
  }

  @Override
  protected void tickContraption() {
    if (nonDamageTicks > 0) nonDamageTicks--;
    if (!(contraption instanceof CarriageContraption cc)) return;

    if (level().isClientSide()) bindCarriage();
    if (carriage == null) {
      if (level().isClientSide()) bindCarriage();
      // Real Train data can arrive before/after the native entity/body snapshot.
      else if (level() instanceof net.minecraft.server.level.ServerLevel server) {
        var train = trainId == null ? null : TrainManager.get(server).get(trainId);
        if (train != null && carriageIndex >= 0 && carriageIndex < train.carriages.size())
          setCarriage(train.carriages.get(carriageIndex));
      }
      if (!level().isClientSide() && carriage == null) recoverIndependent();
      return;
    }

    if (level() instanceof net.minecraft.server.level.ServerLevel server
        && TrainManager.get(server).get(carriage.train.id) != carriage.train) {
      recoverIndependent();
      return;
    }

    if (!level().isClientSide() && carriage.train.isRestoring()) {
      activeDriver = null;
      activeControls = null;
      return;
    }
    tickActors();
    boolean isStalled = isStalled();
    carriage.stalled = isStalled;

    CarriageSyncData carriageData = getCarriageData();

    if (!level().isClientSide()) {

      entityData.set(SCHEDULED, carriage.train.runtime.getSchedule() != null);

      boolean shouldCarriageSyncThisTick =
          carriage.train.shouldCarriageSyncThisTick(
              level().getGameTime(), getType().updateInterval());
      if (shouldCarriageSyncThisTick && carriageData.isDirty()) {
        entityData.set(CARRIAGE_DATA, carriageData.copy(), true);
        carriageData.setDirty(false);
      }

      Navigation navigation = carriage.train.navigation;
      if (navigation.announceArrival
          && Math.abs(navigation.distanceToDestination) < 60
          && carriageIndex
              == (carriage.train.speed < 0 ? carriage.train.carriages.size() - 1 : 0)) {
        navigation.announceArrival = false;
        arrivalSoundPlaying = true;
        arrivalSoundReversed = carriage.train.speed < 0;
        arrivalSoundTicks = Integer.MIN_VALUE;
      }

      if (arrivalSoundPlaying) tickArrivalSound(cc);

      entityData.set(
          TRACK_GRAPH, carriage.train.graph == null ? "" : carriage.train.graph.id.toString());

      level().gameEvent(this, GameEvent.RESONATE_8, this.position());

      return;
    }

    DimensionalCarriageEntity dce = carriage.getDimensional(level());
    if (tickCount % 10 == 0) updateTrackGraph();

    if (!dce.pointsInitialised) return;

    carriageData.approach(this, carriage, 1f / getType().updateInterval());

    if (!carriage.train.derailed) carriage.updateContraptionAnchors();

    xo = getX();
    yo = getY();
    zo = getZ();

    dce.alignEntity(this);

    if (clientEffects == null) clientEffects = TrainClientRegistry.createEffects(this);
    clientEffects.tick(dce);

    double distanceTo = 0;
    if (!firstPositionUpdate) {
      Vec3 diff = position().subtract(xo, yo, zo);
      Vec3 relativeDiff = CarriageBogey.rotate(diff, yaw, Axis.Y);
      double signum = Math.signum(-relativeDiff.x);
      distanceTo = diff.length() * signum;
      movingBackwards = signum < 0;
    }

    carriage.bogeys.getFirst().updateAngles(this, distanceTo);
    if (carriage.isOnTwoBogeys()) carriage.bogeys.getSecond().updateAngles(this, distanceTo);

    firstPositionUpdate = false;
    validForRender = true;
  }

  private void bindCarriage() {
    if (!level().isClientSide()) return;
    var value = entityData.get(TRAIN_ID);
    int index = entityData.get(CARRIAGE_INDEX);
    if (value.isEmpty() || index < 0) return;
    var id = UUID.fromString(value);
    var train = TrainClientRegistry.get(level(), id);
    if (train == null || index >= train.carriages.size()) {
      validForRender = false;
      if (clientEffects != null) {
        clientEffects.stop();
        clientEffects = null;
      }
      if (carriage != null) {
        var old = carriage.getDimensionalIfPresent(level().dimension());
        if (old != null && old.entity.get() == this) old.entity.clear();
      }
      carriage = null;
      return;
    }
    var next = train.carriages.get(index);
    if (carriage != next) {
      if (carriage != null) {
        var old = carriage.getDimensionalIfPresent(level().dimension());
        if (old != null && old.entity.get() == this) old.entity.clear();
      }
      if (clientEffects != null) {
        clientEffects.stop();
        clientEffects = null;
      }
      carriage = next;
      trainId = id;
      carriageIndex = index;
      var dimensional = carriage.getDimensional(level());
      var current = dimensional.entity.get();
      if (current != null && current != this && !current.isRemoved())
        throw new IllegalStateException(
            "Two native fronts cannot own one actual dimensional carriage");
      dimensional.entity = new WeakReference<>(this);
      dimensional.pivot = null;
      ((CarriageContraption) getContraption()).swapStorageAfterAssembly(this);
      setClientRenderState(null);
    }
    updateTrackGraph();
    applyCarriageData();
    carriage.updateContraptionAnchors();
    carriage.getDimensional(level()).updateRenderedCutoff();
  }

  private void applyCarriageData() {
    if (carriage == null) return;
    var data = getCarriageData();
    var graph = carriage.train.graph;
    if (data == appliedData
        && graph == appliedGraph
        && carriage == appliedCarriage
        && carriage.getDimensional(level()).pointsInitialised) return;
    if (data.apply(this, carriage)) {
      appliedData = data;
      appliedGraph = graph;
      appliedCarriage = carriage;
    }
  }

  private void tickArrivalSound(CarriageContraption cc) {
    List<Carriage> carriages = carriage.train.carriages;

    if (arrivalSoundTicks == Integer.MIN_VALUE) {
      int carriageCount = carriages.size();
      Integer tick = null;

      for (int index = 0; index < carriageCount; index++) {
        int i = arrivalSoundReversed ? carriageCount - 1 - index : index;
        Carriage carriage = carriages.get(i);
        CarriageContraptionEntity entity = carriage.getDimensional(level()).entity.get();
        if (entity == null || !(entity.contraption instanceof CarriageContraption otherCC)) break;
        tick =
            arrivalSoundReversed ? otherCC.soundQueue.lastTick() : otherCC.soundQueue.firstTick();
        if (tick != null) break;
      }

      if (tick == null) {
        arrivalSoundPlaying = false;
        return;
      }

      arrivalSoundTicks = tick;
    }

    if (tickCount % 2 == 0) return;

    boolean keepTicking = false;
    for (Carriage c : carriages) {
      CarriageContraptionEntity entity = c.getDimensional(level()).entity.get();
      if (entity == null || !(entity.contraption instanceof CarriageContraption otherCC)) continue;
      keepTicking |= otherCC.soundQueue.tick(entity, arrivalSoundTicks, arrivalSoundReversed);
    }

    if (!keepTicking) {
      arrivalSoundPlaying = false;
      return;
    }

    arrivalSoundTicks += arrivalSoundReversed ? -1 : 1;
  }

  @Override
  public void tickActors() {
    super.tickActors();
  }

  @Override
  protected boolean isActorActive(MovementContext context, MovementBehaviour actor) {
    if (!(contraption instanceof CarriageContraption cc)) return false;
    if (!super.isActorActive(context, actor)) return false;
    return cc.notInPortal() || level().isClientSide();
  }

  @Override
  public void onClientRemoval() {
    super.onClientRemoval();
    entityData.set(CARRIAGE_DATA, new CarriageSyncData());
    if (carriage != null) {
      DimensionalCarriageEntity dce = carriage.getDimensional(level());
      dce.pointsInitialised = false;
      carriage.leadingBogey().couplingAnchors.replace(ignored -> null);
      carriage.trailingBogey().couplingAnchors.replace(ignored -> null);
    }
    firstPositionUpdate = true;
    if (clientEffects != null) {
      clientEffects.stop();
      clientEffects = null;
    }
  }

  private final CarriageCapturedRootReferences capturedRootReferences=new CarriageCapturedRootReferences(this);
  private final Map<Integer,CompoundTag> independentPassengerGraphs=new LinkedHashMap<>();
  private final CarriagePassengerLifecycle independentPassengerLifecycle=new CarriagePassengerLifecycle(new CarriagePassengerLifecycle.Owner(){
    public Map<Integer,CompoundTag> roots(){return independentPassengerGraphs;}
    public boolean current(){return isIndependentNativeOwner()&&CarriagePlayerReferenceTransitions.frontCurrent(CarriageContraptionEntity.this);}
    public boolean owns(CarriageContraptionEntity front){return front==CarriageContraptionEntity.this&&hasOwnedStructure();}
    public void dirty(){}
  });
  CarriagePassengerLifecycle passengerLifecycleForNative(){return carriage==null?independentPassengerLifecycle:carriage.passengerLifecycle();}
  final void transferIndependentPassengers(Carriage target){independentPassengerLifecycle.transferIndependentTo(target.passengerLifecycle(),this);}
  public final boolean ownsNativePassengerGraph(){return hasOwnedStructure();}
  /** Real ENTITY_LOAD has published the native owner; per-node material still uses actual insertion proofs. */
  public final void resumeIndependentPassengers(){if(!level().isClientSide()&&isIndependentNativeOwner()&&hasOwnedStructure()){capturedRootReferences.adopt();passengerLifecycleForNative().load(this,position(),Integer.MIN_VALUE,Integer.MAX_VALUE);}}


  public final boolean adoptCapturedOriginalRoots(){capturedRootReferences.captureInitials();boolean initial=initializeCapturedPassengers();return initial&&capturedRootReferences.adopt();}
  public final CarriageCapturedRootReferences capturedRootReferences(){return capturedRootReferences;}
  /** World-owned restored blocks receive only original-root/parent UUID intents before native eject. */
  public final void preparePassengerWorldHandoff(ServerLevel world,StructureTransform transform){
    if(level()!=world||getContraption()==null||getContraption().phase()!=Contraption.Phase.DISASSEMBLED||getContraption().getStorage().ownsContents())throw new IllegalStateException("Actual world-restored carriage source required");
    var references=CarriagePlayerReferences.get(world.getServer());
    passengerLifecycleForNative().worldReferences(world,this,transform);
    for(var root:getPassengers()){
      var local=getContraption().getSeatOf(root.getUUID());if(local==null)continue;
      if(!(root instanceof Player)&&!references.worldHandoff(world,this,root.getUUID(),transform.apply(local)))throw new IllegalStateException("Actual nested player world handoff refused");
    }
    var originals=capturedRootReferences.pending();
    var handoff=new LinkedHashMap<UUID,Integer>(originals);
    if(!isGloballyManaged()){
      var direct=passengerLifecycleForNative().pendingDirectPlayers(this);if(direct==null)throw new IllegalStateException("Actual NPC material must be restored before independent world handoff");
      for(var entry:direct.entrySet()){var previous=handoff.putIfAbsent(entry.getKey(),entry.getValue());if(previous!=null&&!previous.equals(entry.getValue()))throw new IllegalStateException("Ambiguous original world root seat");}
    }
    com.simibubi.create.content.contraptions.ContraptionPassengerHandoff.finishPlayers(world,getContraption(),transform,handoff);
    capturedRootReferences.clearWorldTransferred();if(!isGloballyManaged())passengerLifecycleForNative().releaseWorldReferences(this);
  }


  @Override
  protected void addAdditionalSaveData(ValueOutput output) {
    if(!level().isClientSide()&&!isGloballyManaged()&&!isIndependentNativeOwner())throw new IllegalStateException("Retired global frontend cannot publish independent native cargo/passenger copies");
    if(isIndependentNativeOwner()){
      capturedRootReferences.captureInitials();
      if(!isInitializingCapturedPassengers())initializeCapturedPassengers();
      capturedRootReferences.write(output);
      if(hasOwnedStructure()&&level() instanceof ServerLevel world&&CarriageNativeSources.current(this)){
        var lifecycle=passengerLifecycleForNative();lifecycle.capture(this);var graphs=lifecycle.write(this);var encoded=new CompoundTag();graphs.forEach((seat,graph)->encoded.put("Seat"+seat,graph));
        if(!encoded.isEmpty())output.store("IndependentPassengers",CompoundTag.CODEC,encoded);
      }
    }
    super.addAdditionalSaveData(output);
    if (isIndependentNativeOwner()) {
      output.putBoolean("IndependentCarriage", true);
      return;
    }
    if (trainId == null || carriageIndex < 0)
      throw new IllegalStateException("Actual Train/Carriage identity is missing");
    output.store("TrainId", UUIDUtil.CODEC, trainId);
    output.putInt("CarriageIndex", carriageIndex);
  }

  @Override
  protected void readAdditionalSaveData(ValueInput input) {
    boolean independent = input.getBooleanOr("IndependentCarriage", false);
    var encoded=input.read(CarriageCapturedRootReferences.strictOptional(CompoundTag.CODEC,"IndependentPassengers")).orElseThrow(()->new IllegalArgumentException("Malformed native independent passenger graph field")).orElseGet(CompoundTag::new);if(!independent&&!encoded.isEmpty())throw new IllegalArgumentException("Managed carriage cannot duplicate its global NPC material");
    var incoming=new LinkedHashMap<Integer,CompoundTag>();for(var key:encoded.keySet()){
      if(!key.startsWith("Seat"))throw new IllegalArgumentException("Unknown independent native passenger seat");int seat=Integer.parseInt(key.substring(4));if(seat<0||!key.equals("Seat"+seat)||incoming.putIfAbsent(seat,encoded.getCompound(key).orElseThrow())!=null)throw new IllegalArgumentException("Ambiguous independent native passenger seat");
    }
    // Validate every NPC/player/body identity before the superclass takes native cargo ownership.
    independentPassengerLifecycle.read(incoming,java.util.Set.of(getUUID()));capturedRootReferences.read(input);
    var pendingOriginals=capturedRootReferences.persisted();var seats=new HashSet<Integer>(incoming.keySet());var identities=new HashSet<UUID>();identities.add(getUUID());
    for(var graph:incoming.values())identities.addAll(CarriagePassengerGraph.fromLegacy(graph,UUID.randomUUID()).identities());
    for(var entry:pendingOriginals.entrySet())if(!seats.add(entry.getValue())||!identities.add(entry.getKey()))throw new IllegalArgumentException("Original world roots overlap paid graph/body identities or native seats");
    if(!seats.isEmpty())CarriagePassengerLifecycle.validateSavedSeatBounds(input.read("Contraption",CompoundTag.CODEC).orElseThrow(),seats);
    trainId = independent ? null : input.read("TrainId", UUIDUtil.CODEC).orElseThrow();
    carriageIndex = independent ? -1 : input.getIntOr("CarriageIndex", -1);
    if (!independent && carriageIndex < 0)
      throw new IllegalArgumentException("Negative actual Carriage index");
    entityData.set(TRAIN_ID, trainId == null ? "" : trainId.toString());
    entityData.set(CARRIAGE_INDEX, carriageIndex);
    super.readAdditionalSaveData(input);
    capturedRootReferences.validateSeats();
    for(int seat:independentPassengerGraphs.keySet())if(getContraption()==null||seat>=getContraption().getSeats().size())throw new IllegalArgumentException("Independent native graph seat outside its actual body");
    prevYaw = yaw;
    prevPitch = pitch;
  }

  public Component getContraptionName() {
    if (carriage != null) return carriage.train.name;
    Component contraptionName = Component.translatable("create.train");
    return contraptionName;
  }

  public Couple<Boolean> checkConductors() {
    Couple<Boolean> sides = Couple.create(false, false);
    if (!(contraption instanceof CarriageContraption cc)) return sides;

    sides.setFirst(cc.blockConductors.getFirst());
    sides.setSecond(cc.blockConductors.getSecond());

    for (Entity entity : getPassengers()) {
      if (entity instanceof Player) continue;
      BlockPos seatOf = cc.getSeatOf(entity.getUUID());
      if (seatOf == null) continue;
      Couple<Boolean> validSides = cc.conductorSeats.get(seatOf);
      if (validSides == null) continue;
      sides.setFirst(sides.getFirst() || validSides.getFirst());
      sides.setSecond(sides.getSecond() || validSides.getSecond());
    }

    return sides;
  }

  public boolean startControlling(BlockPos controlsLocalPos, Player player) {
    if (player == null
        || player.isSpectator()
        || carriage == null
        || carriage.train.derailed
        || !validControlPosition(controlsLocalPos, player)) return false;
    if (level().isClientSide()) return true;
    if (!level().isClientSide()) {
      long now = level().getGameTime();
      if (activeDriver != null
          && now - lastControlTick <= 10
          && !activeDriver.equals(player.getUUID())) return false;
      activeDriver = player.getUUID();
      activeControls = controlsLocalPos.immutable();
      lastControlTick = now;
    }
    if (carriage == null) return false;
    if (carriage.train.derailed) return false;

    Train train = carriage.train;
    if (train.runtime.getSchedule() != null && !train.runtime.paused) train.status.manualControls();
    train.navigation.cancelNavigation();
    train.runtime.paused = true;
    train.navigation.waitingForSignal = null;
    return true;
  }

  @Override
  public Component getDisplayName() {
    if (carriage == null) return Component.translatable("train");
    return carriage.train.name;
  }

  double navDistanceTotal = 0;
  int hudPacketCooldown = 0;

  public boolean control(
      BlockPos controlsLocalPos, Collection<Integer> heldControls, Player player) {
    if (carriage == null
        || carriage.train.derailed
        || !validControlPosition(controlsLocalPos, player)) return false;
    if (level().isClientSide()) return true;
    long now = level().getGameTime();
    if (!player.getUUID().equals(activeDriver)
        || !controlsLocalPos.equals(activeControls)
        || now - lastControlTick > 10) return false;
    lastControlTick = now;
    if (player.isSpectator()) return false;

    if (heldControls.stream().anyMatch(key -> key == null || key < 0 || key > 5)) return false;
    if (heldControls.contains(5)) {
      stopControlling(controlsLocalPos, player);
      return false;
    }

    StructureBlockInfo info = contraption.getBlocks().get(controlsLocalPos);
    Direction initialOrientation = getInitialOrientation().getCounterClockWise();
    boolean inverted = false;
    if (info != null && info.state().hasProperty(ControlsBlock.FACING))
      inverted = !info.state().getValue(ControlsBlock.FACING).equals(initialOrientation);

    if (hudPacketCooldown-- <= 0 && player instanceof ServerPlayer sp) {
      ServerPlayNetworking.send(sp, new TrainHUDUpdatePacket.Clientbound(carriage.train));
      hudPacketCooldown = 5;
    }

    int targetSpeed = 0;
    if (heldControls.contains(0)) targetSpeed++;
    if (heldControls.contains(1)) targetSpeed--;

    int targetSteer = 0;
    if (heldControls.contains(2)) targetSteer++;
    if (heldControls.contains(3)) targetSteer--;

    if (inverted) {
      targetSpeed *= -1;
      targetSteer *= -1;
    }

    if (targetSpeed != 0) carriage.train.burnFuel();

    boolean slow = inverted ^ targetSpeed < 0;
    boolean spaceDown = heldControls.contains(4);
    GlobalStation currentStation = carriage.train.getCurrentStation();
    if (currentStation != null && spaceDown) {
      sendPrompt(
          player,
          Component.translatable(
              "train.arrived_at",
              Component.literal(currentStation.name()).withStyle(s -> s.withColor(0x704630))),
          false);
      return true;
    }

    if (carriage.train.speedBeforeStall != null
        && targetSpeed != 0
        && Math.signum(carriage.train.speedBeforeStall) != Math.signum(targetSpeed)) {
      carriage.train.cancelStall();
    }

    if (currentStation != null && targetSpeed != 0) {
      stationMessage = false;
      sendPrompt(
          player,
          Component.translatable(
              "train.departing_from",
              Component.literal(currentStation.name()).withStyle(s -> s.withColor(0x704630))),
          false);
    }

    if (currentStation == null) {

      Navigation nav = carriage.train.navigation;
      if (nav.destination != null) {
        if (!spaceDown) nav.cancelNavigation();
        if (spaceDown) {
          double f = (nav.distanceToDestination / navDistanceTotal);
          int progress = (int) (Mth.clamp(1 - ((1 - f) * (1 - f)), 0, 1) * 30);
          boolean arrived = progress == 0;
          MutableComponent whiteComponent = Component.literal(Strings.repeat("|", progress));
          MutableComponent greenComponent = Component.literal(Strings.repeat("|", 30 - progress));

          int fromColor = 0x00_FFC244;
          int toColor = 0x00_529915;

          int mixedColor = net.minecraft.util.ARGB.srgbLerp(progress / 30f, toColor, fromColor);
          int targetColor = arrived ? toColor : 0x00_544D45;

          MutableComponent component =
              greenComponent
                  .withStyle(st -> st.withColor(mixedColor))
                  .append(whiteComponent.withStyle(st -> st.withColor(targetColor)));
          sendPrompt(player, component, true);
          carriage.train.manualTick = true;
          return true;
        }
      }

      double directedSpeed = targetSpeed != 0 ? targetSpeed : carriage.train.speed;
      GlobalStation lookAhead =
          nav.findNearestApproachable(
              !carriage.train.doubleEnded || (directedSpeed != 0 ? directedSpeed > 0 : !inverted));

      if (lookAhead != null) {
        if (spaceDown) {
          carriage.train.manualTick = true;
          nav.startNavigation(nav.findPathTo(lookAhead, -1));
          carriage.train.manualTick = false;
          navDistanceTotal = nav.distanceToDestination;
          return true;
        }
        displayApproachStationMessage(player, lookAhead);
      } else cleanUpApproachStationMessage(player);
    }

    carriage.train.manualSteer =
        targetSteer < 0
            ? SteerDirection.RIGHT
            : targetSteer > 0 ? SteerDirection.LEFT : SteerDirection.NONE;

    double topSpeed =
        carriage.train.maxSpeed() * TrainConfig.get(level()).manualTrainSpeedModifier();
    double cappedTopSpeed = topSpeed * carriage.train.throttle;

    if (carriage.getLeadingPoint().edge != null && carriage.getLeadingPoint().edge.isTurn()
        || carriage.getTrailingPoint().edge != null && carriage.getTrailingPoint().edge.isTurn())
      topSpeed = carriage.train.maxTurnSpeed();

    if (slow) topSpeed /= 4;
    carriage.train.targetSpeed = Math.min(topSpeed, cappedTopSpeed) * targetSpeed;

    boolean counteringAcceleration =
        Math.abs(Math.signum(targetSpeed) - Math.signum(carriage.train.speed)) > 1.5f;

    if (slow && !counteringAcceleration) carriage.train.backwardsDriver = player;

    carriage.train.manualTick = true;
    carriage.train.approachTargetSpeed(counteringAcceleration ? 2 : 1);
    return true;
  }

  private void sendPrompt(Player player, MutableComponent component, boolean shadow) {
    if (player instanceof ServerPlayer sp)
      ServerPlayNetworking.send(sp, new TrainPromptPacket(component, shadow));
  }

  boolean stationMessage = false;

  private void displayApproachStationMessage(Player player, GlobalStation station) {
    sendPrompt(
        player,
        Component.translatable(
            "contraption.controls.approach_station", Component.keybind("key.jump"), station.name()),
        false);
    stationMessage = true;
  }

  private void cleanUpApproachStationMessage(Player player) {
    if (!stationMessage) return;
    player.sendOverlayMessage(CommonComponents.EMPTY);
    stationMessage = false;
  }

  private void updateTrackGraph() {
    if (carriage == null || !level().isClientSide()) return;
    var value = entityData.get(TRACK_GRAPH);
    if (value.isEmpty()) {
      carriage.train.graph = null;
      carriage.train.derailed = true;
      return;
    }
    var id = UUID.fromString(value);
    var graph = TrackGraphHelper.getGraph(level(), id);
    if (graph == null) {
      if (carriage.train.graph != null && !carriage.train.graph.id.equals(id))
        carriage.train.graph = null;
      return;
    }
    carriage.train.graph = graph;
    carriage.train.derailed = false;
  }

  @Override
  public boolean shouldBeSaved() {
    // Only actually removed roots without a registered global owner enter native chunk storage.
    return hasOwnedStructure() && isIndependentNativeOwner();
  }

  public boolean isGloballyManaged() {
    if (level().isClientSide() || carriage == null || carriage.train == null) return false;
    var manager = carriage.train.trafficManager();
    return manager != null && manager.isCurrent() && manager.get(carriage.train.id) == carriage.train && carriage.train.carriages.contains(carriage);
  }

  /** Before genuine manager publication the actual accepted source remains natively independent. */
  public boolean isIndependentNativeOwner(){
    if(level().isClientSide())return false;
    return carriage==null?trainId==null:carriage.train!=null&&carriage.train.trafficManager()==null;
  }

  /**
   * Refused/partial assembly retains an actual native body until real source restoration succeeds.
   */
  public boolean recoverIndependent() {
    if(level().isClientSide()||!isIndependentNativeOwner()||!hasOwnedStructure())return false;
    capturedRootReferences.captureInitials();initializeCapturedPassengers();passengerLifecycleForNative().load(this,position(),Integer.MIN_VALUE,Integer.MAX_VALUE);
    if(passengerLifecycleForNative().pendingDirectPlayers(this)==null)return false;
    return ContraptionAssemblyGraph.restore(this);
  }

  @Override
  public boolean disassemble() {
    // Actual managed formations restore through CarriageDisassembly and its durable row receipts.
    if(!level().isClientSide()){
      if(!isIndependentNativeOwner())return false;capturedRootReferences.captureInitials();initializeCapturedPassengers();
      passengerLifecycleForNative().load(this,position(),Integer.MIN_VALUE,Integer.MAX_VALUE);if(passengerLifecycleForNative().pendingDirectPlayers(this)==null)return false;
    }
    return super.disassemble();
  }

  @Override
  public void remove(Entity.RemovalReason reason) {
    if (!level().isClientSide()
        && !isRemoved()
        && reason.shouldDestroy()
        && hasOwnedStructure()
        && !isIndependentNativeOwner()) return;
    super.remove(reason);
  }

  @Override
  public void onRemoval(Entity.RemovalReason reason) {
    super.onRemoval(reason);
    if(!level().isClientSide()&&isGloballyManaged()&&reason==Entity.RemovalReason.UNLOADED_TO_CHUNK){CarriageNativeSources.retired(this);carriage.nativeFrontendRemoved(this);}
    if (level().isClientSide()) {
      if (clientEffects != null) clientEffects.stop();
      clientEffects = null;
    } else if (getContraption() != null && !isGloballyManaged()) {
      // Native EntityStorage encoded the independent owner synchronously before its retirement.
      // The old native object is no longer authoritative; expire every held mounted handle.
      getContraption().getStorage().setOwned(false);
    }
  }

  @Override
  public boolean addSittingPassenger(Entity passenger, int seat) {
    if(!level().isClientSide()&&(!CarriagePlayerReferenceTransitions.frontCurrent(this)||!CarriagePassengerMountGuard.mayAdopt(passenger)))return false;
    if(!level().isClientSide())capturedRootReferences.captureInitials();
    try(var prepared=CarriagePlayerReferenceTransitions.prepareSeat(this,passenger,seat)){return super.addSittingPassenger(passenger,seat);}finally{if(!level().isClientSide())capturedRootReferences.pruneAccepted();}
  }

  public Carriage getCarriage() {
    return carriage;
  }

  public void setCarriage(Carriage carriage) {
    if (carriage == null
        || carriage.train == null
        || !(getContraption() instanceof CarriageContraption body))
      throw new IllegalArgumentException("Actual Train/Carriage/body custody required");
    int index = carriage.train.carriages.indexOf(carriage);
    if (index < 0) throw new IllegalArgumentException("Carriage is not owned by this actual Train");
    if (this.carriage != null && this.carriage != carriage)
      throw new IllegalStateException("Cannot replace an actual server front's cargo owner");
    this.carriage = carriage;
    trainId = carriage.train.id;
    carriageIndex = index;
    body.swapStorageAfterAssembly(this);
    entityData.set(TRAIN_ID, trainId.toString());
    entityData.set(CARRIAGE_INDEX, index);
    entityData.set(
        TRACK_GRAPH, carriage.train.graph == null ? "" : carriage.train.graph.id.toString());
    var dimensional = carriage.getDimensional(level());
    dimensional.pivot = null;
    carriage.updateContraptionAnchors();
    dimensional.updateRenderedCutoff();
  }

  public final boolean prepareNativePassengerUnload(){return isIndependentNativeOwner()||isGloballyManaged()&&carriage.prepareNativePassengerUnload(this);}
  public final boolean prepareNativeNpcRootUnload(Entity actual){return isGloballyManaged()&&carriage.prepareNativeNpcRootUnload(this,actual);}

  /** Native unload of a globally owned frontend, never ordinary destructive disassembly. */
  public void retireToCarriage(Carriage actual) {
    if (actual == null || carriage != actual || actual.train == null)
      throw new IllegalArgumentException("Foreign actual Carriage cannot retire this frontend");
    activeDriver = null;
    activeControls = null;
    validForRender = false;
    super.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
  }

  @Override
  public void setContraption(Contraption body) {
    if (!(body instanceof CarriageContraption))
      throw new IllegalArgumentException("Actual Carriage body required");
    super.setContraption(body);
    if (carriage != null) ((CarriageContraption) body).swapStorageAfterAssembly(this);
    if (level().isClientSide()) bindCarriage();
  }

  private boolean validControlPosition(BlockPos position, Player player) {
    if (!isAlive()
        || carriage == null
        || carriage.train.isRestoring()
        || !hasOwnedStructure()
        || player == null
        || !player.isAlive()
        || player.level() != level()
        || player.isSpectator()) return false;
    var info = getContraption().getBlocks().get(position);
    return info != null
        && info.state().getBlock() instanceof ControlsBlock
        && player.getEyePosition().distanceToSqr(toGlobalVector(Vec3.atCenterOf(position), 1))
            <= 64;
  }

  @Override
  protected void addPassenger(Entity passenger) {
    super.addPassenger(passenger);
    if (passenger instanceof Player)
      com.simibubi.create.content.contraptions.ContraptionPassengerData.mounted(passenger);
  }

  public java.util.Optional<UUID> getControllingPlayer() {
    if (activeDriver == null || !(level() instanceof net.minecraft.server.level.ServerLevel server))
      return java.util.Optional.empty();
    var player = server.getServer().getPlayerList().getPlayer(activeDriver);
    return isControlledBy(player)
        ? java.util.Optional.of(activeDriver)
        : java.util.Optional.empty();
  }

  public boolean isControlledBy(Player player) {
    return player != null
        && !level().isClientSide()
        && isGloballyManaged()
        && activeControls != null
        && player.getUUID().equals(activeDriver)
        && level().getGameTime() - lastControlTick <= 10
        && validControlPosition(activeControls, player);
  }

  public void stopControlling(BlockPos position, Player player) {
    if (player != null) stopControlling(position, player.getUUID());
  }

  public void stopControlling(BlockPos position, UUID originalPlayer) {
    if (originalPlayer != null
        && originalPlayer.equals(activeDriver)
        && position.equals(activeControls)) {
      activeDriver = null;
      activeControls = null;
      lastControlTick = Long.MIN_VALUE;
    }
  }

  @Override
  public void setAngles(float yaw, float pitch) {
    this.yaw = yaw;
    this.pitch = pitch;
    super.setAngles(yaw, pitch);
  }

  @Override
  public float getViewYRot(float partial) {
    return -ContraptionOrientation.lerp(prevYaw, yaw, partial);
  }

  @Override
  public float getViewXRot(float partial) {
    return ContraptionOrientation.lerp(prevPitch, pitch, partial);
  }

  @Override
  public Quaternionf getOrientationQuaternion(float partial) {
    return ContraptionOrientation.source(
        -getViewYRot(partial), getViewXRot(partial), getInitialYaw());
  }

  private void alignPassenger(Entity passenger) {
    if (!(passenger instanceof LivingEntity living) || passenger instanceof ArmorStand) return;
    var motion = getContactPointMotion(passenger.getEyePosition());
    if (motion.lengthSqr() < 1e-12) return;
    float angle =
        net.createmod.catnip.math.AngleHelper.angleLerp(
            .4f, living.getYRot(), (float) Math.toDegrees(-Mth.atan2(motion.x, motion.z)));
    living.setYRot(angle);
    if (level().isClientSide()) {
      living.setXRot(0);
      living.yBodyRot = angle;
      living.yHeadRot = angle;
    }
  }

  public void updateRenderedPortalCutoff() {
    setClientRenderState(null);
    if (clientEffects != null) clientEffects.portalCutoffChanged();
  }
}
