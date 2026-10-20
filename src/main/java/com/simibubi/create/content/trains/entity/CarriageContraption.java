package com.simibubi.create.content.trains.entity;

import com.simibubi.create.AllContraptionTypes;
import com.simibubi.create.api.behaviour.interaction.ConductorBlockInteractionBehavior;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.minecart.TrainCargoManager;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Actual source carriage discovery/metadata with one native mounted cargo manager. WIP: real
 * Carriage/Train/conductor consumers and native portal rendering are still being ported.
 */
public final class CarriageContraption extends Contraption {
  private Direction assemblyDirection;
  private boolean forwardControls, backwardControls;
  public Couple<Boolean> blockConductors = Couple.create(false, false);
  public final Map<BlockPos, Couple<Boolean>> conductorSeats = new LinkedHashMap<>();
  public final ArrivalSoundQueue soundQueue = new ArrivalSoundQueue();
  private MountedStorageManager storageProxy;
  private int bogeys;
  private boolean sidewaysControls;
  private BlockPos secondBogeyPos;
  private final List<BlockPos> assembledBlockConductors = new ArrayList<>();
  public int portalCutoffMin = Integer.MIN_VALUE, portalCutoffMax = Integer.MAX_VALUE;

  public CarriageContraption() {
    super(new TrainCargoManager());
  }

  public CarriageContraption(Direction direction) {
    this();
    assemblyDirection = requireHorizontal(direction);
  }

  private static Direction requireHorizontal(Direction direction) {
    if (direction == null || !direction.getAxis().isHorizontal())
      throw new IllegalArgumentException("Actual horizontal carriage assembly direction required");
    return direction;
  }

  public void setSoundQueueOffset(int offset) {
    soundQueue.offset = offset;
  }

  @Override
  public boolean assemble(Level world, BlockPos pos) throws AssemblyException {
    requireHorizontal(assemblyDirection);
    bogeys = 0;
    sidewaysControls = false;
    secondBogeyPos = null;
    forwardControls = backwardControls = false;
    assembledBlockConductors.clear();
    conductorSeats.clear();
    blockConductors = Couple.create(false, false);
    soundQueue.deserialize(new CompoundTag());
    if (!searchMovedStructure(world, pos, null) || blocks.size() <= 1 || bogeys == 0) return false;
    if (bogeys > 2)
      throw new AssemblyException(
          Component.translatable("create.train_assembly.too_many_bogeys", bogeys));
    if (sidewaysControls)
      throw new AssemblyException(
          Component.translatable("create.train_assembly.sideways_controls"));
    for (var conductor : assembledBlockConductors)
      for (var direction : Direction.values())
        if (direction.getAxis() == assemblyDirection.getAxis() && inControl(conductor, direction))
          blockConductors.set(direction != assemblyDirection, true);
    for (var seat : getSeats())
      for (var direction : Direction.values())
        if (direction.getAxis() == assemblyDirection.getAxis() && inControl(seat, direction))
          conductorSeats
              .computeIfAbsent(seat, ignored -> Couple.create(false, false))
              .set(direction != assemblyDirection, true);
    return true;
  }

  public boolean inControl(BlockPos local, Direction direction) {
    var controls = blocks.get(local.relative(direction));
    return controls != null
        && controls.state().getBlock() instanceof ControlsBlock
        && controls.state().getValue(ControlsBlock.FACING) == direction.getOpposite();
  }

  @Override
  protected boolean isAnchoringBlockAt(BlockPos pos) {
    return false;
  }

