package com.simibubi.create.content.trains.station;

import com.mojang.serialization.Codec;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.content.trains.bogey.*;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.utility.*;
import java.util.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** Genuine native station owner, point binding, configuration and physical assembly preflight. */
public final class StationBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity
    implements TrackEdgePoint.MigrationReceiver,
        TrackTargetingBlockItem.Target,
        com.simibubi.create.api.contraption.transformable.TransformableBlockEntity {
  private final TrackTargetingBehaviour<GlobalStation> targeting;
  public final com.simibubi.create.content.logistics.depot.DepotBehaviour depotBehaviour;
  private boolean originalPointConfigurationPending;
  private int failedCarriageIndex = -1;
  private UUID autoSchedulePresentTrain;
  public int failedCarriageIndex(){return failedCarriageIndex;}
  public net.minecraft.world.item.ItemStack getAutoSchedule(){return depotBehaviour.getHeldItemStack();}

  private StationTrainAssembly.Status trainAssemblyStatus=StationTrainAssembly.Status.NOT_CHECKED;
  private com.simibubi.create.content.contraptions.AssemblyException trainAssemblyFailure;
  private HolderLookup.Provider loadedRegistries;
  public StationTrainAssembly.Status trainAssemblyStatus(){return trainAssemblyStatus;}
  public com.simibubi.create.content.contraptions.AssemblyException trainAssemblyFailure(){return trainAssemblyFailure;}
  public boolean tryAssemble(Player player){
    if(pendingAssemblyEntry!=null)return false;
    var result=StationTrainAssembly.assemble(this,player);
    trainAssemblyStatus=result.status();trainAssemblyFailure=result.failure();
    revision=Math.incrementExact(revision);setChanged();sync();return result.assembled();
  }
  public boolean tryDisassemble(Player player){
    if(!StationPermissions.canEdit(player,this,true)||getStation()==null)return false;
    var train=getStation().getPresentTrain();
    if(train==null||train.trafficManager()==null||!train.canDisassemble())return false;
    var selected=targeting.selection();
    if(selected==null||!targeting.isOrthogonal())return false;
    var axis=selected.axis().scale(selected.direction().getStep());
    var direction=Direction.getApproximateNearest(axis.x,axis.y,axis.z);
    boolean complete=train.disassemble(direction,selected.target().pos().above(),player);
    trainAssemblyStatus=complete?StationTrainAssembly.Status.DISASSEMBLED:train.isRestoring()?StationTrainAssembly.Status.DISASSEMBLY_PENDING:StationTrainAssembly.Status.FAILED;
    revision=Math.incrementExact(revision);setChanged();sync();return complete||train.isRestoring();
  }
  public void assemblyCompleted(com.simibubi.create.content.trains.entity.Train train){
    if(level==null||level.isClientSide()||train.trafficManager()==null||train.trafficManager().get(train.id)!=train||train.getCurrentStation()!=getStation())
      throw new IllegalArgumentException("Actual adopted Train/owning station required");
    lastDisassembledTrainName=null;lastDisassembledMapColorIndex=0;lastDisassembledTrainId=null;
    var current=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,worldPosition);
    if(current.getBlock() instanceof StationBlock)level.setBlock(worldPosition,current.setValue(StationBlock.ASSEMBLING,false),Block.UPDATE_ALL);
    suspendArea();getStation().configure(this,stationName,false);
    trainAssemblyStatus=StationTrainAssembly.Status.ASSEMBLED;trainAssemblyFailure=null;
    revision=Math.incrementExact(revision);setChanged();sync();
  }
  private String stationName = "Track Station";
  private record AssemblyEntryIntent(UUID train,UUID point,UUID requester,TrackTargetingSelection selection,String name,DoorControl doors){
    private static final Codec<AssemblyEntryIntent> CODEC=com.mojang.serialization.codecs.RecordCodecBuilder.create(i->i.group(
        UUIDUtil.CODEC.fieldOf("Train").forGetter(AssemblyEntryIntent::train),
        UUIDUtil.CODEC.fieldOf("Point").forGetter(AssemblyEntryIntent::point),
        UUIDUtil.CODEC.fieldOf("Requester").forGetter(AssemblyEntryIntent::requester),
        TrackTargetingSelection.CODEC.fieldOf("Selection").forGetter(AssemblyEntryIntent::selection),
        Codec.STRING.fieldOf("Name").forGetter(AssemblyEntryIntent::name),
        Codec.STRING.xmap(DoorControl::valueOf,DoorControl::name).fieldOf("Doors").forGetter(AssemblyEntryIntent::doors)).apply(i,AssemblyEntryIntent::new));
    private AssemblyEntryIntent{
      Objects.requireNonNull(train);Objects.requireNonNull(point);Objects.requireNonNull(requester);Objects.requireNonNull(selection);Objects.requireNonNull(doors);
      if(name==null||name.isBlank()||name.length()>256)throw new IllegalArgumentException("Invalid authorized native station entry name");
    }
  }
  private AssemblyEntryIntent pendingAssemblyEntry;
  private UUID lastDisassembledTrainId;
  public boolean stationModeEntryPending(){return pendingAssemblyEntry!=null;}
  public void rememberDisassembledTrain(com.simibubi.create.content.trains.entity.Train train){
    if(train==null||train.trafficManager()==null||train.trafficManager().get(train.id)!=train
        ||train.getCurrentStation()!=getStation()||!train.hasCompletedDisassembly())
      throw new IllegalArgumentException("Actual fully consumed formation and returned schedule required for station completion receipt");
    lastDisassembledTrainId=train.id;
    rememberDisassembledTrain(train.name,train.mapColorIndex);
  }
  private Component lastDisassembledTrainName;
  private int lastDisassembledMapColorIndex;

  public Component lastDisassembledTrainName(){return lastDisassembledTrainName==null?null:lastDisassembledTrainName.copy();}
  public int lastDisassembledMapColorIndex(){return lastDisassembledMapColorIndex;}
  public void rememberDisassembledTrain(Component name,int mapColorIndex){
    if(level==null||level.isClientSide()||getStation()==null||!getStation().ownedBy(this))
      throw new IllegalStateException("Actual server station owner required for restored Train metadata");
    lastDisassembledTrainName=java.util.Objects.requireNonNull(name).copy();
    lastDisassembledMapColorIndex=mapColorIndex;
    trainAssemblyStatus=StationTrainAssembly.Status.DISASSEMBLED;
    revision=Math.incrementExact(revision);
    setChanged();
    sync();
  }
  private DoorControl doorControl = DoorControl.ALL;
  private long revision;
  private int ticks;
  private float flag, previousFlag, flagTarget;
  /** Client display metadata; this record grants no train, station or assembly authority. */
  public record ClientTrainPresence(UUID imminentTrain, boolean present, boolean canDisassemble,
      boolean backwards, boolean hasSchedule, boolean hasAutoSchedule) {
    public static final ClientTrainPresence EMPTY =
        new ClientTrainPresence(null, false, false, false, false, false);
    public ClientTrainPresence {
      if (imminentTrain == null && (present || canDisassemble || backwards || hasSchedule || hasAutoSchedule))
        throw new IllegalArgumentException("Station client flags require the actual supplied train identifier");
    }
  }
  private ClientTrainPresence clientTrainPresence = ClientTrainPresence.EMPTY;
  public ClientTrainPresence clientTrainPresence() { return clientTrainPresence; }
  /** Client packets are display evidence only; server redstone resolves the actual canonical train. */
  public int comparatorOutput() {
    if(level==null||isRemoved()||!LoadedLevelAccess.isLoaded(level,worldPosition)
        ||LoadedLevelAccess.blockEntity(level,worldPosition)!=this)return 0;
    return (level.isClientSide()?clientTrainPresence.present():actualTrainPresence().present())?15:0;
  }
  private ClientTrainPresence actualTrainPresence() {
    var station = getStation();
    var train = station == null ? null : station.getImminentTrain();
    if (train == null || train.trafficManager() == null || train.trafficManager().get(train.id) != train)
      return ClientTrainPresence.EMPTY;
    boolean present = train.getCurrentStation() == station;
    boolean hasSchedule = present && train.runtime.getSchedule() != null;
    return new ClientTrainPresence(train.id, present, present && train.canDisassemble(),
        train.currentlyBackwards, hasSchedule, hasSchedule && train.runtime.isAutoSchedule);
  }
  private StationAssemblyArea area;
  private Direction publishedDirection;
  private int publishedLength;
  private List<Integer> publishedBogeys = List.of();
  private UUID publishedGraph;
  private long publishedGraphRevision = -1;
  private StationAssemblyPreflight.Status lastStatus = StationAssemblyPreflight.Status.NOT_CHECKED;

  public StationBlockEntity(BlockPos pos, BlockState state) {
    super(AllBlockEntityTypes.TRACK_STATION, pos, state);
    targeting =
        new TrackTargetingBehaviour<>(this, StationEdgePoints.STATION, this::targetingChanged);
    depotBehaviour = new com.simibubi.create.content.logistics.depot.DepotBehaviour(this)
        .onlyAccepts(stack -> stack.is(com.simibubi.create.AllItems.SCHEDULE))
        .withCallback(stack -> applyAutoSchedule());
  }

  /** A station's real held schedule is a reusable template; it is never debited into Train cargo. */
  public boolean applyAutoSchedule(){
    if (!(level instanceof ServerLevel server) || !depotBehaviour.isActiveWorldOwner()) return false;
    var stack=getAutoSchedule();
    if(!stack.is(com.simibubi.create.AllItems.SCHEDULE))return false;
    var schedule=com.simibubi.create.content.trains.schedule.ScheduleItem.getSchedule(level.registryAccess(),stack);
    var station=getStation();
    if(schedule==null||schedule.entries.isEmpty()||station==null)return false;
    var train=station.getImminentTrain();
    if(train==null||train.isRestoring()||train.getCurrentStation()!=station)return false;
    award(com.simibubi.create.foundation.advancement.AllAdvancements.CONDUCTOR);
    train.runtime.setSchedule(schedule,true);
    level.playSound(null,worldPosition,com.simibubi.create.AllSoundEvents.CONFIRM,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
    var at=net.minecraft.world.phys.Vec3.atBottomCenterOf(worldPosition.above());
    server.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,at.x,at.y,at.z,8,.35,.05,.35,1);
    server.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,at.x,at.y+.25,at.z,10,.05,1,.05,.005);
    return true;
  }

  public TrackTargetingBehaviour<GlobalStation> targeting() {
    return targeting;
  }

  public GlobalStation getStation() {
    var point = targeting.getEdgePoint();
    return point != null && point.ownedBy(this) ? point : null;
  }

  public String stationName() {
    return stationName;
  }

  public DoorControl doorControl() {
    return doorControl;
  }

  public long configurationRevision() {
    return revision;
  }

  public StationAssemblyPreflight.Status lastStatus() {
    return lastStatus;
  }

  public float flagValue(float partial) {
    return net.minecraft.util.Mth.lerp(partial, previousFlag, flag);
  }

  public float flagTarget() {
    return flagTarget;
  }

  public boolean flagSettled() {
    return Math.abs(flag - flagTarget) < 1e-6;
  }

  private void tickFlag() {
    previousFlag = flag;
    float previousTarget = flagTarget;
    if (flagTarget == 0 || flagSettled()) {
      float next = clientTrainPresence.present() || isAssembling() ? 1 : 0;
      if (next != flagTarget) {
        flagTarget = next;
        if (next == 1)
          level.playLocalSound(
              worldPosition,
              com.simibubi.create.AllSoundEvents.CONTRAPTION_DISASSEMBLE,
              net.minecraft.sounds.SoundSource.BLOCKS,
              1,
              2,
              true);
      }
    }
    boolean crossed = flag > .15f;
    flag = net.minecraft.util.Mth.approach(flag, flagTarget, .1f);
    if (previousTarget == 0 && crossed != (flag > .15f))
      level.playLocalSound(
          worldPosition,
          com.simibubi.create.AllSoundEvents.CONTRAPTION_ASSEMBLE,
          net.minecraft.sounds.SoundSource.BLOCKS,
          .75f,
          1.5f,
          true);
  }

  public boolean isAssembling() {
    return getBlockState().hasProperty(StationBlock.ASSEMBLING)
        && getBlockState().getValue(StationBlock.ASSEMBLING);
  }

  public Direction publishedDirection() {
    return publishedDirection;
  }

  public int publishedAreaLength() {
    return publishedLength;
  }

  public List<Integer> publishedBogeys() {
    return publishedBogeys;
  }

  public boolean publishedContains(BlockPos pos) {
    if (!isAssembling() || publishedDirection == null || publishedLength <= 0) return false;
    var target = targeting.getGlobalPosition();
    var delta = pos.subtract(target);
    int along =
        delta.getX() * publishedDirection.getStepX()
            + delta.getY() * publishedDirection.getStepY()
            + delta.getZ() * publishedDirection.getStepZ();
    return along >= 1
        && along <= publishedLength
        && target.relative(publishedDirection, along).equals(pos);
  }

  private void sync() {
    if (level != null && !level.isClientSide())
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
  }

  private void targetingChanged() {
    revision = Math.incrementExact(revision);
    setChanged();
    sync();
  }

  public boolean setTarget(TrackTargetingSelection selected) {
    if(pendingAssemblyEntry!=null)return false;
    boolean accepted = targeting.configure(selected);
    if (accepted) {
      suspendArea();
      targeting.resolveTick();
    }
    return accepted;
  }

  private void suspendArea() {
    boolean changed =
        area != null
            || publishedLength != 0
            || publishedDirection != null
            || !publishedBogeys.isEmpty()
            || publishedGraph != null;
    area = null;
    publishedLength = 0;
    publishedDirection = null;
    publishedBogeys = List.of();
    publishedGraph = null;
    publishedGraphRevision = -1;
    StationAssemblyAreas.remove(this);
    if (changed) sync();
  }

  public static void tick(Level level, BlockPos pos, BlockState state, StationBlockEntity station) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, station)) {
            if (!createTick.shouldTick()) return;

    var point = station.targeting.getEdgePoint();
    if (level.isClientSide()) station.tickFlag();
    if (!level.isClientSide()) {
      if (point != null && station.originalPointConfigurationPending && point.ownedBy(station)) {
        station.stationName = point.name();
        station.originalPointConfigurationPending = false;
        station.setChanged();
      }
      if (point != null) station.advanceAssemblyEntry(null);
      if (point != null) point.configure(station, station.stationName, station.isAssembling());
      var present = point == null ? null : point.getPresentTrain();
      UUID presentId = present == null ? null : present.id;
      if (!Objects.equals(presentId, station.autoSchedulePresentTrain)) {
        station.autoSchedulePresentTrain = presentId;
        if (present != null) station.applyAutoSchedule();
      }
      if(present!=null&&present.runtime.displayLinkUpdateRequested){
        com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock.notifyGatherers(level,pos);
        present.runtime.displayLinkUpdateRequested=false;
      }
      var nextPresence = station.actualTrainPresence();
      if (!nextPresence.equals(station.clientTrainPresence)) {
        station.clientTrainPresence = nextPresence;
        // Original notifyUpdate also marked the actual BE changed, notifying comparators.
        station.setChanged();
        station.sync();
      }
      if (station.isAssembling() && station.ticks++ % 20 == 0) station.refreshAssemblyInfo();
      else if (!station.isAssembling()) station.suspendArea();
    }
    StationAssemblyAreas.update(station);
  
        }
    }

  public boolean refreshAssemblyInfo() {
    if (!(level instanceof ServerLevel) || !isAssembling()) {
      suspendArea();
      return false;
    }
    try {
      var found = StationAssemblyArea.discover(this);
      if (StationAssemblyAreas.conflicts(this, found)) {
        suspendArea();
        lastStatus = StationAssemblyPreflight.Status.INVALID_OWNER;
        return false;
      }
      boolean changed =
          publishedLength != found.length()
              || publishedDirection != found.direction()
              || !publishedBogeys.equals(
                  found.bogeys().stream().map(StationAssemblyArea.Bogey::offset).toList())
              || !Objects.equals(publishedGraph, found.graphId())
              || publishedGraphRevision != found.graphRevision();
      area = found;
      publishedDirection = found.direction();
      publishedLength = found.length();
      publishedBogeys = found.bogeys().stream().map(StationAssemblyArea.Bogey::offset).toList();
      publishedGraph = found.graphId();
      publishedGraphRevision = found.graphRevision();
      StationAssemblyAreas.update(this);
      if (changed) sync();
      return true;
    } catch (UnloadedTrackException pending) {
      suspendArea();
      lastStatus = StationAssemblyPreflight.Status.UNLOADED_FRONTIER;
      sync();
      return false;
    } catch (IllegalArgumentException invalid) {
      suspendArea();
      lastStatus = StationAssemblyPreflight.Status.INVALID_TRACK;
      sync();
      return false;
    } catch (IllegalStateException missingOwner) {
      suspendArea();
      lastStatus = StationAssemblyPreflight.Status.INVALID_OWNER;
      sync();
      return false;
    }
  }

  public boolean configure(Player player,String name,boolean assembling,DoorControl doors){
    if(!(level instanceof ServerLevel)||!StationPermissions.canEdit(player,this,true)||getStation()==null
        ||name==null||name.isBlank()||name.length()>256||doors==null)return false;
    if(pendingAssemblyEntry!=null){
      if(!assembling||!pendingAssemblyEntry.requester().equals(player.getUUID())
          ||!pendingAssemblyEntry.name().equals(name)||pendingAssemblyEntry.doors()!=doors)return false;
      return advanceAssemblyEntry(player);
    }
    if(assembling&&assemblyEntryArea()==null)return false;
    if(assembling&&!isAssembling()){
      var train=getStation().getPresentTrain();
      if(train!=null){
        if(train.trafficManager()==null||!train.canDisassemble()||!targeting.isOrthogonal())return false;
        pendingAssemblyEntry=new AssemblyEntryIntent(train.id,targeting.id(),player.getUUID(),targeting.selection(),name,doors);
        trainAssemblyStatus=StationTrainAssembly.Status.DISASSEMBLY_PENDING;trainAssemblyFailure=null;
        revision=Math.incrementExact(revision);setChanged();sync();
        return advanceAssemblyEntry(player);
      }
    }
    return applyConfiguration(name,assembling,doors);
  }

  private StationAssemblyArea assemblyEntryArea(){
    try{
      var found=StationAssemblyArea.discover(this);
      return found.length()<1||StationAssemblyAreas.conflicts(this,found)?null:found;
    }catch(UnloadedTrackException|IllegalArgumentException|IllegalStateException unavailable){return null;}
  }

  private void failAssemblyEntry(){
    pendingAssemblyEntry=null;trainAssemblyStatus=StationTrainAssembly.Status.FAILED;
    trainAssemblyFailure=com.simibubi.create.content.contraptions.AssemblyException.structureChanged(worldPosition);
    revision=Math.incrementExact(revision);setChanged();sync();
  }

  /** An accepted mode request waits on real formation and item custody; normal Train ticks resume it. */
  private boolean advanceAssemblyEntry(Player requester){
    var intent=pendingAssemblyEntry;
    if(intent==null)return false;
    if(!(level instanceof ServerLevel server)||getStation()==null)return true;
    if(!targeting.id().equals(intent.point())||!Objects.equals(targeting.selection(),intent.selection())||!getStation().ownedBy(this)){
      failAssemblyEntry();return false;
    }
    var traffic=com.simibubi.create.content.trains.TrainManager.get(server);
    var train=traffic.get(intent.train());
    if(train==null){
      if(!intent.train().equals(lastDisassembledTrainId)){failAssemblyEntry();return false;}
      if(assemblyEntryArea()==null)return true;
      trainAssemblyStatus=StationTrainAssembly.Status.DISASSEMBLED;trainAssemblyFailure=null;
      if(!applyConfiguration(intent.name(),true,intent.doors()))return true;
      pendingAssemblyEntry=null;setChanged();return true;
    }
    if(train.getCurrentStation()!=getStation()||train.trafficManager()!=traffic){failAssemblyEntry();return false;}
    if(train.isRestoring())return true;
    // A saved intent can predate the first native removal. Only its actual authorized player may begin it.
    if(requester==null)requester=server.getServer().getPlayerList().getPlayer(intent.requester());
    if(requester==null||!intent.requester().equals(requester.getUUID())||!StationPermissions.canEdit(requester,this,true))return true;
    if(!train.canDisassemble())return true;
    var selected=intent.selection();var axis=selected.axis().scale(selected.direction().getStep());
    var direction=Direction.getApproximateNearest(axis.x,axis.y,axis.z);
    boolean completed;
    try{completed=train.disassemble(direction,selected.target().pos().above(),requester);}
    finally{
      if(train.isRestoring()){
        trainAssemblyStatus=StationTrainAssembly.Status.DISASSEMBLY_PENDING;
        revision=Math.incrementExact(revision);setChanged();sync();
      }else failAssemblyEntry();
    }
    if(completed)return advanceAssemblyEntry(null);
    return pendingAssemblyEntry!=null;
  }

  private boolean applyConfiguration(String name,boolean assembling,DoorControl doors){
    if(assembling!=isAssembling()&&!level.setBlock(worldPosition,getBlockState().setValue(StationBlock.ASSEMBLING,assembling),Block.UPDATE_ALL))return false;
    if(LoadedLevelAccess.blockEntity(level,worldPosition)!=this)throw new IllegalStateException("Native station configuration replaced its owning BE");
    stationName=name;doorControl=doors;lastStatus=StationAssemblyPreflight.Status.NOT_CHECKED;
    getStation().configure(this,name,assembling);revision=Math.incrementExact(revision);setChanged();
    if(assembling)refreshAssemblyInfo();else suspendArea();sync();return true;
  }

  public StationAssemblyPreflight.Report preflight(Player player) {
    var report = StationAssemblyPreflight.check(this, player);
    lastStatus = report.status();
    sync();
    return report;
  }

  public boolean trackClicked(
      Player player, InteractionHand hand, ITrackBlock track, BlockState state, BlockPos pos) {
    if (!(level instanceof ServerLevel)
        || !isAssembling()
        || !StationPermissions.canEdit(player, this, false)
        || !player.isWithinBlockInteractionRange(pos, 1)
        || !level.mayInteract(player, pos)
        || !refreshAssemblyInfo()
        || area == null
        || !area.contains(pos)
        || LoadedLevelAccess.blockState(level, pos) != state) return false;
    int offset = area.offset(pos);
    if (!area.isValidBogeyOffset(offset)) {
      for (boolean upsideDown : new boolean[] {false, true})
        for (int shift = -1; shift <= 1; shift++) {
          var candidate =
              pos.relative(area.direction(), shift)
                  .offset(
                      BlockPos.containing(
                          track.getUpNormal(level, pos, state).scale(upsideDown ? -1 : 1)));
          if (!LoadedLevelAccess.isLoaded(level, candidate))
            throw new UnloadedTrackException(level, candidate);
          var actual = LoadedLevelAccess.blockState(level, candidate);
          if (actual.getBlock() instanceof AbstractBogeyBlock<?> bogey
              && bogey.isUpsideDown(actual) == upsideDown
              && LoadedLevelAccess.blockEntity(level, candidate)
                  instanceof AbstractBogeyBlockEntity) {
            boolean cycled = BogeyPlacement.cycleSize(player, candidate);
            if (cycled) refreshAssemblyInfo();
            return cycled;
          }
        }
      return false;
    }
    var target = pos.above();
    if (!LoadedLevelAccess.isLoaded(level, target)) throw new UnloadedTrackException(level, target);
    if (!level.mayInteract(player, target)) return false;
    var previous = LoadedLevelAccess.blockState(level, target);
    if (!previous.isAir()
        && !PlayerBlockBreakEvents.BEFORE
            .invoker()
            .beforeBlockBreak(
                level, player, target, previous, LoadedLevelAccess.blockEntity(level, target)))
      return false;
    boolean placed = BogeyPlacement.placeAtAuthorizedTrack(player, hand, track, state, pos);
    if (placed) refreshAssemblyInfo();
    return placed;
  }

  @Override
  public void transform(
      BlockEntity blockEntity,
      com.simibubi.create.content.contraptions.StructureTransform transform) {
    StationAssemblyAreas.remove(this);
    targeting.transform(blockEntity, transform);
    suspendArea();
  }

  @Override
  public void invalidateEdgePoint(CompoundTag sourceData) {
    targeting.invalidateEdgePoint(sourceData);
    suspendArea();
  }

  @Override
  public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    
    StationAssemblyAreas.remove(this);
    super.preRemoveSideEffects(pos, state);
  }

  @Override
  public void invalidate() {
    StationAssemblyAreas.remove(this);
    super.invalidate();
  }

  public CompoundTag writeStationData(boolean clientSnapshot) {
    var tag = new CompoundTag();
    tag.putString("Name", stationName);
    tag.putInt("DoorControl", doorControl.ordinal());
    tag.putLong("ConfigurationRevision", revision);
    if (originalPointConfigurationPending && !clientSnapshot) tag.putBoolean("OriginalPointConfigurationPending", true);
    if (!clientSnapshot) return tag;
    var presence = clientTrainPresence;
    if (level != null && !level.isClientSide()) presence = actualTrainPresence();
    if (presence.imminentTrain() != null) {
      tag.store("ImminentTrain", UUIDUtil.CODEC, presence.imminentTrain());
      if (presence.present()) tag.putBoolean("TrainPresent", true);
      if (presence.canDisassemble()) tag.putBoolean("TrainCanDisassemble", true);
      if (presence.backwards()) tag.putBoolean("TrainBackwards", true);
      if (presence.hasSchedule()) tag.putBoolean("TrainHasSchedule", true);
      if (presence.hasAutoSchedule()) tag.putBoolean("TrainHasAutoSchedule", true);
    }
    tag.putInt("AssemblyLength", publishedLength);
    if (publishedDirection != null)
      tag.store("AssemblyDirection", Direction.CODEC, publishedDirection);
    tag.store("BogeyLocations", Codec.INT.listOf(), publishedBogeys);
    if (publishedGraph != null) tag.store("AssemblyGraph", UUIDUtil.CODEC, publishedGraph);
    tag.putLong("AssemblyGraphRevision", publishedGraphRevision);
    tag.putString("PreflightStatus", lastStatus.name());
    return tag;
  }

  private void readData(CompoundTag tag, boolean clientPacket) {
    stationName = tag.getStringOr("Name", "Track Station");
    originalPointConfigurationPending = tag.getBooleanOr("OriginalPointConfigurationPending", false);
    if (stationName.length() > 256)
      throw new IllegalArgumentException("Station name exceeds source limit");
    doorControl = DoorControl.byId(tag.getIntOr("DoorControl", 0));
    revision = tag.getLongOr("ConfigurationRevision", 0);
    if (revision < 0) throw new IllegalArgumentException("Negative station revision");
    publishedLength =
        Math.clamp(tag.getIntOr("AssemblyLength", 0), 0, StationAssemblyArea.MAX_LENGTH - 1);
    publishedDirection = tag.read("AssemblyDirection", Direction.CODEC).orElse(null);
    publishedBogeys = tag.read("BogeyLocations", Codec.INT.listOf()).orElse(List.of());
    publishedGraph = tag.read("AssemblyGraph", UUIDUtil.CODEC).orElse(null);
    publishedGraphRevision = tag.getLongOr("AssemblyGraphRevision", -1);
    lastStatus =
        StationAssemblyPreflight.Status.valueOf(
            tag.getStringOr("PreflightStatus", StationAssemblyPreflight.Status.NOT_CHECKED.name()));
    clientTrainPresence = clientPacket
        ? new ClientTrainPresence(tag.read("ImminentTrain", UUIDUtil.CODEC).orElse(null),
            tag.getBooleanOr("TrainPresent", false), tag.getBooleanOr("TrainCanDisassemble", false),
            tag.getBooleanOr("TrainBackwards", false), tag.getBooleanOr("TrainHasSchedule", false),
            tag.getBooleanOr("TrainHasAutoSchedule", false))
        : ClientTrainPresence.EMPTY;
    area = null;
  }

  @Override
  protected void write(ValueOutput out, boolean clientPacket) {
    out.store("StationData", CompoundTag.CODEC, writeStationData(clientPacket));
    if(pendingAssemblyEntry!=null)out.store("AssemblyEntryIntent",AssemblyEntryIntent.CODEC,pendingAssemblyEntry);
    if(lastDisassembledTrainId!=null)out.store("DisassembledTrainId",UUIDUtil.CODEC,lastDisassembledTrainId);
    if(lastDisassembledTrainName!=null)out.store("DisassembledTrainName",net.minecraft.network.chat.ComponentSerialization.CODEC,lastDisassembledTrainName);
    out.putInt("DisassembledTrainColor",lastDisassembledMapColorIndex);
    out.putInt("FailedCarriageIndex",failedCarriageIndex);
    out.putString("TrainAssemblyStatus",trainAssemblyStatus.name());
    if(trainAssemblyFailure!=null){var failure=new CompoundTag();com.simibubi.create.content.contraptions.AssemblyException.write(failure,level==null?Objects.requireNonNull(loadedRegistries,"Actual decoded station registries required"):level.registryAccess(),trainAssemblyFailure);out.store("TrainAssemblyFailure",CompoundTag.CODEC,failure);}
    super.write(out, clientPacket);
  }

  @Override
  protected void read(ValueInput in, boolean clientPacket) {
    loadedRegistries = in.lookup();
    readData(in.read("StationData", CompoundTag.CODEC).orElseGet(CompoundTag::new), clientPacket);
    pendingAssemblyEntry=in.read("AssemblyEntryIntent",AssemblyEntryIntent.CODEC).orElse(null);
    lastDisassembledTrainId=in.read("DisassembledTrainId",UUIDUtil.CODEC).orElse(null);
    lastDisassembledTrainName=in.read("DisassembledTrainName",net.minecraft.network.chat.ComponentSerialization.CODEC).orElse(null);
    lastDisassembledMapColorIndex=in.getIntOr("DisassembledTrainColor",0);
    failedCarriageIndex=in.getIntOr("FailedCarriageIndex",-1);
    if(failedCarriageIndex < -1)throw new IllegalArgumentException("Invalid saved failed carriage index");
    trainAssemblyStatus=StationTrainAssembly.Status.valueOf(in.getStringOr("TrainAssemblyStatus","NOT_CHECKED"));
    trainAssemblyFailure=com.simibubi.create.content.contraptions.AssemblyException.read(in.read("TrainAssemblyFailure",CompoundTag.CODEC).orElseGet(CompoundTag::new),in.lookup());
    
  
        super.read(in, clientPacket);
    }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    var tag = super.getUpdateTag(registries);
    tag.remove("AssemblyEntryIntent");tag.remove("DisassembledTrainId");
    return tag;
  }

  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (targeting != null) behaviours.add(targeting);
        if (depotBehaviour != null) {
            behaviours.add(depotBehaviour);
            behaviours.add(depotBehaviour.directInput);
            behaviours.add(depotBehaviour.transportedHandler);
        }
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        ids.clear();
        ids.add("contraption_actors");
        ids.add("train");
        ids.add("long_train");
        ids.add("conductor");
    }
}
