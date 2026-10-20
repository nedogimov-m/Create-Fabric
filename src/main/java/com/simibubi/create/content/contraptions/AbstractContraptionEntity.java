package com.simibubi.create.content.contraptions;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.storage.item.chest.ChestMountedStorage;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/**
 * Real persistent moving structure owner. Terrain/passenger surface collision is integrated
 * separately.
 */
public abstract class AbstractContraptionEntity extends Entity {
  public final Map<Entity, org.apache.commons.lang3.mutable.MutableInt> collidingEntities =
      new IdentityHashMap<>();
  private Vec3 previousCollisionAnchor;
  private ContraptionRotationState previousCollisionRotation;
  private boolean passengersInitialized, skipActorStop;
  private final Map<UUID, Vec3> dismountLocations = new HashMap<>();
  protected Contraption contraption;
  private Object clientRenderState;
  private static final EntityDataAccessor<Boolean> STALLED =
      SynchedEntityData.defineId(AbstractContraptionEntity.class, EntityDataSerializers.BOOLEAN);

  public AbstractContraptionEntity(EntityType<?> type, Level level) {
    super(type, level);
    noPhysics = true;
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder data) {
    data.define(STALLED, false);
  }

  public Contraption getContraption() {
    return contraption;
  }

  public boolean hasOwnedStructure() {
    return contraption != null
        && contraption.phase() == Contraption.Phase.REMOVED
        && !contraption.disassembled
        && contraption.getStorage().ownsContents();
  }

  public void setContraption(Contraption contraption) {
    bindRemovedContraptionPure(contraption);
    if (contraption != null) {
      if (contraption.getActors().stream().anyMatch(actor -> actor.right == null))
        contraption.initializeMissingActors(level());
      updateStructureBounds();
    }
  }

  /** All accepted graph owners receive their actual removed data before observable activation. */
  final void bindRemovedContraptionPure(Contraption contraption) {
    if (contraption != null && contraption.phase() != Contraption.Phase.REMOVED)
      throw new IllegalArgumentException("Moving entity must own removed blocks");
    this.contraption = contraption;
    clientRenderState = null;
    if (contraption != null) contraption.entity = this;
  }

  public Object getClientRenderState() {
    return clientRenderState;
  }

  public void setClientRenderState(Object state) {
    clientRenderState = state;
  }

  public boolean isStalled() {
    return entityData.get(STALLED);
  }

  public void setStalled(boolean stalled) {
    entityData.set(STALLED, stalled);
    if (contraption != null) contraption.stalled = stalled;
  }