  @Override
  protected Pair<StructureBlockInfo, BlockEntity> capture(Level world, BlockPos pos) {
    var state = com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(world, pos);
    if (ArrivalSoundQueue.isPlayable(state)) {
      var axis = assemblyDirection.getAxis();
      int distance =
          axis.choose(
              pos.getX() - anchor.getX(), pos.getY() - anchor.getY(), pos.getZ() - anchor.getZ());
      soundQueue.add(distance * assemblyDirection.getAxisDirection().getStep(), toLocalPos(pos));
    }
    if (state.getBlock() instanceof AbstractBogeyBlock<?>) {
      bogeys++;
      if (bogeys == 2) secondBogeyPos = pos.immutable();
    }
    var interaction = MovingInteractionBehaviour.REGISTRY.get(state);
    if (interaction instanceof ConductorBlockInteractionBehavior conductor
        && conductor.isValidConductor(state)) assembledBlockConductors.add(toLocalPos(pos));
    if (state.getBlock() instanceof ControlsBlock) {
      var facing = state.getValue(ControlsBlock.FACING);
      if (facing.getAxis() != assemblyDirection.getAxis()) sidewaysControls = true;
      else if (facing == assemblyDirection) forwardControls = true;
      else backwardControls = true;
    }
    return super.capture(world, pos);
  }

  /** Native entity fronts bind the actual Carriage-owned manager, never a serialized copy. */
  public void swapStorageAfterAssembly(CarriageContraptionEntity entity) {
    var carriage = Objects.requireNonNull(entity.getCarriage(), "Actual carriage owner required");
    if (entity.level().isClientSide()) {
      // Client front owns only its actual public SyncedMountedStorage snapshot; private cargo
      // remains server-owned.
      storageProxy = storage;
      return;
    }
    if (carriage.storage == null) {
      if (!(storage instanceof TrainCargoManager cargo)
          || !cargo.ownsContents()
          || phase() != Phase.REMOVED)
        throw new IllegalStateException(
            "Actual removed native cargo custody is required for carriage adoption");
      carriage.storage = cargo;
    } else if (storage != carriage.storage && storage.ownsContents())
      throw new IllegalStateException(
          "Carriage front cannot introduce a second actual cargo owner");
    storageProxy = carriage.storage;
  }

  public void returnStorageForDisassembly(MountedStorageManager actual) {
    if (!(actual instanceof TrainCargoManager) || !actual.ownsContents())
      throw new IllegalArgumentException(
          "Actual owned carriage cargo required for native restoration");
    if (storageProxy != null && storageProxy != actual)
      throw new IllegalStateException("Foreign carriage cargo cannot restore this actual body");
    storageProxy = actual;
  }

  @Override
  public MountedStorageManager getStorage() {
    var actual = storageProxy == null ? storage : storageProxy;
    actual.initialize();
    return actual;
  }

  @Override
  public void writeStorage(
      CompoundTag data, HolderLookup.Provider registries, boolean spawnPacket) {
    // Real Carriage SavedData persists authoritative TrainCargoManager once. Front snapshots only
    // expose source public sync.
    if (spawnPacket) getStorage().write(data, registries, true);
    else if (entity instanceof CarriageContraptionEntity front && !front.isGloballyManaged()) {
      // A refused native forest still has a real accepted body owner, outside Train SavedData.
      // Its native chunk record is the sole durable cargo owner until actual source recovery.
      data.putInt("NativeStandaloneCargo", 1);
      getStorage().write(data, registries, false);
    }
  }

  @Override
  protected void readStorage(Level world, CompoundTag data, boolean spawnPacket) {
    boolean independent = !spawnPacket && data.contains("NativeStandaloneCargo");
    if (independent && data.getInt("NativeStandaloneCargo").orElseThrow() != 1)
      throw new IllegalArgumentException("Unknown native standalone carriage custody format");
    if (!spawnPacket && !independent && (data.contains("items") || data.contains("fluids")))
      throw new IllegalArgumentException(
          "Carriage body contains cargo without its actual global carriage custody record");
    storage.read(data, world.registryAccess(), spawnPacket, this);
    storage.setOwned(
        (independent || spawnPacket && world.isClientSide())
            && phase() == Phase.REMOVED
            && !disassembled);
  }

