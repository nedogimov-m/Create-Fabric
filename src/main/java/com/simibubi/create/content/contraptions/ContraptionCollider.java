package com.simibubi.create.content.contraptions;

import com.simibubi.create.content.contraptions.actors.seat.*;
import com.simibubi.create.foundation.collision.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Source continuous OBB separation and support response against each actual local block shape. */
public final class ContraptionCollider {
  public static Predicate<Player> isLocalPlayer = player -> false;

  @FunctionalInterface
  public interface ClientMotion {
    void accept(AbstractContraptionEntity owner, Vec3 motion, Vec3 carry);
  }

  public static ClientMotion clientMotion = (entity, motion, carry) -> {};
  public static Consumer<TrainCollisionPacket> clientTrainCollision = packet -> {};

  public static void collideEntities(AbstractContraptionEntity owner) {
    var level = owner.level();
    var structure = owner.getContraption();
    if (structure == null) return;
    var rotation = owner.getRotationState();
    var matrix = rotation.asMatrix();
    var anchorMotion = owner.getAnchorVec().subtract(owner.getPrevAnchorVec());
    var dense = new CollisionList();
    for (var entity :
        level.getEntitiesOfClass(
            Entity.class,
            owner.getBoundingBox().inflate(2).expandTowards(0, 32, 0),
            owner::canCollideWith)) {
      if (!entity.isAlive() || level.tickRateManager().isEntityFrozen(entity)) continue;
      boolean local =
          entity instanceof Player player && level.isClientSide() && isLocalPlayer.test(player);
      if (entity instanceof Player && level.isClientSide() && !local) continue;
      var position = entity.position();
      var bounds = entity.getBoundingBox();
      var velocity = entity.getDeltaMovement();
      if (local && bounds.getYsize() > 1) bounds = bounds.contract(0, 2 / 16d, 0);
      // Source contact tolerance keeps an end-of-sweep floor touch from disappearing
      // when native player movement reports exactly the previous separation epsilon.
      bounds = bounds.inflate(1e-7);
      var center = owner.toLocalVector(bounds.getCenter(), 1);
      var extents = new Vec3(bounds.getXsize() / 2, bounds.getYsize() / 2, bounds.getZsize() / 2);
      var obb = new OrientedBB(center, extents, matrix);
      var motion = matrix.transform(velocity.subtract(anchorMotion));
      var localBounds = envelope(center, extents, matrix).expandTowards(motion).inflate(1);
      var shapes = getPotentiallyCollidedShapes(structure, localBounds);
      var result =
          ContinuousOBBCollider.collideMany(
              shapes, dense, obb, motion, entity.maxUpStep(), !rotation.hasVerticalRotation());
      var response = matrix.transformTransposed(result.collisionResponse);
      var normal = matrix.transformTransposed(result.normal).normalize();
      boolean hard = response.lengthSqr() > 0;
      boolean temporal = result.temporalResponse != 1;
      boolean surface =
          result.surfaceCollision && (normal.y > .4 || normal.lengthSqr() == 0 && response.y > 0);
      if (entity instanceof ServerPlayer server) {
        if (surface) {
          owner.registerColliding(entity);
          if (server.connection != null) server.connection.resetFlyingTicks();
        }
        continue;
      }
      var motionResponse =
          matrix.transformTransposed(motion.scale(result.temporalResponse)).add(anchorMotion);
      var collisionLocation =
          matrix
              .transformTransposed(result.location)
              .add(position.add(entity.getBoundingBox().getCenter()).scale(.5));
      if (temporal) collisionLocation = collisionLocation.add(0, motionResponse.y, 0);
      var localContact = BlockPos.containing(owner.toLocalVector(collisionLocation, 1));
      var info =
          structure.isHiddenInPortal(localContact) ? null : structure.getBlocks().get(localContact);
      if (info == null) {
        var below = BlockPos.containing(owner.toLocalVector(entity.position().add(0, -.05, 0), 1));
        info = structure.isHiddenInPortal(below) ? null : structure.getBlocks().get(below);
      }
      var state = info == null ? Blocks.AIR.defaultBlockState() : info.state();
      double bounce = state.getBlock().getBounceRestitution(),
          slide = Math.max(0, state.getBlock().getFriction() - .6f);
      var entityLocal = BlockPos.containing(owner.toLocalVector(position, 1));
      var inside =
          structure.isHiddenInPortal(entityLocal) ? null : structure.getBlocks().get(entityLocal);
      if (inside != null && inside.state().is(BlockTags.CLIMBABLE)) {
        surface = true;
        response = response.add(0, .1, 0);
        hard = true;
      }
      if ((hard || temporal) && info != null) {
        var interaction =
            com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour.REGISTRY.get(
                state);
        if (interaction != null) {
          interaction.handleEntityCollision(entity, info.pos(), owner);
          if (entity.isPassenger()) continue;
        }
      }
      if ((hard || temporal)
          && !level.isClientSide()
          && info != null
          && state.getBlock() instanceof SeatBlock
          && SeatBlock.canBePickedUp(entity)) {
        owner.addSittingPassenger(entity, structure.getSeats().indexOf(info.pos()));
        if (entity.getVehicle() == owner) continue;
      }
      if (bounce > 0
          && (hard || temporal)
          && normal.lengthSqr() > 0
          && bounceEntity(entity, normal, owner, bounce)) {
        level.playSound(
            local ? (Player) entity : null,
            entity.blockPosition(),
            SoundEvents.SLIME_BLOCK_FALL,
            SoundSource.BLOCKS,
            .5f,
            1);
        continue;
      }
      var originalVelocity = velocity;
      if (temporal && motionResponse.y != velocity.y) {
        velocity = new Vec3(velocity.x, motionResponse.y, velocity.z);
        entity.setDeltaMovement(velocity);
      }
      if (hard) {
        if (velocity.x != 0 && Math.abs(response.x) > 1 / 128d && velocity.x > 0 == response.x < 0)
          velocity = velocity.multiply(0, 1, 1);
        if (velocity.y != 0 && response.y != 0 && velocity.y > 0 == response.y < 0)
          velocity = velocity.multiply(1, 0, 1).add(0, anchorMotion.y, 0);
        if (velocity.z != 0 && Math.abs(response.z) > 1 / 128d && velocity.z > 0 == response.z < 0)
          velocity = velocity.multiply(1, 1, 0);
      }
      if (bounce == 0
          && slide > 0
          && normal.lengthSqr() > 0
          && (hard || temporal)
          && rotation.hasVerticalRotation()) {
        double factor = normal.multiply(1, 0, 1).length() * 1.25;
        var input = originalVelocity.multiply(0, .9, 0).add(0, -.01, 0);
        var slideNormal = normal.cross(input.cross(normal)).normalize();
        velocity =
            velocity
                .multiply(.85, 0, .85)
                .add(slideNormal.scale((.2 + slide) * input.length() * factor))
                .add(0, -.1 - normal.y * .125, 0);
        entity.setDeltaMovement(velocity);
      }
      if (!hard && !surface) continue;
      var allowed = collide(response, entity);
      entity.setPos(position.add(allowed));
      velocity = handleDamageFromTrain(owner, anchorMotion, entity, velocity, local);
      Vec3 carry = Vec3.ZERO;
      if (surface) {
        owner.registerColliding(entity);
        entity.resetFallDistance();
        boolean canWalk = bounce != 0 || slide == 0;
        if (canWalk || !rotation.hasVerticalRotation()) {
          if (canWalk) entity.setOnGround(true);
          if (entity instanceof ItemEntity) velocity = velocity.multiply(.5, 1, .5);
        }
        carry = collide(owner.getContactPointMotion(entity.position()), entity);
        entity.setPos(entity.getX() + carry.x, entity.getY(), entity.getZ() + carry.z);
      }
      entity.setDeltaMovement(velocity);
      entity.syncVelocity = true;
      if (local) clientMotion.accept(owner, velocity, carry);
    }
  }

