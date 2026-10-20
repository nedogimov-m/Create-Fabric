package com.simibubi.create.content.contraptions;

import com.simibubi.create.*;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.*;
import com.simibubi.create.api.registry.*;
import com.simibubi.create.content.contraptions.actors.seat.*;
import com.simibubi.create.content.contraptions.bearing.*;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.chassis.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock;
import com.simibubi.create.content.contraptions.piston.PistonAttachmentLogic;
import com.simibubi.create.content.contraptions.pulley.*;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Source structure traversal/capture and world transfer. Entity movement/actors are integrated
 * separately.
 */
public abstract class Contraption {
  private final StabilizedSubContraptions stabilized = new StabilizedSubContraptions(this);

  public StabilizedSubContraptions getStabilizedSubContraptions() {
    return stabilized;
  }

  public boolean supportsActorControls() {
    return true;
  }

  private ContraptionWorld collisionLevel;

  public ContraptionWorld getCollisionLevel() {
    if (collisionLevel == null) collisionLevel = new ContraptionWorld(this);
    return collisionLevel;
  }

  public AbstractContraptionEntity entity;
  private java.util.function.Function<BlockPos, BlockEntity> clientBlockEntities;

  public void setClientBlockEntityLookup(
      java.util.function.Function<BlockPos, BlockEntity> lookup) {
    clientBlockEntities = lookup;
  }

  public BlockEntity getBlockEntityClientSide(BlockPos pos) {
    return clientBlockEntities == null ? null : clientBlockEntities.apply(pos);
  }

  /** Mutate only the public BE preview used for future tracking snapshots. */
  public void updateBlockEntityPreview(
      BlockPos pos, java.util.function.Consumer<CompoundTag> update) {
    var tag = updateTags.get(pos);
    if (tag != null) update.accept(tag);
  }

  public enum Phase {
    EMPTY,
    CAPTURED,
    REMOVED,
    DISASSEMBLED
  }

  public AABB bounds;
  public BlockPos anchor = BlockPos.ZERO;
  public boolean stalled, hasUniversalCreativeCrate, disassembled;
  protected final Map<BlockPos, StructureBlockInfo> blocks = new LinkedHashMap<>();
  protected final Map<BlockPos, CompoundTag> updateTags = new HashMap<>();
  protected final List<AABB> superglue = new ArrayList<>();
  protected final List<BlockPos> seats = new ArrayList<>();
  protected final List<MutablePair<StructureBlockInfo, MovementContext>> actors = new ArrayList<>();
  protected final List<ItemStack> disabledActors = new ArrayList<>();
  private boolean actorsStopped;
  protected final Map<UUID, Integer> seatMapping = new HashMap<>();
  protected final Map<BlockPos, List<BlockPos>> capturedMultiblocks = new LinkedHashMap<>();
  protected final MountedStorageManager storage;

  protected Contraption() {
    this(new MountedStorageManager());
  }

  protected Contraption(MountedStorageManager storage) {
    this.storage = Objects.requireNonNull(storage);
  }

  private final Set<SuperGlueEntity> glueToRemove = new HashSet<>();
  private final Set<Long> checkedEntityChunks = new HashSet<>();
  private final Map<BlockPos, BlockState> sourceStates = new HashMap<>();
  private final Map<BlockPos, BlockEntity> sourceEntities = new HashMap<>();
  private final Map<BlockPos, Entity> initialPassengers = new HashMap<>();
  private Phase phase = Phase.EMPTY;
  // Explicit item proposals stay off-world and expose no storage or actor callbacks.
  private boolean packedItemProposal, packedItemActorActivationPending;
  private final Set<BlockPos> packedItemMissingActorStarts = new HashSet<>();
  private boolean worldNotificationsPending,
      restorationNotificationsPending,
      notifyingWorldTransfer,
      transferringWorld;

  public abstract boolean assemble(Level level, BlockPos pos) throws AssemblyException;

  public abstract ContraptionType getType();

  public abstract boolean canBeStabilized(Direction facing, BlockPos localPos);

  public Phase phase() {
    return phase;
  }

  /** Source generic contraptions have no portal cutoff; actual Carriage overrides this. */
  public boolean isHiddenInPortal(BlockPos localPos) {
    return false;
  }

  public Map<BlockPos, StructureBlockInfo> getBlocks() {
    return blocks;
  }

  public MountedStorageManager getStorage() {
    storage.initialize();
    return storage;
  }

  public List<BlockPos> getSeats() {
    return seats;
  }

  public Map<UUID, Integer> getSeatMapping() {
    return seatMapping;
  }

  public List<AABB> getSuperglue() {
    return Collections.unmodifiableList(superglue);
  }

  public Map<BlockPos, Entity> getInitialPassengers() {
    return Collections.unmodifiableMap(initialPassengers);
  }

  public void finishPassengerHandoff() {
    initialPassengers.clear();
  }

  public BlockPos getSeatOf(UUID id) {
    var index = seatMapping.get(id);
    return index == null || index < 0 || index >= seats.size() ? null : seats.get(index);
  }

  protected boolean isAnchoringBlockAt(BlockPos pos) {
    return pos.equals(anchor);
  }

  protected boolean shouldUpdateAfterMovement(StructureBlockInfo info) {
    if (net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(info.state()).isPresent()) return false;
    return !(info.state().getBlock() instanceof com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock);
  }

  public List<BlockPos> restorationNotificationPositions(StructureTransform transform) {
    return blocks.values().stream()
        .filter(this::shouldUpdateAfterMovement)
        .map(info -> transform.apply(info.pos()).immutable())
        .toList();
  }

  protected boolean movementAllowed(BlockState state, Level level, BlockPos pos) {
    return BlockMovementChecks.isMovementAllowed(state, level, pos);
  }

  protected boolean addToInitialFrontier(
      Level level, BlockPos pos, Direction forced, Queue<BlockPos> frontier)
      throws AssemblyException {
    return true;
  }

  protected boolean customBlockRemoval(LevelAccessor level, BlockPos pos, BlockState state) {
    return false;
  }

  protected boolean customBlockPlacement(LevelAccessor level, BlockPos pos, BlockState state) {
    return false;
  }

  /**
   * Local position: structural controllers may move their state while retaining the real source BE.
   */
  protected boolean shouldMoveBlockEntity(
      Level level, BlockPos local, BlockState state, BlockEntity source) {
    return true;
  }

  protected BlockPos toLocalPos(BlockPos pos) {
    return pos.subtract(anchor);
  }

  private static final class UniqueQueue<T> extends AbstractQueue<T> {
    private final LinkedHashSet<T> entries = new LinkedHashSet<>();

    @Override
    public Iterator<T> iterator() {
      return entries.iterator();
    }

    @Override
    public int size() {
      return entries.size();
    }

    @Override
    public boolean add(T value) {
      return entries.add(value);
    }

    @Override
    public boolean offer(T value) {
      return entries.add(value);
    }

    @Override
    public T poll() {
      return entries.isEmpty() ? null : entries.removeFirst();
    }

    @Override
    public T peek() {
      return entries.isEmpty() ? null : entries.getFirst();
    }
  }