  @Override
  public CompoundTag writeNBT(HolderLookup.Provider registries, boolean spawnPacket) {
    var data = super.writeNBT(registries, spawnPacket);
    data.store("AssemblyDirection", Direction.CODEC, requireHorizontal(assemblyDirection));
    data.putBoolean("FrontControls", forwardControls);
    data.putBoolean("BackControls", backwardControls);
    data.putBoolean("FrontBlazeConductor", blockConductors.getFirst());
    data.putBoolean("BackBlazeConductor", blockConductors.getSecond());
    var seats = new ListTag();
    conductorSeats.forEach(
        (pos, directions) -> {
          var seat = new CompoundTag();
          seat.store("Pos", BlockPos.CODEC, pos);
          seat.putBoolean("Forward", directions.getFirst());
          seat.putBoolean("Backward", directions.getSecond());
          seats.add(seat);
        });
    data.put("ConductorSeats", seats);
    soundQueue.serialize(data);
    return data;
  }

  @Override
  public void readNBT(Level world, CompoundTag data, boolean spawnPacket) {
    var direction =
        requireHorizontal(data.read("AssemblyDirection", Direction.CODEC).orElseThrow());
    var seats = new LinkedHashMap<BlockPos, Couple<Boolean>>();
    for (var value : data.getListOrEmpty("ConductorSeats")) {
      if (!(value instanceof CompoundTag seat))
        throw new IllegalArgumentException("Malformed actual carriage conductor seat");
      var pos = seat.read("Pos", BlockPos.CODEC).orElseThrow();
      if (seats.putIfAbsent(
              pos,
              Couple.create(
                  seat.getBooleanOr("Forward", false), seat.getBooleanOr("Backward", false)))
          != null) throw new IllegalArgumentException("Duplicate actual carriage conductor seat");
    }
    super.readNBT(world, data, spawnPacket);
    assemblyDirection = direction;
    for (var pos : seats.keySet())
      if (!getSeats().contains(pos))
        throw new IllegalArgumentException("Actual conductor seat missing from carriage body");
    conductorSeats.clear();
    conductorSeats.putAll(seats);
    forwardControls = data.getBooleanOr("FrontControls", false);
    backwardControls = data.getBooleanOr("BackControls", false);
    blockConductors =
        Couple.create(
            data.getBooleanOr("FrontBlazeConductor", false),
            data.getBooleanOr("BackBlazeConductor", false));
    soundQueue.deserialize(data);
    for (var positions : soundQueue.sources().values())
      for (var pos : positions)
        if (!blocks.containsKey(pos))
          throw new IllegalArgumentException("Actual arrival sound source outside carriage body");
  }

  @Override
  public void validateSourceBlocks(Level level, BlockPos offset) throws AssemblyException {
    super.validateSourceBlocks(level, offset);
    CarriagePassengerPreflight.validate(level, this, offset);
  }

  @Override
  public boolean canBeStabilized(Direction facing, BlockPos localPos) {
    return false;
  }

  @Override
  public ContraptionType getType() {
    return AllContraptionTypes.CARRIAGE.value();
  }

  public Direction getAssemblyDirection() {
    return assemblyDirection;
  }

  public boolean hasForwardControls() {
    return forwardControls;
  }

  public boolean hasBackwardControls() {
    return backwardControls;
  }

  public BlockPos getSecondBogeyPos() {
    return secondBogeyPos;
  }

  private int movementCoordinate(BlockPos local) {
    var axis = assemblyDirection.getClockWise().getAxis();
    return axis.choose(local.getZ(), local.getY(), local.getX())
        * -assemblyDirection.getAxisDirection().getStep();
  }

  @Override
  public boolean isHiddenInPortal(BlockPos local) {
    return isHiddenInPortal(movementCoordinate(local));
  }

  public boolean isHiddenInPortal(int coordinate) {
    return !notInPortal() && (!withinVisible(coordinate) || atSeam(coordinate));
  }

  public boolean notInPortal() {
    return portalCutoffMin == Integer.MIN_VALUE && portalCutoffMax == Integer.MAX_VALUE;
  }

  public boolean atSeam(BlockPos local) {
    return atSeam(movementCoordinate(local));
  }

  public boolean withinVisible(BlockPos local) {
    return withinVisible(movementCoordinate(local));
  }

  public boolean atSeam(int coordinate) {
    return coordinate == portalCutoffMin || coordinate == portalCutoffMax;
  }

  public boolean withinVisible(int coordinate) {
    return coordinate > portalCutoffMin && coordinate < portalCutoffMax;
  }
}