  private static AABB envelope(Vec3 center, Vec3 extents, Matrix3d matrix) {
    var x = matrix.transform(new Vec3(extents.x, 0, 0));
    var y = matrix.transform(new Vec3(0, extents.y, 0));
    var z = matrix.transform(new Vec3(0, 0, extents.z));
    return new AABB(center, center)
        .inflate(
            Math.abs(x.x) + Math.abs(y.x) + Math.abs(z.x),
            Math.abs(x.y) + Math.abs(y.y) + Math.abs(z.y),
            Math.abs(x.z) + Math.abs(y.z) + Math.abs(z.z));
  }

  /** Source damage is evaluated at the real accepted collision, before support registration. */
  private static Vec3 handleDamageFromTrain(AbstractContraptionEntity owner, Vec3 trainMotion,
      Entity entity, Vec3 entityMotion, boolean localPlayer) {
    if (!(owner instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity train)
        || !entity.onGround()) return entityMotion;
    if (ContraptionPassengerData.takeGrounded(entity)) return entityMotion;
    if (train.collidingEntities.containsKey(entity) || entity instanceof ItemEntity
        || train.nonDamageTicks != 0
        || !com.simibubi.create.content.trains.TrainConfig.get(owner.level()).trainsCauseDamage())
      return entityMotion;
    var difference = trainMotion.subtract(entity.getDeltaMovement());
    if (difference.length() <= .35f || trainMotion.length() <= .35f) return entityMotion;
    double damage = difference.length();
    if (entity.getType().getCategory() == MobCategory.MONSTER) damage *= 2;
    if (entity instanceof Player player && (player.isCreative() || player.isSpectator()))
      return entityMotion;
    var level = owner.level();
    if (localPlayer) {
      clientTrainCollision.accept(new TrainCollisionPacket((int) (damage * 16),
          owner.getId(), owner.getUUID()));
      level.playSound((Player) entity, entity.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
          SoundSource.NEUTRAL, 1, .75f);
    } else {
      if (level instanceof net.minecraft.server.level.ServerLevel server) {
        entity.hurtServer(server,
            com.simibubi.create.foundation.damageTypes.CreateDamageSources.runOver(level, owner),
            (int) (damage * 16));
      }
      level.playSound(null, entity.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
          SoundSource.NEUTRAL, 1, .75f);
      if (level instanceof net.minecraft.server.level.ServerLevel server && !entity.isAlive())
        train.getControllingPlayer().map(server::getPlayerByUUID)
            .ifPresent(player -> com.simibubi.create.foundation.advancement.PrerequisiteAdvancements
                .award(player, "train_roadkill"));
    }
    var result = entityMotion.add(trainMotion.multiply(1, 0, 1).normalize()
        .add(0, .25, 0).scale(damage * 4)).add(difference);
    double length = result.length();
    return length > 3 ? result.scale(3 / length) : result;
  }