  protected static void requireLoaded(Level level, BlockPos pos) throws AssemblyException {
    if (!level.isInValidBounds(pos)) return;
    if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null)
      throw AssemblyException.unloadedChunk(pos);
  }

  protected static BlockState stateAt(Level level, BlockPos pos) throws AssemblyException {
    requireLoaded(level, pos);
    return LoadedLevelAccess.blockState(level, pos);
  }

  public boolean searchMovedStructure(Level level, BlockPos pos, Direction forced)
      throws AssemblyException {
    if (phase == Phase.REMOVED)
      throw new IllegalStateException(
          "Cannot assemble a structure that already owns removed blocks");
    if (worldNotificationsPending || transferringWorld || notifyingWorldTransfer)
      throw new IllegalStateException("Cannot recapture before world transfer finalization");
    stabilized.resetCapture();
    storage.reset();
    checkedEntityChunks.clear();
    collisionLevel = null;
    actors.clear();
    disabledActors.clear();
    actorsStopped = false;
    blocks.clear();
    updateTags.clear();
    sourceStates.clear();
    sourceEntities.clear();
    capturedMultiblocks.clear();
    initialPassengers.clear();
    seats.clear();
    seatMapping.clear();
    glueToRemove.clear();
    superglue.clear();
    disassembled = false;
    phase = Phase.EMPTY;
    anchor = pos.immutable();
    bounds = new AABB(BlockPos.ZERO);
    var frontier = new UniqueQueue<BlockPos>();
    var visited = new HashSet<BlockPos>();
    try {
      if (!BlockMovementChecks.isBrittle(stateAt(level, pos))) frontier.add(pos);
      if (!addToInitialFrontier(level, pos, forced, frontier)) return false;
      for (int limit = 100000; limit > 0; limit--) {
        if (frontier.isEmpty()) {
          phase = Phase.CAPTURED;
          storage.initialize();
          return true;
        }
        if (!moveBlock(level, forced, frontier, visited)) return false;
      }
      throw AssemblyException.structureTooLarge();
    } catch (AssemblyException exception) {
      storage.reset();
      actors.clear();
      disabledActors.clear();
      actorsStopped = false;
      blocks.clear();
      updateTags.clear();
      sourceStates.clear();
      sourceEntities.clear();
      capturedMultiblocks.clear();
      glueToRemove.clear();
      initialPassengers.clear();
      seats.clear();
      throw exception;
    } finally {
      checkedEntityChunks.clear();
    }
  }

  protected boolean moveBlock(
      Level level, Direction forced, Queue<BlockPos> frontier, Set<BlockPos> visited)
      throws AssemblyException {
    var pos = frontier.poll();
    if (pos == null) return false;
    if (!visited.add(pos)) return true;
    if (!level.isInValidBounds(pos) || isAnchoringBlockAt(pos)) return true;
    var state = stateAt(level, pos);
    if (!BlockMovementChecks.isMovementNecessary(state, level, pos)) return true;
    LoadedEntityAccess.requireReady(level, pos);
    LoadedEntityAccess.requireGlueQueriesReady(level, pos, checkedEntityChunks);
    if (!movementAllowed(state, level, pos)) throw AssemblyException.unmovableBlock(pos, state);
    if (!moveSpecialBlocks(level, pos, state, forced, frontier, visited)) return false;
    if (state.hasProperty(ChestBlock.TYPE)
        && state.hasProperty(ChestBlock.FACING)
        && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
      var next = pos.relative(ChestBlock.getConnectedDirection(state));
      if (!visited.contains(next)) frontier.add(next);
    }
    if (state.getBlock() instanceof SeatBlock) {
      var local = toLocalPos(pos);
      seats.add(local);
      var vehicles = level.getEntitiesOfClass(SeatEntity.class, new AABB(pos));
      if (!vehicles.isEmpty() && !vehicles.getFirst().getPassengers().isEmpty())
        initialPassengers.put(local, vehicles.getFirst().getPassengers().getFirst());
    }
    for (var direction : Direction.values()) {
      var next = pos.relative(direction);
      if (isAnchoringBlockAt(next)) continue;
      var neighbor = stateAt(level, next);
      if (!movementAllowed(neighbor, level, next)) {
        if (direction == forced) throw AssemblyException.unmovableBlock(pos, state);
        continue;
      }
      if (BlockMovementChecks.isMovementNecessary(neighbor, level, next))
        LoadedEntityAccess.requireReady(level, next);
      boolean glued = SuperGlueEntity.isGlued(level, pos, direction, glueToRemove);
      boolean attached =
          BlockMovementChecks.isBlockAttachedTowards(
              neighbor, level, next, direction.getOpposite());
      boolean sticky =
          canStick(state, neighbor)
              && !BlockMovementChecks.isBrittle(neighbor)
              && state.getPistonPushReaction() != PushReaction.PUSH
              && neighbor.getPistonPushReaction() != PushReaction.PUSH
              && !BlockMovementChecks.isNotSupportive(state, direction)
              && !BlockMovementChecks.isNotSupportive(neighbor, direction.getOpposite());
      if (!visited.contains(next)
          && (sticky
              || attached
              || glued
              || direction == forced && !BlockMovementChecks.isNotSupportive(state, forced)))
        frontier.add(next);
    }
    addBlock(level, pos, capture(level, pos));
    if (blocks.size() > ContraptionSettings.maxBlocksMoved)
      throw AssemblyException.structureTooLarge();
    return true;
  }

  /** Entry point for each controller/actor's original additional structural rules. */
  protected boolean moveSpecialBlocks(
      Level level,
      BlockPos pos,
      BlockState state,
      Direction forced,
      Queue<BlockPos> frontier,
      Set<BlockPos> visited)
      throws AssemblyException {
    if (state.getBlock()
        instanceof com.simibubi.create.content.trains.bogey.AbstractBogeyBlock<?> bogey)
      for (var face : bogey.getStickySurfaces(level, pos, state)) {
        var next = pos.relative(face);
        if (!visited.contains(next)) frontier.add(next);
      }
    if (state.getBlock() instanceof BeltBlock)
      for (boolean forward : new boolean[] {true, false}) {
        var next = BeltBlock.nextSegmentPosition(state, pos, forward);
        if (next != null && !visited.contains(next)) frontier.add(next);
      }
    if (state.getBlock() instanceof WindmillBearingBlock
        && LoadedLevelAccess.blockEntity(level, pos)
            instanceof WindmillBearingBlockEntity bearing) {
      if (!bearing.disassembleForMovement()) throw AssemblyException.entitiesNotReady(pos);
      var front = pos.relative(state.getValue(BearingBlock.FACING));
      if (!visited.contains(front)) frontier.add(front);
    }
    if (state.is(AllBlocks.MECHANICAL_BEARING)) {
      if (LoadedLevelAccess.blockEntity(level, pos) instanceof MechanicalBearingBlockEntity bearing
          && !bearing.disassembleForMovement()) throw AssemblyException.entitiesNotReady(pos);
      if (stateAt(level, pos) != state) throw AssemblyException.structureChanged(pos);
      var face = state.getValue(BearingBlock.FACING);
      if (canBeStabilized(face, toLocalPos(pos))) stabilized.queue(toLocalPos(pos), face);
      else if (!visited.contains(pos.relative(face))) frontier.add(pos.relative(face));
    }
    if (state.is(AllBlocks.CLOCKWORK_BEARING)
        && LoadedLevelAccess.blockEntity(level, pos) instanceof ClockworkBearingBlockEntity clock
        && !clock.disassembleForMovement()) throw AssemblyException.entitiesNotReady(pos);
    if (LoadedLevelAccess.blockEntity(level, pos)
            instanceof
            com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlockEntity elevator
        && !elevator.disassembleForMovement()) throw AssemblyException.entitiesNotReady(pos);
    if (state.getBlock() instanceof AbstractChassisBlock)
      ChassisAttachmentLogic.enqueue(level, pos, forced, frontier, visited);
    if ((MechanicalPistonBlock.isPiston(state)
            || MechanicalPistonBlock.isExtensionPole(state)
            || MechanicalPistonBlock.isPistonHead(state))
        && !PistonAttachmentLogic.enqueue(level, pos, state, frontier, visited)) return false;
    if (state.getBlock()
            instanceof com.simibubi.create.content.contraptions.gantry.GantryCarriageBlock
        || state.getBlock() instanceof com.simibubi.create.content.kinetics.gantry.GantryShaftBlock)
      com.simibubi.create.content.contraptions.gantry.GantryAttachmentLogic.enqueue(
          level, pos, state, frontier, visited);
    if (state.getBlock() instanceof PulleyBlock) {
      if (LoadedLevelAccess.blockEntity(level, pos) instanceof PulleyBlockEntity pulley
          && !pulley.disassembleForMovement()) throw AssemblyException.entitiesNotReady(pos);
      if (stateAt(level, pos) != state) throw AssemblyException.structureChanged(pos);
      movePulley(level, pos, frontier, visited);
    }
    if (state.getBlock() instanceof StickerBlock && state.getValue(StickerBlock.EXTENDED)) {
      Direction face = state.getValue(StickerBlock.FACING);
      BlockPos attached = pos.relative(face);
      BlockState target = stateAt(level, attached);
      if (!visited.contains(attached)
          && !BlockMovementChecks.isNotSupportive(target, face.getOpposite()))
        frontier.add(attached);
    }
    return true;
  }

  private void movePulley(
      Level level, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited)
      throws AssemblyException {
    for (int distance = 1; distance <= PulleySettings.maxRopeLength + 1; distance++) {
      var ropePos = pos.below(distance);
      if (!level.isInValidBounds(ropePos)) return;
      var state = stateAt(level, ropePos);
      if (!PulleyBlock.isRope(state)) {
        if (!visited.contains(ropePos)) frontier.add(ropePos);
        return;
      }
      LoadedEntityAccess.requireReady(level, ropePos);
      addBlock(level, ropePos, capture(level, ropePos));
      if (blocks.size() > ContraptionSettings.maxBlocksMoved)
        throw AssemblyException.structureTooLarge();
    }
  }

  protected boolean canStick(BlockState first, BlockState second) {
    if (first.is(Blocks.HONEY_BLOCK) && second.is(Blocks.SLIME_BLOCK)
        || first.is(Blocks.SLIME_BLOCK) && second.is(Blocks.HONEY_BLOCK)) return false;
    return first.is(Blocks.SLIME_BLOCK)
        || first.is(Blocks.HONEY_BLOCK)
        || second.is(Blocks.SLIME_BLOCK)
        || second.is(Blocks.HONEY_BLOCK);
  }

  protected Pair<StructureBlockInfo, BlockEntity> capture(Level level, BlockPos pos) {
    var original = LoadedLevelAccess.blockState(level, pos);
    var state = original;
    if (state.getBlock() instanceof ButtonBlock) state = state.setValue(ButtonBlock.POWERED, false);
    if (state.getBlock() instanceof PressurePlateBlock)
      state = state.setValue(PressurePlateBlock.POWERED, false);
    if (state.getBlock()
        instanceof com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock)
      state =
          state.setValue(
              com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock.VISIBLE, false);
    var actual = LoadedLevelAccess.blockEntity(level, pos);
    var be =
        actual != null && shouldMoveBlockEntity(level, toLocalPos(pos), state, actual)
            ? actual
            : null;
    var data = be == null ? null : be.saveWithFullMetadata(level.registryAccess()).copy();
    if (data != null) {
      data.remove("x");
      data.remove("y");
      data.remove("z");
    }
    sourceStates.put(toLocalPos(pos), original);
    if (actual != null) sourceEntities.put(toLocalPos(pos), actual);
    return Pair.of(new StructureBlockInfo(pos, state, data), be);
  }

  protected void addBlock(
      Level level, BlockPos pos, Pair<StructureBlockInfo, BlockEntity> capture) {
    var local = toLocalPos(pos);
    if (blocks.containsKey(local)) return;
    var data = capture.getLeft();
    var info = new StructureBlockInfo(local, data.state(), data.nbt());
    blocks.put(local, info);
    bounds = bounds.minmax(new AABB(local));
    var be = capture.getRight();
    if (be != null) updateTags.put(local, be.getUpdateTag(level.registryAccess()).copy());
    storage.addBlock(level, info.state(), pos, local, be);
    if (MovementBehaviour.REGISTRY.get(info.state()) != null)
      actors.add(MutablePair.of(info, null));
    if (be instanceof IMultiBlockEntityContainer multi) {
      var controller = multi.getController().subtract(anchor);
      capturedMultiblocks.computeIfAbsent(controller, key -> new ArrayList<>()).add(local);
      if (info.nbt() != null) {
        info.nbt().store("Controller", BlockPos.CODEC, controller);
        info.nbt().remove("LastKnownPos");
      }
      var update = updateTags.get(local);
      if (update != null) {
        update.store("Controller", BlockPos.CODEC, controller);
        update.remove("LastKnownPos");
      }
    }
  }

  public void expandBoundsAroundAxis(Direction.Axis axis) {
    double radius = getRadius(blocks.keySet(), axis);
    int r = (int) Math.ceil(radius);
    bounds =
        new AABB(
            axis == Direction.Axis.X ? bounds.minX : -r - 1,
            axis == Direction.Axis.Y ? bounds.minY : -r - 1,
            axis == Direction.Axis.Z ? bounds.minZ : -r - 1,
            axis == Direction.Axis.X ? bounds.maxX : r + 2,
            axis == Direction.Axis.Y ? bounds.maxY : r + 2,
            axis == Direction.Axis.Z ? bounds.maxZ : r + 2);
  }

  public static double getRadius(Iterable<? extends Vec3i> positions, Direction.Axis axis) {
    double max = 0;
    for (var pos : positions) {
      double a = axis == Direction.Axis.X ? pos.getY() : pos.getX(),
          b = axis == Direction.Axis.Z ? pos.getY() : pos.getZ();
      max = Math.max(max, a * a + b * b);
    }
    return Math.sqrt(max);
  }

  /** Pure source preflight; item/BE refresh and every world mutation remain in the transfer. */
  public void validateSourceBlocks(Level level, BlockPos offset) throws AssemblyException {
    if (!ContraptionAssemblyGraph.isTransferPermitted(this))
      throw AssemblyException.entitiesNotReady(anchor.offset(offset));
    if (transferringWorld || notifyingWorldTransfer)
      throw AssemblyException.entitiesNotReady(anchor.offset(offset));
    if (phase != Phase.CAPTURED || level.isClientSide())
      throw new IllegalStateException("Source validation requires a fresh server capture");
    if (net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.getLifecycle()
        != net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.Lifecycle.NONE)
      throw new IllegalStateException(
          "Cannot move world ownership during a Fabric transfer transaction");
    if (sourceStates.size() != blocks.size())
      throw new IllegalStateException(
          "A deserialized capture must be assembled again before taking world ownership");
    var entityChecks = new HashSet<Long>();
    for (var info : blocks.values()) {
      var worldPos = info.pos().offset(anchor).offset(offset);
      requireLoaded(level, worldPos);
      if (DeferredContraptionRestoration.isPending(level, worldPos)
          || ContraptionPassengerHandoff.isPending(level, worldPos))
        throw AssemblyException.entitiesNotReady(worldPos);
      LoadedEntityAccess.requireReady(level, worldPos);
      LoadedEntityAccess.requireGlueQueriesReady(level, worldPos, entityChecks);
      if (LoadedLevelAccess.blockState(level, worldPos) != sourceStates.get(info.pos())
          || LoadedLevelAccess.blockEntity(level, worldPos) != sourceEntities.get(info.pos()))
        throw AssemblyException.unmovableBlock(
            worldPos, LoadedLevelAccess.blockState(level, worldPos));
      if (sourceEntities.get(info.pos())
          instanceof com.simibubi.create.content.trains.track.TrackTargetingBlockItem.Target target)
        target.targeting().validateMovement();
    }
    if (level instanceof ServerLevel server)
      for (var glue : glueToRemove)
        if (glue.isRemoved() || server.getEntity(glue.getUUID()) != glue)
          throw AssemblyException.structureChanged(anchor.offset(offset));
  }

  public boolean tryRemoveBlocksFromWorld(Level level, BlockPos offset) throws AssemblyException {
    return tryRemoveBlocksFromWorld(level, offset, true);
  }

  /** Paired controllers may defer perimeter callbacks until every hand owns its source. */
  public boolean tryRemoveBlocksFromWorld(Level level, BlockPos offset, boolean finishNeighbors)
      throws AssemblyException {
    if (phase != Phase.CAPTURED || level.isClientSide()) return false;
    validateSourceBlocks(level, offset);
    transferringWorld = true;
    try {
      storage.reset();
      for (var info : blocks.values()) {
        var be = sourceEntities.get(info.pos());
        storage
            .addBlock(
                level,
                info.state(),
                info.pos().offset(anchor).offset(offset),
                info.pos(),
                shouldMoveBlockEntity(level, info.pos(), info.state(), be) ? be : null);
      }
      storage.initialize();
      // Contents can change after the graph was examined. Capture their latest values before
      // ownership moves.
      initialPassengers.clear();
      for (var local : seats) {
        var source = local.offset(anchor).offset(offset);
        for (var seat : level.getEntitiesOfClass(SeatEntity.class, new AABB(source)))
          if (seat.getFirstPassenger() != null) {
            initialPassengers.put(local, seat.getFirstPassenger());
            break;
          }
      }
      var railwayTransfers =
          new HashMap<
              BlockPos,
              com.simibubi.create.content.trains.track.TrackTargetingBehaviour.Movement>();
      for (var info : List.copyOf(blocks.values())) {
        var be = sourceEntities.get(info.pos());
        if (be == null || !shouldMoveBlockEntity(level, info.pos(), info.state(), be)) continue;
        var data = be.saveWithFullMetadata(level.registryAccess()).copy();
        data.remove("x");
        data.remove("y");
        data.remove("z");
        if (be instanceof IMultiBlockEntityContainer multi) {
          data.store("Controller", BlockPos.CODEC, multi.getController().subtract(anchor));
          data.remove("LastKnownPos");
        }
        if (be
            instanceof
            com.simibubi.create.content.trains.track.TrackTargetingBlockItem.Target target) {
          String key =
              be instanceof com.simibubi.create.content.trains.station.StationBlockEntity
                  ? "StationData"
                  : "Targeting";
          var targeting =
              data.getCompound(key)
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "Actual captured railway targeting data is missing"));
          var transfer = target.targeting().captureMovement(targeting);
          if (transfer != null) railwayTransfers.put(info.pos(), transfer);
        }
        var refreshed = new StructureBlockInfo(info.pos(), info.state(), data);
        blocks.put(info.pos(), refreshed);
        var actor = getActorAt(info.pos());
        if (actor != null) {
          actor.setLeft(refreshed);
          if (actor.right != null) actor.right.blockEntityData = data;
        }
        var update = be.getUpdateTag(level.registryAccess()).copy();
        if (be instanceof IMultiBlockEntityContainer multi) {
          update.store("Controller", BlockPos.CODEC, multi.getController().subtract(anchor));
          update.remove("LastKnownPos");
        }
        updateTags.put(info.pos(), update);
      }
      if (this instanceof com.simibubi.create.content.trains.entity.CarriageContraption carriage) {
        // Native reservation, storage providers and BE serialization can change the real seat
        // tree after capture. Validate the current world again before the first removal and
        // adopt exactly those validated original roots into the transient handoff.
        var actualPassengers = com.simibubi.create.content.trains.entity.CarriagePassengerPreflight
            .validate(level, carriage, offset);
        initialPassengers.clear();
        initialPassengers.putAll(actualPassengers);
      }
      // Native flags suppress BE removal effects (inventory drops, multiblock splitting, chain
      // refunds).
      int flags =
          Block.UPDATE_CLIENTS
              | Block.UPDATE_IMMEDIATE
              | Block.UPDATE_KNOWN_SHAPE
              | Block.UPDATE_SUPPRESS_DROPS
              | Block.UPDATE_MOVE_BY_PISTON
              | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
      for (boolean brittle : new boolean[] {true, false})
        for (var info : blocks.values()) {
          if (BlockMovementChecks.isBrittle(info.state()) != brittle) continue;
          var worldPos = info.pos().offset(anchor).offset(offset);
          if (customBlockRemoval(level, worldPos, info.state())) continue;
          var old = LoadedLevelAccess.blockState(level, worldPos);
          var replacement =
              old.getBlock() instanceof SimpleWaterloggedBlock
                      && old.hasProperty(BlockStateProperties.WATERLOGGED)
                      && old.getValue(BlockStateProperties.WATERLOGGED)
                  ? Blocks.WATER.defaultBlockState()
                  : Blocks.AIR.defaultBlockState();
          if (!setQuiet(level, worldPos, replacement, flags))
            throw new IllegalStateException("Could not remove captured block at " + worldPos);
          var railwayTransfer = railwayTransfers.remove(info.pos());
          if (railwayTransfer != null) railwayTransfer.committed();
        }
      superglue.clear();
      for (var glue : glueToRemove) {
        BoundingBox cropped = null;
        for (var info : blocks.values())
          if (glue.contains(info.pos().offset(anchor).offset(offset))) {
            var pos = info.pos();
            if (cropped == null) cropped = new BoundingBox(pos);
            else cropped.encapsulate(pos);
          }
        if (cropped != null) {
          var box =
              new AABB(
                  cropped.minX(),
                  cropped.minY(),
                  cropped.minZ(),
                  cropped.maxX() + 1,
                  cropped.maxY() + 1,
                  cropped.maxZ() + 1);
          if (box.getSize() > 1.01) superglue.add(box);
        }
        glue.discard();
      }
      phase = Phase.REMOVED;
      getStorage().setOwned(true);
      sourceStates.clear();
      sourceEntities.clear();
      glueToRemove.clear();
      worldNotificationsPending = true;
      restorationNotificationsPending = false;
      if (finishNeighbors)
        notifyStructureNeighbors(level, new StructureTransform(anchor.offset(offset), 0, 0, 0));
      return true;
    } finally {
      transferringWorld = false;
    }
  }

  public void removeBlocksFromWorld(Level level, BlockPos offset) {
    try {
      if (!tryRemoveBlocksFromWorld(level, offset))
        throw new IllegalStateException("Contraption does not own a fresh world capture");
    } catch (AssemblyException exception) {
      throw new IllegalStateException(exception.component.getString(), exception);
    }
  }

  private static boolean setQuiet(Level level, BlockPos pos, BlockState state, int flags) {
    if (!(level instanceof ServerLevel server) || !level.isInValidBounds(pos)) return false;
    var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    if (chunk == null) return false;
    var previous = chunk.setBlockState(pos, state, flags);
    var current = chunk.getBlockState(pos);
    if (current != state) return false;
    if (previous != null && previous != current) {
      level.setBlocksDirty(pos, previous, current);
      level.sendBlockUpdated(pos, previous, current, flags);
      level.updatePOIOnBlockStateChange(pos, previous, current);
    }
    return true;
  }

  private static void notifyNeighbors(Level level, BlockPos pos, Block block) {
    // Do not ask Level for an absent neighbor chunk while notifying the immediate perimeter.
    for (var direction : Direction.values()) {
      var next = pos.relative(direction);
      if (level.getChunkSource().getChunkNow(next.getX() >> 4, next.getZ() >> 4) != null)
        level.neighborChanged(LoadedLevelAccess.blockState(level, next), next, block, null, false);
    }
  }

  /** Pure availability preflight shared by ordinary and paired restoration. */
  public void validateRestoration(Level level, StructureTransform transform)
      throws AssemblyException {
    if (!ContraptionAssemblyGraph.isTransferPermitted(this))
      throw AssemblyException.entitiesNotReady(transform.apply(BlockPos.ZERO));
    if (transferringWorld || notifyingWorldTransfer)
      throw AssemblyException.entitiesNotReady(transform.apply(BlockPos.ZERO));
    if (disassembled || phase != Phase.REMOVED || level.isClientSide())
      throw new IllegalStateException("Restoration validation requires removed server ownership");
    if (net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.getLifecycle()
        != net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.Lifecycle.NONE)
      throw new IllegalStateException(
          "Cannot restore world ownership during a Fabric transfer transaction");
    for (var info : blocks.values()) {
      var target = transform.apply(info.pos());
      requireLoaded(level, target);
      if (!level.isInValidBounds(target))
        throw AssemblyException.unmovableBlock(target, info.state());
      LoadedEntityAccess.requireReady(level, target);
      for (var side : Direction.values()) requireLoaded(level, target.relative(side));
    }
    for (var box : superglue) {
      var centre = BlockPos.containing(transform.apply(box.getCenter()));
      requireLoaded(level, centre);
      LoadedEntityAccess.requireReady(level, centre);
    }
  }

  public boolean tryAddBlocksToWorld(Level level, StructureTransform transform)
      throws AssemblyException {
    return tryAddBlocksToWorld(level, transform, true);
  }

  public boolean tryAddBlocksToWorld(
      Level level, StructureTransform transform, boolean finishNeighbors) throws AssemblyException {
    if (disassembled || phase != Phase.REMOVED || level.isClientSide()) return false;
    // A live native printer publication cannot transfer its canonical actor/job to the world.
    for (var actor : actors)
      if (actor.right != null
          && MovementBehaviour.REGISTRY.get(actor.left.state())
              instanceof com.simibubi.create.content.kinetics.deployer.DeployerMovementBehaviour
          && !com.simibubi.create.content.kinetics.deployer.DeployerSchematicPrinter
              .prepareDisassembly(actor.right))
        throw AssemblyException.entitiesNotReady(transform.apply(actor.left.pos()));
    validateRestoration(level, transform);
    transferringWorld = true;
    try {
      for (var actor : actors)
        if (actor.right != null
            && MovementBehaviour.REGISTRY.get(actor.left.state())
                instanceof
                com.simibubi.create.content.contraptions.behaviour.dispenser
                    .DropperMovementBehaviour) {
          var target = transform.apply(actor.left.pos());
          if (!com.simibubi.create.content.contraptions.behaviour.dispenser.MountedDispenseExecution
              .prepareDisassembly(actor.right, target))
            throw AssemblyException.entitiesNotReady(target);
        }
      forEachActor(level, (behaviour, context) -> behaviour.writeExtraData(context));
      stop(level);
      getStorage().writeTankContents(blocks, level.registryAccess());
      prepareMultiblocks(level, transform);
      boolean drop = !ContraptionSettings.noDropWhenContraptionReplaceBlocks;
      for (boolean solid : new boolean[] {true, false})
        for (var info : blocks.values()) {
          if (solid == BlockMovementChecks.isBrittle(info.state())) continue;
          var target = transform.apply(info.pos());
          var state = transform.apply(info.state());
          if (customBlockPlacement(level, target, state)) continue;
          if (state.getBlock()
              instanceof com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock)
            state =
                state
                    .setValue(
                        com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock.VISIBLE,
                        !state.getValue(DoorBlock.OPEN))
                    .setValue(DoorBlock.POWERED, false);
          var occupied = LoadedLevelAccess.blockState(level, target);
          if (occupied.getDestroySpeed(level, target) == -1
              || state.getCollisionShape(level, target).isEmpty()
                  && !occupied.getCollisionShape(level, target).isEmpty()) {
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, target, Block.getId(state));
            if (drop) Block.dropResources(state, level, target, null);
            continue;
          }
          if (state.getBlock() instanceof SimpleWaterloggedBlock
              && state.hasProperty(BlockStateProperties.WATERLOGGED))
            state =
                state.setValue(
                    BlockStateProperties.WATERLOGGED,
                    occupied.getFluidState().getType() == Fluids.WATER);
          if (!occupied.isAir() && !occupied.getFluidState().isSource())
            level.destroyBlock(target, drop);
          if (state.is(Blocks.SCULK_SHRIEKER)) state = Blocks.SCULK_SHRIEKER.defaultBlockState();
          setQuiet(
              level,
              target,
              state,
              Block.UPDATE_CLIENTS | Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_KNOWN_SHAPE);
          if (state.getBlock() instanceof DoorBlock
              && transform.rotation != Rotation.NONE
              && (transform.rotationAxis == null || transform.rotationAxis.isHorizontal()))
            level.destroyBlock(target, drop);
          var be = LoadedLevelAccess.blockEntity(level, target);
          var data = info.nbt();
          if (be != null
              && data != null
              && !state.is(Blocks.SCULK_SENSOR)
              && !state.is(Blocks.SCULK_SHRIEKER)) {
            var copy = data.copy();
            copy.putInt("x", target.getX());
            copy.putInt("y", target.getY());
            copy.putInt("z", target.getZ());
            if (be instanceof PulleyBlockEntity
                && transform.rotation != Rotation.NONE
                && transform.rotationAxis != null
                && transform.rotationAxis.isHorizontal()) {
              copy.remove("Offset");
              copy.remove("InitialOffset");
            }
            be.loadWithComponents(
                TagValueInput.create(
                    net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), copy));
            transform.apply(be);
            be.setChanged();
          }
          getStorage().unmount(level, info, target, be);
          if (be != null) level.sendBlockUpdated(target, state, state, Block.UPDATE_CLIENTS);
        }
      worldNotificationsPending = true;
      restorationNotificationsPending = true;
      // Preserve ordinary single-owner ordering; paired hands finalize after both placements.
      if (finishNeighbors) notifyStructureNeighbors(level, transform);
      for (var box : superglue)
        level.addFreshEntity(
            new SuperGlueEntity(
                level,
                new AABB(
                    transform.apply(new Vec3(box.minX, box.minY, box.minZ)),
                    transform.apply(new Vec3(box.maxX, box.maxY, box.maxZ)))));
      disassembled = true;
      phase = Phase.DISASSEMBLED;
      getStorage().setOwned(false);
      return true;
    } finally {
      transferringWorld = false;
    }
  }

  /** Finalize a durable paired restoration after every hand has returned its supporting cells. */
  public static boolean finishPairedRestoration(Level level, Collection<BlockPos> positions) {
    if (level.isClientSide()) return false;
    // Check the whole set before the first shape update. Never load a pending owner's chunks.
    for (var pos : positions) {
      if (!LoadedLevelAccess.isLoaded(level, pos)) return false;
      for (var side : Direction.values())
        if (!LoadedLevelAccess.isLoaded(level, pos.relative(side))) return false;
    }
    boolean drop = !ContraptionSettings.noDropWhenContraptionReplaceBlocks;
    for (var pos : positions) {
      var before = LoadedLevelAccess.blockState(level, pos);
      var shaped = before;
      if (!(before.getBlock()
          instanceof com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock))
        for (var side : Direction.values()) {
          var next = pos.relative(side);
          shaped =
              shaped.updateShape(
                  level,
                  level,
                  pos,
                  side,
                  next,
                  LoadedLevelAccess.blockState(level, next),
                  level.getRandom());
        }
      if (shaped != before)
        Block.updateOrDestroy(
            before,
            shaped,
            level,
            pos,
            Block.UPDATE_ALL
                | Block.UPDATE_MOVE_BY_PISTON
                | (drop ? 0 : Block.UPDATE_SUPPRESS_DROPS));
      notifyNeighbors(level, pos, before.getBlock());
    }
    return true;
  }

  /** Complete a deferred perimeter once. No world chunks are requested or fabricated. */
  public void notifyStructureNeighbors(Level level, StructureTransform transform) {
    if (!worldNotificationsPending || notifyingWorldTransfer || level.isClientSide()) return;
    // A deferred caller may have yielded; never read an absent target or indirect shape chunk.
    if (restorationNotificationsPending)
      for (var info : blocks.values()) {
        var target = transform.apply(info.pos());
        if (!LoadedLevelAccess.isLoaded(level, target)) return;
        for (var side : Direction.values())
          if (!LoadedLevelAccess.isLoaded(level, target.relative(side))) return;
      }
    notifyingWorldTransfer = true;
    try {
      boolean drop = !ContraptionSettings.noDropWhenContraptionReplaceBlocks;
      for (var info : blocks.values()) {
        if (restorationNotificationsPending && !shouldUpdateAfterMovement(info)) continue;
        var target = transform.apply(info.pos());
        if (restorationNotificationsPending) {
          var before = LoadedLevelAccess.blockState(level, target);
          var shaped = before;
          if (!(before.getBlock()
              instanceof com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock))
            for (var side : Direction.values()) {
              var next = target.relative(side);
              shaped =
                  shaped.updateShape(
                      level,
                      level,
                      target,
                      side,
                      next,
                      LoadedLevelAccess.blockState(level, next),
                      level.getRandom());
            }
          if (shaped != before)
            Block.updateOrDestroy(
                before,
                shaped,
                level,
                target,
                Block.UPDATE_ALL
                    | Block.UPDATE_MOVE_BY_PISTON
                    | (drop ? 0 : Block.UPDATE_SUPPRESS_DROPS));
        }
        notifyNeighbors(level, target, info.state().getBlock());
      }
      worldNotificationsPending = false;
      restorationNotificationsPending = false;
    } finally {
      notifyingWorldTransfer = false;
    }
  }

  public void addBlocksToWorld(Level level, StructureTransform transform) {
    try {
      if (!disassembled && !tryAddBlocksToWorld(level, transform))
        throw new IllegalStateException("Contraption has no removed structure to restore");
    } catch (AssemblyException exception) {
      throw new IllegalStateException(exception.component.getString(), exception);
    }
  }

  protected void translateMultiblockControllers(StructureTransform transform) {
    for (var entry : capturedMultiblocks.entrySet()) {
      if (transform.rotationAxis != null
          && transform.rotationAxis != Direction.Axis.Y
          && transform.rotation != Rotation.NONE) {
        for (var local : entry.getValue()) {
          var data = blocks.get(local).nbt();
          if (data != null)
            data.store("LastKnownPos", BlockPos.CODEC, BlockPos.ZERO.below(Integer.MAX_VALUE - 1));
        }
        continue;
      }
      var extent =
          BoundingBox.encapsulatingPositions(
              entry.getValue().stream().map(transform::apply).toList());
      if (extent.isEmpty()) continue;
      var box = extent.get();
      var target = new BlockPos(box.minX(), box.minY(), box.minZ());
      var other = transform.unapply(target);
      var previous = blocks.get(entry.getKey());
      var next = blocks.get(other);
      // Native vaults own twenty slots per component; their item payload must follow that
      // component.
      boolean localInventory =
          previous != null
              && previous.state().getBlock()
                  instanceof com.simibubi.create.content.logistics.vault.ItemVaultBlock;
      if (!localInventory && previous != null && next != null && !other.equals(entry.getKey())) {
        blocks.put(other, new StructureBlockInfo(next.pos(), next.state(), previous.nbt()));
        blocks.put(
            entry.getKey(), new StructureBlockInfo(previous.pos(), previous.state(), next.nbt()));
      }
      for (var local : entry.getValue()) {
        var data = blocks.get(local).nbt();
        if (data != null) {
          data.store("Controller", BlockPos.CODEC, target);
          data.remove("LastKnownPos");
        }
      }
    }
  }

  private void prepareMultiblocks(Level level, StructureTransform transform) {
    boolean vertical =
        transform.rotationAxis != null
            && transform.rotationAxis != Direction.Axis.Y
            && transform.rotation != Rotation.NONE;
    if (!vertical) {
      translateMultiblockControllers(transform);
      return;
    }
    for (var entry : capturedMultiblocks.entrySet()) {
      var root = blocks.get(entry.getKey());
      boolean fluid =
          root != null
              && root.state().getBlock()
                  instanceof com.simibubi.create.content.fluids.tank.FluidTankBlock;
      boolean vault =
          root != null
              && root.state().getBlock()
                  instanceof com.simibubi.create.content.logistics.vault.ItemVaultBlock;
      if (!fluid && !vault) {
        for (var local : entry.getValue()) {
          var data = blocks.get(local).nbt();
          if (data != null)
            data.store("LastKnownPos", BlockPos.CODEC, BlockPos.ZERO.below(Integer.MAX_VALUE - 1));
        }
        continue;
      }
      com.simibubi.create.foundation.fluid.FluidStack remaining =
          com.simibubi.create.foundation.fluid.FluidStack.EMPTY;
      boolean creative =
          fluid
              && ((com.simibubi.create.content.fluids.tank.FluidTankBlock) root.state().getBlock())
                  .creative;
      if (fluid && root.nbt() != null) {
        var tank =
            new com.simibubi.create.foundation.fluid.SmartFluidTank(Long.MAX_VALUE, ignored -> {});
        tank.load(
            TagValueInput.create(
                    net.minecraft.util.ProblemReporter.DISCARDING,
                    level.registryAccess(),
                    root.nbt())
                .childOrEmpty("TankContent"));
        remaining = tank.getFluid();
      }
      for (var local : entry.getValue()) {
        var data = blocks.get(local).nbt();
        if (data == null) continue;
        data.remove("Controller");
        data.remove("LastKnownPos");
        data.putInt("Size", 1);
        data.putInt(fluid ? "Height" : "Length", 1);
        data.putBoolean("Uninitialized", true);
        if (fluid) {
          long capacity = com.simibubi.create.content.fluids.tank.FluidTankBlockEntity.CAPACITY;
          long amount = creative ? capacity : Math.min(capacity, remaining.getAmount());
          var part =
              new com.simibubi.create.foundation.fluid.SmartFluidTank(capacity, ignored -> {});
          if (!remaining.isEmpty())
            part.setFluid(
                new com.simibubi.create.foundation.fluid.FluidStack(remaining.variant(), amount));
          var output =
              TagValueOutput.createWithContext(
                  net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
          part.save(output);
          data.put("TankContent", output.buildResult());
          if (!creative) remaining.shrink(amount);
        }
      }
    }
  }

  private static BlockPos readPos(CompoundTag data, String key) {
    if (data.get(key) instanceof CompoundTag old)
      return new BlockPos(old.getIntOr("X", 0), old.getIntOr("Y", 0), old.getIntOr("Z", 0));
    return data.read(key, BlockPos.CODEC).orElse(BlockPos.ZERO);
  }

  public CompoundTag writeNBT(HolderLookup.Provider registries, boolean spawnData) {
    var result = new CompoundTag();
    var type = CreateBuiltInRegistries.CONTRAPTION_TYPE.getKey(getType());
    if (type == null) throw new IllegalStateException("Unregistered contraption type");
    result.putString("Type", type.toString());
    getStorage().initialize();
    writeActors(result, registries);
    writeStorage(result, registries, spawnData);
    var palette = new LinkedHashMap<BlockState, Integer>();
    var list = new ListTag();
    for (var info : blocks.values()) {
      int index = palette.computeIfAbsent(info.state(), state -> palette.size());
      var entry = new CompoundTag();
      entry.putLong("Pos", info.pos().asLong());
      entry.putInt("State", index);
      var update = updateTags.get(info.pos());
      if (spawnData) {
        if (update != null) entry.put("Data", update.copy());
        else if (info.nbt() != null) {
          entry.put("Data", info.nbt().copy());
          entry.putBoolean("Legacy", true);
        }
      } else {
        if (info.nbt() != null) entry.put("Data", info.nbt().copy());
        if (update != null) entry.put("UpdateTag", update.copy());
      }
      list.add(entry);
    }
    var states = new ListTag();
    for (var state : palette.keySet()) states.add(NbtUtils.writeBlockState(state));
    var packed = new CompoundTag();
    packed.put("Palette", states);
    packed.put("BlockList", list);
    result.put("Blocks", packed);
    var multi = new ListTag();
    for (var entry : capturedMultiblocks.entrySet()) {
      var data = new CompoundTag();
      data.store("Controller", BlockPos.CODEC, entry.getKey());
      var parts = new ListTag();
      for (var pos : entry.getValue()) {
        var part = new CompoundTag();
        part.store("Pos", BlockPos.CODEC, pos);
        parts.add(part);
      }
      data.put("Parts", parts);
      multi.add(data);
    }
    result.put("CapturedMultiblocks", multi);
    var glue = new ListTag();
    if (!spawnData)
      for (var box : superglue) {
        var data = new CompoundTag();
        SuperGlueEntity.writeBoundingBox(data, box);
        glue.add(data);
      }
    result.put("Superglue", glue);
    var seatList = new ListTag();
    for (var pos : seats) {
      var data = new CompoundTag();
      data.store("Pos", BlockPos.CODEC, pos);
      seatList.add(data);
    }
    result.put("Seats", seatList);
    writeSeatMapping(result);
    result.store("Anchor", BlockPos.CODEC, anchor);
    result.putBoolean("Stalled", stalled);
    result.putBoolean("BottomlessSupply", hasUniversalCreativeCrate);
    result.putString("AssemblyPhase", phase.name());
    result.putBoolean("Disassembled", disassembled);
    stabilized.write(result);
    if (bounds != null) {
      var box = new ListTag();
      for (double value :
          new double[] {
            bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ
          }) box.add(FloatTag.valueOf((float) value));
      result.put("BoundsFront", box);
    }
    return result;
  }

  public void readNBT(Level level, CompoundTag data, boolean spawnData) {
    worldNotificationsPending =
        restorationNotificationsPending = notifyingWorldTransfer = transferringWorld = false;
    collisionLevel = null;
    actors.clear();
    disabledActors.clear();
    actorsStopped = false;
    blocks.clear();
    updateTags.clear();
    capturedMultiblocks.clear();
    superglue.clear();
    sourceStates.clear();
    sourceEntities.clear();
    glueToRemove.clear();
    seats.clear();
    seatMapping.clear();
    initialPassengers.clear();
    var packed = data.getCompoundOrEmpty("Blocks");
    if (packed.contains("Palette")) {
      var palette = new ArrayList<BlockState>();
      for (var value : packed.getListOrEmpty("Palette"))
        if (value instanceof CompoundTag state) {
          var id =
              net.minecraft.resources.Identifier.tryParse(
                  state.getStringOr("id", state.getStringOr("Name", "")));
          if (id == null || !net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id))
            throw new IllegalArgumentException("Unknown captured block: " + id);
          palette.add(readState(level, state));
        }
      for (var value : packed.getListOrEmpty("BlockList"))
        if (value instanceof CompoundTag entry) {
          int index = entry.getIntOr("State", -1);
          if (index < 0 || index >= palette.size())
            throw new IllegalArgumentException("Invalid contraption palette index");
          readBlock(BlockPos.of(entry.getLongOr("Pos", 0)), palette.get(index), entry, spawnData);
        }
    } else if (data.get("Blocks") instanceof ListTag legacy) {
      for (var value : legacy)
        if (value instanceof CompoundTag entry) {
          var pos =
              entry.contains("Pos")
                  ? readPos(entry, "Pos")
                  : new BlockPos(
                      entry.getIntOr("X", 0), entry.getIntOr("Y", 0), entry.getIntOr("Z", 0));
          var state = readState(level, entry.getCompoundOrEmpty("Block"));
          readBlock(pos, state, entry, spawnData);
        }
    }
    for (var value : data.getListOrEmpty("CapturedMultiblocks"))
      if (value instanceof CompoundTag entry) {
        var root = readPos(entry, "Controller");
        var parts = new ArrayList<BlockPos>();
        for (var item : entry.getListOrEmpty("Parts"))
          if (item instanceof CompoundTag part) {
            var pos = readPos(part, "Pos");
            if (blocks.containsKey(pos)) parts.add(pos);
          }
        capturedMultiblocks.put(root, parts);
      }
    for (var value : data.getListOrEmpty("Superglue"))
      if (value instanceof CompoundTag entry) superglue.add(SuperGlueEntity.readBoundingBox(entry));
    for (var value : data.getListOrEmpty("Seats"))
      if (value instanceof CompoundTag entry)
        seats.add(
            entry.contains("Pos")
                ? readPos(entry, "Pos")
                : new BlockPos(
                    entry.getIntOr("X", 0), entry.getIntOr("Y", 0), entry.getIntOr("Z", 0)));
    readSeatMapping(data);
    anchor = readPos(data, "Anchor");
    stalled = data.getBooleanOr("Stalled", false);
    hasUniversalCreativeCrate = data.getBooleanOr("BottomlessSupply", false);
    disassembled = data.getBooleanOr("Disassembled", false);
    try {
      phase = Phase.valueOf(data.getStringOr("AssemblyPhase", Phase.REMOVED.name()));
    } catch (IllegalArgumentException ignored) {
      phase = Phase.REMOVED;
    }
    if (disassembled) phase = Phase.DISASSEMBLED;
    var extent = data.getListOrEmpty("BoundsFront");
    if (extent.size() == 6)
      bounds =
          new AABB(
              extent.getDoubleOr(0, 0),
              extent.getDoubleOr(1, 0),
              extent.getDoubleOr(2, 0),
              extent.getDoubleOr(3, 0),
              extent.getDoubleOr(4, 0),
              extent.getDoubleOr(5, 0));
    else {
      bounds = new AABB(BlockPos.ZERO);
      for (var pos : blocks.keySet()) bounds = bounds.minmax(new AABB(pos));
    }
    readStorage(level, data, spawnData);
    stabilized.read(data);
    readActors(level, data);
  }

  public void writeStorage(CompoundTag data, HolderLookup.Provider registries, boolean spawnData) {
    getStorage().write(data, registries, spawnData);
  }

  protected void readStorage(Level level, CompoundTag data, boolean spawnData) {
    getStorage().read(data, level.registryAccess(), spawnData, this);
    getStorage().setOwned(!packedItemProposal && phase == Phase.REMOVED && !disassembled);
  }

  /** Pinned source actor predicate, evaluated from the actual captured native forest. */
  public boolean containsBlockBreakers() {
    for (var pair : actors) {
      var behaviour = com.simibubi.create.api.behaviour.movement.MovementBehaviour.REGISTRY.get(pair.getLeft().state());
      if (behaviour instanceof com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour
          || behaviour instanceof com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour)
        return true;
    }
    return false;
  }

  public List<MutablePair<StructureBlockInfo, MovementContext>> getActors() {
    return actors;
  }

  public MutablePair<StructureBlockInfo, MovementContext> getActorAt(BlockPos pos) {
    for (var actor : actors) if (actor.left.pos().equals(pos)) return actor;
    return null;
  }

  public List<ItemStack> getDisabledActors() {
    return disabledActors;
  }

  public void startMoving(Level level) {
    actorsStopped = false;
    disabledActors.clear();
    for (var actor : actors) {
      var context = new MovementContext(level, actor.left, this);
      actor.setRight(context);
      var behaviour = MovementBehaviour.REGISTRY.get(actor.left.state());
      if (behaviour != null) behaviour.startMoving(context);
    }
    com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsMovement
        .initializeControls(this);
  }

  /** Late binding must not replace an already live actor's context or its actual item owners. */
  public void initializeMissingActors(Level level) {
    actorsStopped = false;
    boolean initialized = false;
    for (var actor : List.copyOf(actors)) {
      if (actor.right != null) continue;
      initialized = true;
      var context = new MovementContext(level, actor.left, this);
      actor.setRight(context);
      var behaviour = MovementBehaviour.REGISTRY.get(actor.left.state());
      if (behaviour != null) behaviour.startMoving(context);
    }
    if (initialized)
      com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsMovement
          .initializeControls(this);
  }

  public void forEachActor(
      Level level, java.util.function.BiConsumer<MovementBehaviour, MovementContext> action) {
    for (var actor : List.copyOf(actors)) {
      var behaviour = MovementBehaviour.REGISTRY.get(actor.left.state());
      if (behaviour != null && actor.right != null) action.accept(behaviour, actor.right);
    }
  }

  public void stop(Level level) {
    if (actorsStopped) return;
    actorsStopped = true;
    forEachActor(
        level,
        (behaviour, context) -> {
          behaviour.stopMoving(context);
          context.position = null;
          context.motion = Vec3.ZERO;
          context.relativeMotion = Vec3.ZERO;
          context.rotation = vector -> vector;
        });
  }

  public boolean isActorTypeDisabled(ItemStack filter) {
    return disabledActors.stream()
        .anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, filter));
  }

  public void setActorsActive(ItemStack reference, boolean enable) {
    com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsMovement
        .setActorTypeActive(this, reference, enable);
    if (entity != null && !entity.level().isClientSide()) ContraptionNetworking.syncActors(entity);
  }

  public void writeActors(CompoundTag tag, HolderLookup.Provider registries) {
    var entries = new ListTag();
    for (var actor : actors) {
      var behaviour = MovementBehaviour.REGISTRY.get(actor.left.state());
      if (behaviour == null || actor.right == null) continue;
      behaviour.writeExtraData(actor.right);
      var entry = new CompoundTag();
      entry.store("Pos", BlockPos.CODEC, actor.left.pos());
      actor.right.writeToNBT(entry);
      entries.add(entry);
    }
    tag.put("Actors", entries);
    var output =
        TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, registries);
    output.store("DisabledActors", ItemStack.OPTIONAL_CODEC.listOf(), disabledActors);
    tag.put("DisabledActors", output.buildResult().get("DisabledActors").copy());
  }

  public void readActors(Level level, CompoundTag tag) {
    var next = new ArrayList<MutablePair<StructureBlockInfo, MovementContext>>();
    var visited = new HashSet<BlockPos>();
    for (var value : tag.getListOrEmpty("Actors")) {
      if (!(value instanceof CompoundTag entry))
        throw new IllegalArgumentException("Invalid actor entry");
      var pos = readPos(entry, "Pos");
      var info = blocks.get(pos);
      if (info == null || !visited.add(pos))
        throw new IllegalArgumentException("Invalid/duplicate actor position");
      var behaviour = MovementBehaviour.REGISTRY.get(info.state());
      if (behaviour == null) continue;
      var context = MovementContext.readNBT(level, info, entry, this);
      if (!level.isClientSide()
          && behaviour
              instanceof com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour)
        context.data.putInt(
            "BreakerId",
            com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity
                .nextBreakerId());
      next.add(MutablePair.of(info, context));
    }
    for (var info : blocks.values())
      if (!visited.contains(info.pos())) {
        var behaviour = MovementBehaviour.REGISTRY.get(info.state());
        if (behaviour == null) continue;
        var context = new MovementContext(level, info, this);
        if (packedItemProposal) packedItemMissingActorStarts.add(info.pos());
        else behaviour.startMoving(context);
        next.add(MutablePair.of(info, context));
      }
    var disabled =
        TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag)
            .read("DisabledActors", ItemStack.OPTIONAL_CODEC.listOf())
            .orElse(List.of());
    actors.clear();
    actors.addAll(next);
    disabledActors.clear();
    disabledActors.addAll(disabled);
    actorsStopped = false;
    if (packedItemProposal) {
      packedItemActorActivationPending = true;
      return;
    }
    for (var actor : actors) {
      var behaviour = MovementBehaviour.REGISTRY.get(actor.left.state());
      var stack = behaviour.canBeDisabledVia(actor.right);
      actor.right.disabled = stack != null && isActorTypeDisabled(stack);
      behaviour.onDataUpdated(actor.right);
    }
    com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsMovement
        .applyDisabledActors(this);
  }

  public void readActorData(CompoundTag data) {
    var updates = new LinkedHashMap<MovementContext, CompoundTag>();
    for (var value : data.getListOrEmpty("ActorData")) {
      if (!(value instanceof CompoundTag entry)
          || !entry.contains("Pos")
          || !(entry.get("Data") instanceof CompoundTag))
        throw new IllegalArgumentException("Invalid actor data update");
      var pos = readPos(entry, "Pos");
      var actor = getActorAt(pos);
      if (actor == null || actor.right == null || updates.put(actor.right, entry) != null)
        throw new IllegalArgumentException("Invalid/duplicate actor data position");
    }
    updates.forEach(
        (context, entry) -> {
          context.data = entry.getCompoundOrEmpty("Data").copy();
          context.stall = entry.getBooleanOr("Stall", context.stall);
          MovementBehaviour.REGISTRY.get(context.state).onDataUpdated(context);
        });
  }

  private static BlockState readState(Level level, CompoundTag state) {
    if (!state.contains("id") && state.contains("Name")) {
      state = state.copy();
      state.putString("id", state.getStringOr("Name", "minecraft:air"));
      if (state.contains("Properties"))
        state.put("properties", state.getCompoundOrEmpty("Properties"));
    }
    return NbtUtils.readBlockState(
        level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
        state);
  }

  private void readBlock(BlockPos pos, BlockState state, CompoundTag entry, boolean spawnData) {
    var payload = entry.contains("Data") ? entry.getCompoundOrEmpty("Data").copy() : null;
    if (blocks.put(pos, new StructureBlockInfo(pos, state, payload)) != null)
      throw new IllegalArgumentException("Duplicate captured block position");
    if (spawnData) {
      if (payload != null) updateTags.put(pos, payload.copy());
    } else if (entry.contains("UpdateTag"))
      updateTags.put(pos, entry.getCompoundOrEmpty("UpdateTag").copy());
  }

  /** Decode-only item proposal. The caller must retain the actual canonical paid item until commit. */
  public static Contraption fromPackedItemNBT(Level level, CompoundTag data) {
    var result = ContraptionType.fromType(data.getStringOr("Type", ""));
    if (result == null) throw new IllegalArgumentException("Unknown packed contraption type");
    if (!(result instanceof com.simibubi.create.content.contraptions.mounted.MountedContraption)
        && !(result instanceof com.simibubi.create.content.contraptions.bearing.StabilizedContraption))
      throw new IllegalArgumentException("Packed cart payload requires actual mounted or stabilized source types");
    result.packedItemProposal = true;
    result.readNBT(level, data, false);
    if (result.phase != Phase.REMOVED || result.disassembled || result.entity != null
        || result.getStorage().ownsContents()) throw new IllegalArgumentException("Invalid off-world packed structure");
    return result;
  }

  /** A refused pickup resumes these same actual owners; stopped deployer avatars are reconstructed from their flushed canonical data. */
  final void resumeAfterPackedItemRefusal() {
    if (!actorsStopped || entity == null || !entity.hasOwnedStructure()) return;
    actorsStopped = false;
    for (var actor : actors) {
      if (actor.right == null) continue;
      if (actor.right.temporaryData instanceof com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer avatar
          && avatar.isRemoved()) actor.right.temporaryData = null;
      var behaviour = MovementBehaviour.REGISTRY.get(actor.left.state());
      if (behaviour != null) behaviour.startMoving(actor.right);
    }
    com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsMovement.applyDisabledActors(this);
  }

  public final boolean isPackedItemProposal() { return packedItemProposal; }

  /** Package-scoped callback-free claim; the owning entity validates the concrete receipt commit. */
  final void claimPackedItemContentsPure() {
    if (!packedItemProposal || entity == null || entity.getContraption() != this
        || phase != Phase.REMOVED || disassembled || getStorage().ownsContents())
      throw new IllegalStateException("Actual decoded item ownership claim required");
    getStorage().setOwned(true);
    packedItemProposal = false;
  }

  /** Called after every actual forest/native owner is bound, and never in the ownership commit. */
  public final void activatePackedItemActors() {
    if (!packedItemActorActivationPending) return;
    if (packedItemProposal || entity == null || !entity.hasOwnedStructure())
      throw new IllegalStateException("Cannot activate actors before actual packed ownership commit");
    var starts = Set.copyOf(packedItemMissingActorStarts);
    packedItemMissingActorStarts.clear();
    packedItemActorActivationPending = false;
    for (var actor : actors) {
      var behaviour = MovementBehaviour.REGISTRY.get(actor.left.state());
      if (behaviour == null || actor.right == null) continue;
      if (starts.contains(actor.left.pos())) behaviour.startMoving(actor.right);
      var stack = behaviour.canBeDisabledVia(actor.right);
      actor.right.disabled = stack != null && isActorTypeDisabled(stack);
      behaviour.onDataUpdated(actor.right);
    }
    com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsMovement
        .applyDisabledActors(this);
  }

  public static Contraption fromNBT(Level level, CompoundTag data, boolean spawnData) {
    var result = ContraptionType.fromType(data.getStringOr("Type", ""));
    if (result == null)
      throw new IllegalArgumentException(
          "Unknown contraption type: " + data.getStringOr("Type", ""));
    result.readNBT(level, data, spawnData);
    return result;
  }

  public void writeSeatMapping(CompoundTag data) {
    var entries = new ListTag();
    seatMapping.forEach(
        (id, index) -> {
          var entry = new CompoundTag();
          entry.store("Id", net.minecraft.core.UUIDUtil.CODEC, id);
          entry.putInt("Seat", index);
          entries.add(entry);
        });
    data.put("Passengers", entries);
  }

  public void readSeatMapping(CompoundTag data) {
    var decoded = new HashMap<UUID, Integer>();
    var occupied = new HashSet<Integer>();
    for (var value : data.getListOrEmpty("Passengers")) {
      if (!(value instanceof CompoundTag entry))
        throw new IllegalArgumentException("Invalid seat mapping");
      var id =
          entry
              .read("Id", net.minecraft.core.UUIDUtil.CODEC)
              .orElseThrow(() -> new IllegalArgumentException("Invalid seat UUID"));
      int index = entry.getIntOr("Seat", -1);
      if (index < 0
          || index >= seats.size()
          || decoded.put(id, index) != null
          || !occupied.add(index))
        throw new IllegalArgumentException("Invalid/duplicate passenger seat");
    }
    seatMapping.clear();
    seatMapping.putAll(decoded);
  }

  public void addPassengersToWorld(
      Level world, StructureTransform transform, List<Entity> passengers) {
    if (world instanceof ServerLevel server)
      ContraptionPassengerHandoff.finish(server, this, transform, passengers);
  }
}