  @Override
  public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
    super.onSyncedDataUpdated(accessor);
    if (STALLED.equals(accessor) && contraption != null) contraption.stalled = isStalled();
  }

  public abstract Vec3 applyRotation(Vec3 local, float partial);

  public abstract org.joml.Quaternionf getOrientationQuaternion(float partial);

  public abstract Vec3 reverseRotation(Vec3 local, float partial);

  protected abstract void tickContraption();

  protected abstract StructureTransform disassemblyTransform();

  public final StructureTransform getRestorationTransform() {
    return disassemblyTransform();
  }

  public abstract ContraptionRotationState getRotationState();

  public Vec3 getAnchorVec() {
    return position();
  }

  public Vec3 getPrevAnchorVec() {
    return previousCollisionAnchor == null ? position() : previousCollisionAnchor;
  }

  public Vec3 toGlobalVector(Vec3 local, float partial) {
    return applyRotation(local.subtract(.5, .5, .5), partial).add(.5, .5, .5).add(getAnchorVec());
  }

  public Vec3 toLocalVector(Vec3 global, float partial) {
    return reverseRotation(global.subtract(getAnchorVec()).subtract(.5, .5, .5), partial)
        .add(.5, .5, .5);
  }

  public Vec3 getContactPointMotion(Vec3 point) {
    if (previousCollisionAnchor == null || previousCollisionRotation == null) return Vec3.ZERO;
    var local =
        previousCollisionRotation
            .asMatrix()
            .transform(point.subtract(getPrevAnchorVec()).subtract(.5, .5, .5));
    return getRotationState()
        .asMatrix()
        .transformTransposed(local)
        .add(.5, .5, .5)
        .add(getAnchorVec())
        .subtract(point);
  }

  public void beginCollisionTick() {
    collidingEntities.entrySet().removeIf(entry -> entry.getValue().incrementAndGet() > 3);
  }

  public void finishCollisionTick() {
    previousCollisionAnchor = position();
    previousCollisionRotation = getRotationState();
  }

  public void registerColliding(Entity entity) {
    collidingEntities
        .computeIfAbsent(entity, key -> new org.apache.commons.lang3.mutable.MutableInt())
        .setValue(0);
  }

  public boolean canCollideWith(Entity entity) {
    return hasOwnedStructure()
        && entity != this
        && !entity.noPhysics
        && !entity.isSpectator()
        && !(entity instanceof net.minecraft.world.entity.decoration.HangingEntity)
        && !(entity instanceof net.minecraft.world.entity.projectile.Projectile)
        && !(entity instanceof SeatEntity)
        && !entity.isPassenger()
        && !hasIndirectPassenger(entity)
        && !isPassengerOfSameVehicle(entity)
        && entity.getPistonPushReaction()
            == net.minecraft.world.level.material.PushReaction.PUSH_PULL;
  }

  private void updateStructureBounds() {
    if (contraption != null && contraption.bounds != null)
      setBoundingBox(contraption.bounds.move(position()));
  }

  @Override
  protected AABB makeBoundingBox(Vec3 position) {
    return contraption != null && contraption.bounds != null
        ? contraption.bounds.move(position)
        : super.makeBoundingBox(position);
  }

  protected final void refreshPassengerPositionsForPose() {
    if (!hasOwnedStructure()) return;
    for (var passenger : getPassengers())
      if (level().isClientSide()
          || passenger instanceof OrientedContraptionEntity child
              && contraption.getStabilizedSubContraptions().accepts(child))
        positionRider(passenger);
  }

  @Override
  public void setPos(double x, double y, double z) {
    super.setPos(x, y, z);
    updateStructureBounds();
    refreshPassengerPositionsForPose();
  }

  @Override
  public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    return false;
  }

  @Override
  public void kill(ServerLevel level) {
    disassemble();
  }

  @Override
  public boolean isPickable() {
    return false;
  }

  /** Adopt original captured riders only on their real accepted native owner. */
  private boolean passengersInitializing;
  public final boolean isInitializingCapturedPassengers(){return passengersInitializing;}

  public final boolean initializeCapturedPassengers() {
    if (level().isClientSide()) return true;
    if (passengersInitialized) return true;
    if (contraption != null && contraption.getInitialPassengers().isEmpty()) {
      passengersInitialized = true;
      return true;
    }
    if (!(level() instanceof ServerLevel server)
        || server.getEntity(getUUID()) != this
        || !hasOwnedStructure()) return false;
    if(passengersInitializing)return contraption.getInitialPassengers().entrySet().stream().allMatch(entry->entry.getValue().getVehicle()==this&&java.util.Objects.equals(contraption.getSeatMapping().get(entry.getValue().getUUID()),contraption.getSeats().indexOf(entry.getKey())));
    passengersInitializing=true;
    try{
    boolean complete = true;
    for (var entry : List.copyOf(contraption.getInitialPassengers().entrySet())) {
      var passenger = entry.getValue();
      if (!passenger.isAlive() || passenger.level() != level()) continue;
      if (passenger.getVehicle() != this
          && passenger.isPassenger()
          && !(passenger.getVehicle() instanceof SeatEntity)) {
        complete = false;
        continue;
      }
      complete &= addSittingPassenger(passenger, contraption.getSeats().indexOf(entry.getKey()));
    }
    if (complete) {
      contraption.finishPassengerHandoff();
      passengersInitialized = true;
    }
    return complete;
    }finally{passengersInitializing=false;}
  }

  @Override
  public void tick() {
    if (contraption == null) {
      if (!level().isClientSide()) discard();
      return;
    }
    if (!hasOwnedStructure()) return;
    if (level() instanceof ServerLevel server)
      contraption.getStabilizedSubContraptions().reattachLoaded(server);
    ContraptionHandler.addSpawnedContraptionsToCollisionList(this);
    initializeCapturedPassengers();
    xo = getX();
    yo = getY();
    zo = getZ();
    super.tick();
    tickContraption();
    if (!level().isClientSide() && tickCount % 8 == 0) ContraptionNetworking.syncStorage(this);
    for (var passenger : getPassengers()) positionRider(passenger);
  }

  public void tickActors() {
    if (!hasOwnedStructure() || contraption.getStabilizedSubContraptions().isRecovering()) return;
    boolean previous = contraption.stalled;
    contraption.stalled = level().isClientSide() && isStalled();
    skipActorStop = true;
    for (var pair : List.copyOf(contraption.getActors())) {
      var context = pair.right;
      var actor = MovementBehaviour.REGISTRY.get(pair.left.state());
      if (actor == null || context == null) continue;
      var oldMotion = context.motion;
      var actorPosition =
          toGlobalVector(
              Vec3.atCenterOf(pair.left.pos()).add(actor.getActiveAreaOffset(context)), 1);
      var gridPosition = BlockPos.containing(actorPosition);
      boolean visit = false;
      if (!context.stall && context.position != null) {
        context.motion = actorPosition.subtract(context.position);
        context.relativeMotion = reverseRotation(context.motion, 1);
        visit =
            !BlockPos.containing(context.position).equals(gridPosition)
                || context.relativeMotion.lengthSqr() > 0 && context.firstMovement;
      }
      context.rotation = vector -> applyRotation(vector, 1);
      context.position = actorPosition;
      if (!isActorActive(context, actor) && !actor.mustTickWhileDisabled()) continue;
      if (visit && !context.stall) {
        actor.visitNewPosition(context, gridPosition);
        context.firstMovement = false;
        if (!isAlive()) break;
      }
      if (!oldMotion.equals(context.motion)) {
        actor.onSpeedChanged(context, oldMotion, context.motion);
        if (!isAlive()) break;
      }
      actor.tick(context);
      if (!isAlive()) break;
      contraption.stalled |= context.stall;
    }
    skipActorStop = false;
    if (!isAlive()) {
      contraption.stop(level());
      return;
    }
    if (level() instanceof ServerLevel server)
      contraption.stalled |= contraption.getStabilizedSubContraptions().hasStalledChild(server);
    if (level().isClientSide()) contraption.stalled = isStalled();
    else {
      entityData.set(STALLED, contraption.stalled);
      if (previous != contraption.stalled) {
        onContraptionStalled();
        ContraptionNetworking.syncActors(this);
      }
    }
  }

  protected boolean isActorActive(
      com.simibubi.create.content.contraptions.behaviour.MovementContext context,
      com.simibubi.create.api.behaviour.movement.MovementBehaviour actor) {
    return actor.isActive(context);
  }

  public void refreshPSIs() {
    if (contraption == null) return;
    for (var pair : List.copyOf(contraption.getActors())) {
      var context = pair.right;
      var actor = MovementBehaviour.REGISTRY.get(pair.left.state());
      if (actor
              instanceof
              com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceMovement
          && context != null
          && context.position != null
          && actor.isActive(context))
        actor.visitNewPosition(context, BlockPos.containing(context.position));
    }
  }

  protected void onContraptionStalled() {}

  @Override
  public void remove(RemovalReason reason) {
    if (!level().isClientSide()
        && !isRemoved()
        && reason.shouldDestroy()
        && hasOwnedStructure()
        && (!contraption.getStabilizedSubContraptions().children().isEmpty()
            || contraption.getStabilizedSubContraptions().isRecovering()
            || contraption.getStabilizedSubContraptions().hasPendingRestoration()
            || this instanceof OrientedContraptionEntity)) {
      ContraptionAssemblyGraph.restore(this);
      return;
    }
    if (!level().isClientSide() && !isRemoved() && contraption != null && !skipActorStop)
      contraption.stop(level());
    super.remove(reason);
  }

  public void clearDismountLocation(Entity passenger) {
    dismountLocations.remove(passenger.getUUID());
    ContraptionPassengerData.clearImportedDismountLocation(passenger);
  }

  public boolean disassemble() {
    if (level().isClientSide() || contraption == null || !isAlive()) return false;
    if (!contraption.getStabilizedSubContraptions().children().isEmpty()
        || contraption.getStabilizedSubContraptions().isRecovering()
        || contraption.getStabilizedSubContraptions().hasPendingRestoration())
      return ContraptionAssemblyGraph.restore(this);
    try {
      var transform = disassemblyTransform();
      if (!contraption.tryAddBlocksToWorld(level(), transform)) return false;
      contraption.addPassengersToWorld(level(), transform, getPassengers());
      ejectPassengers();
      discard();
      return true;
    } catch (AssemblyException unloaded) {
      return false;
    }
  }

  public BlockHitResult rayTrace(Vec3 start, Vec3 end, float partial) {
    if (!hasOwnedStructure()) return null;
    return rayTraceLocal(toLocalVector(start, partial), toLocalVector(end, partial));
  }

  protected BlockHitResult rayTraceLocal(Vec3 start, Vec3 end) {
    var view = new ContraptionWorld(contraption);
    var hit =
        view.clip(
            new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this));
    BlockHitResult nearest = hit.getType() == HitResult.Type.MISS ? null : hit;
    double distance =
        nearest == null ? Double.MAX_VALUE : start.distanceToSqr(nearest.getLocation());
    // A sliding leaf can occupy a neighboring cell that vanilla voxel traversal never visits.
    // Restrict the extra work to cells around the short interaction ray, not the entire carrier.
    var from =
        BlockPos.containing(
            Math.min(start.x, end.x) - 1,
            Math.min(start.y, end.y) - 1,
            Math.min(start.z, end.z) - 1);
    var to =
        BlockPos.containing(
            Math.max(start.x, end.x) + 1,
            Math.max(start.y, end.y) + 1,
            Math.max(start.z, end.z) + 1);
    for (var pos : BlockPos.betweenClosed(from, to)) {
      var info = contraption.getBlocks().get(pos);
      if (info == null
          || contraption.isHiddenInPortal(pos)
          || !(info.state().getBlock()
              instanceof com.simibubi.create.foundation.block.IHaveBigOutline)) continue;
      var extended = info.state().getShape(view, pos).clip(start, end, info.pos());
      if (extended == null) continue;
      double candidate = start.distanceToSqr(extended.getLocation());
      if (candidate < distance) {
        nearest = extended;
        distance = candidate;
      }
    }
    return nearest;
  }

  protected record InteractionPick(BlockHitResult hit, Vec3 global) {}

  protected List<InteractionPick> interactionPicks(Vec3 start, Vec3 end) {
    var hit = rayTrace(start, end, 1);
    return hit == null
        ? List.of()
        : List.of(new InteractionPick(hit, toGlobalVector(hit.getLocation(), 1)));
  }

  public boolean visibleFrom(Vec3 start, Vec3 end, BlockHitResult localHit, float partial) {
    var worldHit =
        ContraptionWorld.loadedWorld(level())
            .clip(
                new ClipContext(
                    start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this));
    return worldHit.getType() == HitResult.Type.MISS
        || worldHit.getLocation().distanceToSqr(start) + .001
            >= toGlobalVector(localHit.getLocation(), partial).distanceToSqr(start);
  }

  public boolean handleInteractionPacket(ServerPlayer player, ContraptionInteractionPacket packet) {
    if (player.isSpectator()
        || !isAlive()
        || !hasOwnedStructure()
        || !packet.entityUuid().equals(getUUID())) return false;
    var start = player.getEyePosition();
    var end = start.add(player.getViewVector(1).scale(player.blockInteractionRange() + 1));
    var worldHit =
        ContraptionWorld.loadedWorld(level())
            .clip(
                new ClipContext(
                    start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    double clearDistance =
        worldHit.getType() == HitResult.Type.MISS
            ? Double.MAX_VALUE
            : start.distanceToSqr(worldHit.getLocation());
    for (var other :
        level()
            .getEntitiesOfClass(AbstractContraptionEntity.class, new AABB(start, end).inflate(1))) {
      if (other == this) continue;
      var obstruction = other.rayTrace(start, end, 1);
      if (obstruction != null)
        clearDistance =
            Math.min(
                clearDistance,
                start.distanceToSqr(other.toGlobalVector(obstruction.getLocation(), 1)));
    }
    // A tracked angle reaches the client several ticks later. Accept actual recent poses, never
    // arbitrary supplied angles.
    for (var pick : interactionPicks(start, end))
      if (pick.hit.getBlockPos().equals(packet.localPos())
          && start.distanceToSqr(pick.global) <= clearDistance + .001)
        return handlePlayerInteraction(player, packet.localPos(), packet.hand());
    return false;
  }

  public boolean handlePlayerInteraction(
      ServerPlayer player, BlockPos localPos, InteractionHand hand) {
    if (!hasOwnedStructure() || !isAlive()) return false;
    var info = contraption.getBlocks().get(localPos);
    if (info == null) return false;
    var interaction =
        com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour.REGISTRY.get(
            info.state());
    if (interaction != null && interaction.handlePlayerInteraction(player, hand, localPos, this))
      return true;
    int seat = contraption.getSeats().indexOf(localPos);
    if (seat >= 0 && !player.getItemInHand(hand).is(com.simibubi.create.AllItems.WRENCH)) {
      if (player.isPassenger()) return false;
      var passenger =
          com.simibubi.create.content.contraptions.actors.seat.SeatBlock.getLeashed(level(), player)
              .orElse(player);
      return addSittingPassenger(passenger, seat);
    }
    var storage = contraption.getStorage().getAllItemStorages().get(localPos);
    var provider =
        contraption
            .getStorage()
            .createMenuProvider(
                contraption,
                localPos,
                p ->
                    isAlive()
                        && hasOwnedStructure()
                        && p.distanceToSqr(toGlobalVector(Vec3.atCenterOf(localPos), 1)) < 64,
                p -> {
                  if (storage instanceof ChestMountedStorage)
                    level()
                        .playSound(
                            null,
                            BlockPos.containing(toGlobalVector(Vec3.atCenterOf(localPos), 1)),
                            SoundEvents.CHEST_CLOSE,
                            SoundSource.BLOCKS,
                            .75f,
                            1);
                });
    if (provider == null || player.openMenu(provider).isEmpty()) return false;
    var pos = BlockPos.containing(toGlobalVector(Vec3.atCenterOf(localPos), 1));
    level()
        .playSound(
            null,
            pos,
            storage instanceof ChestMountedStorage
                ? SoundEvents.CHEST_OPEN
                : SoundEvents.BARREL_OPEN,
            SoundSource.BLOCKS,
            .75f,
            1);
    return true;
  }

  @Override
  protected void addAdditionalSaveData(ValueOutput output) {
    if (contraption != null)
      output.store("Contraption", CompoundTag.CODEC, contraption.writeNBT(registryAccess(), false));
    output.putBoolean("Stalled", isStalled());
  }

  @Override
  protected void readAdditionalSaveData(ValueInput input) {
    var data = input.read("Contraption", CompoundTag.CODEC).orElse(null);
    if (data != null) setContraption(Contraption.fromNBT(level(), data, false));
    setStalled(input.getBooleanOr("Stalled", false));
  }

  public boolean addSittingPassenger(Entity passenger, int seat) {
    if (!hasOwnedStructure()
        || seat < 0
        || seat >= contraption.getSeats().size()
        || passenger instanceof net.fabricmc.fabric.api.entity.FakePlayer
        || !passenger.isAlive()) return false;
    for (var existing : List.copyOf(getPassengers()))
      if (Objects.equals(contraption.getSeatMapping().get(existing.getUUID()), seat)) {
        if (existing == passenger) return true;
        if (existing instanceof Player || !(passenger instanceof Player)) return false;
        existing.stopRiding();
      }
    if (passenger.getVehicle() != this && !passenger.startRiding(this, true, true)) return false;
    // Native startRiding returns false for the current vehicle; reseating still changes its slot.
    // A saved mapping can outlive a removed rider, so assigning the slot also removes stale
    // ownership.
    contraption
        .getSeatMapping()
        .entrySet()
        .removeIf(entry -> entry.getValue() == seat && !entry.getKey().equals(passenger.getUUID()));
    contraption.getSeatMapping().put(passenger.getUUID(), seat);
    if (passenger instanceof TamableAnimal animal) animal.setInSittingPose(true);
    positionRider(passenger);
    if (!level().isClientSide()) ContraptionNetworking.syncSeats(this, null, null);
    return true;
  }

  public Vec3 getPassengerPosition(Entity passenger, float partial) {
    if (contraption != null
        && passenger instanceof OrientedContraptionEntity child
        && contraption.getStabilizedSubContraptions().accepts(child))
      return contraption.getStabilizedSubContraptions().passengerPosition(child, partial);
    var seat = contraption == null ? null : contraption.getSeatOf(passenger.getUUID());
    if (seat == null) return null;
    double height = passenger.getBoundingBox().getYsize();
    return toGlobalVector(
            Vec3.atLowerCornerOf(seat)
                .add(
                    .5,
                    -passenger.getVehicleAttachmentPoint(this).y
                        + height
                        + .125
                        - SeatEntity.getCustomEntitySeatOffset(passenger),
                    .5),
            partial)
        .add(0, .5 - height, 0);
  }

  @Override
  public Vec3 getPassengerRidingPosition(Entity passenger) {
    var position = getPassengerPosition(passenger, 1);
    if (passenger instanceof OrientedContraptionEntity)
      return position == null ? passenger.position() : position;
    return position == null
        ? passenger.position().add(passenger.getVehicleAttachmentPoint(this))
        : position
            .add(0, SeatEntity.getCustomEntitySeatOffset(passenger) - .125, 0)
            .add(passenger.getVehicleAttachmentPoint(this));
  }

  @Override
  protected void positionRider(Entity passenger, MoveFunction move) {
    if (!hasPassenger(passenger)) return;
    var position = getPassengerPosition(passenger, 1);
    if (position != null)
      move.accept(
          passenger,
          position.x,
          position.y
              + (passenger instanceof OrientedContraptionEntity
                  ? 0
                  : SeatEntity.getCustomEntitySeatOffset(passenger) - .125),
          position.z);
  }

  @Override
  protected boolean canAddPassenger(Entity passenger) {
    if (passenger instanceof OrientedContraptionEntity)
      return hasOwnedStructure() && contraption.getStabilizedSubContraptions().accepts(passenger);
    return hasOwnedStructure()
        && contraption.getSeatMapping().size() < contraption.getSeats().size()
        && !(passenger instanceof net.fabricmc.fabric.api.entity.FakePlayer);
  }

  @Override
  protected void removePassenger(Entity passenger) {
    if (passenger instanceof OrientedContraptionEntity) {
      super.removePassenger(passenger);
      return;
    }
    var position = getPassengerPosition(passenger, 1);
    if (level() instanceof ServerLevel server) {
      var pending = ContraptionPassengerHandoff.pendingDismount(server, passenger.getUUID());
      if (pending != null) position = pending;
    }
    super.removePassenger(passenger);
    if (passenger instanceof TamableAnimal animal) animal.setInSittingPose(false);
    // Native passenger synchronization can temporarily detach/re-add riders on the client.
    // The authoritative seat mapping arrives separately and must survive that refresh.
    if (level().isClientSide()) return;
    if (position != null) dismountLocations.put(passenger.getUUID(), position);
    if (contraption != null) contraption.getSeatMapping().remove(passenger.getUUID());
    if (!level().isClientSide()) ContraptionNetworking.syncSeats(this, passenger, position);
  }

  @Override
  public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
    var position = dismountLocations.remove(passenger.getUUID());
    var imported = ContraptionPassengerData.takeImportedDismountLocation(passenger);
    if (position == null) position = imported;
    if (position == null) return super.getDismountLocationForPassenger(passenger);
    passenger.setOnGround(false);
    var mount = ContraptionPassengerData.takeMountLocation(passenger);
    if (passenger instanceof net.minecraft.server.level.ServerPlayer player)
      com.simibubi.create.foundation.advancement.TrainPassengerAdvancements
          .dismounted(player, mount, position);
    return position;
  }

  @Override
  public void onPassengerTurned(Entity passenger) {
    passenger.setYHeadRot(passenger.getYRot());
  }

  @Override
  public boolean hasExactlyOnePlayerPassenger() {
    return false;
  }

  public static class ContraptionRotationState {
    public org.joml.Quaternionfc orientation;
    public float xRotation, yRotation, zRotation, secondYRotation;
    private com.simibubi.create.foundation.collision.Matrix3d matrix;

    public com.simibubi.create.foundation.collision.Matrix3d asMatrix() {
      if (matrix == null) {
        if (orientation != null)
          return matrix =
              new com.simibubi.create.foundation.collision.Matrix3d()
                  .asQuaternion(new org.joml.Quaternionf(orientation).conjugate());
        matrix = new com.simibubi.create.foundation.collision.Matrix3d().asIdentity();
        if (xRotation != 0)
          matrix.multiply(
              new com.simibubi.create.foundation.collision.Matrix3d()
                  .asXRotation((float) Math.toRadians(-xRotation)));
        if (yRotation != 0)
          matrix.multiply(
              new com.simibubi.create.foundation.collision.Matrix3d()
                  .asYRotation((float) Math.toRadians(-yRotation)));
        if (zRotation != 0)
          matrix.multiply(
              new com.simibubi.create.foundation.collision.Matrix3d()
                  .asZRotation((float) Math.toRadians(-zRotation)));
      }
      return matrix;
    }

    public boolean hasVerticalRotation() {
      return orientation != null
          ? ContraptionOrientation.apply(orientation, new Vec3(0, 1, 0))
                  .distanceToSqr(new Vec3(0, 1, 0))
              > 1e-10
          : xRotation != 0 || zRotation != 0;
    }

    public float getYawOffset() {
      return secondYRotation;
    }
  }
}