  public static CollisionList getPotentiallyCollidedShapes(Contraption structure, AABB bounds) {
    var result = new CollisionList();
    var out = new CollisionList.Populate(result);
    var view = structure.getCollisionLevel();
    for (var pos :
        BlockPos.betweenClosed(
            BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
            BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
      var info = structure.getBlocks().get(pos);
      if (info == null || structure.isHiddenInPortal(pos)) continue;
      out.offsetX = pos.getX();
      out.offsetY = pos.getY();
      out.offsetZ = pos.getZ();
      info.state().getCollisionShape(view, pos).forAllBoxes(out);
    }
    return result;
  }

  public static boolean bounceEntity(
      Entity entity, Vec3 normal, AbstractContraptionEntity owner, double factor) {
    if (factor == 0 || entity.isSuppressingBounce()) return false;
    var motion = entity.getDeltaMovement().subtract(owner.getContactPointMotion(entity.position()));
    var delta = normal.scale(factor * 2 * motion.dot(normal));
    if (delta.lengthSqr() < .1) return false;
    entity.setDeltaMovement(entity.getDeltaMovement().subtract(delta));
    entity.syncVelocity = true;
    return true;
  }

  /**
   * The native world collision lookup requests existing chunks only, including the original step-up
   * pass.
   */
  public static Vec3 collide(Vec3 movement, Entity entity) {
    if (movement.lengthSqr() == 0) return movement;
    var box = entity.getBoundingBox();
    var level = entity.level();
    var others = level.getEntityCollisions(entity, box.expandTowards(movement));
    var clipped = Entity.collideBoundingBox(entity, movement, box, level, others);
    if (entity.maxUpStep() > 0
        && clipped.y != movement.y
        && movement.y < 0
        && (clipped.x != movement.x || clipped.z != movement.z)) {
      var step =
          Entity.collideBoundingBox(
              entity, new Vec3(movement.x, entity.maxUpStep(), movement.z), box, level, others);
      var up =
          Entity.collideBoundingBox(
              entity,
              new Vec3(0, entity.maxUpStep(), 0),
              box.expandTowards(movement.x, 0, movement.z),
              level,
              others);
      if (up.y < entity.maxUpStep()) {
        var across =
            Entity.collideBoundingBox(
                    entity, new Vec3(movement.x, 0, movement.z), box.move(up), level, others)
                .add(up);
        if (across.horizontalDistanceSqr() > step.horizontalDistanceSqr()) step = across;
      }
      if (step.horizontalDistanceSqr() > clipped.horizontalDistanceSqr())
        return step.add(
            Entity.collideBoundingBox(
                entity, new Vec3(0, -step.y + movement.y, 0), box.move(step), level, others));
    }
    return clipped;
  }

  private ContraptionCollider() {}
}
