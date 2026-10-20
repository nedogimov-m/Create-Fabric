package com.simibubi.create.content.trains.entity;

import com.simibubi.create.content.trains.CubeParticleData;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.*;
import net.minecraft.world.phys.Vec3;

/** Native lifecycle of genuine source carriage sound, contact/derail/portal particles. */
public final class CarriageClientEffects implements TrainClientRegistry.ClientEffects {
  private static final Map<CarriageContraptionEntity, CarriageClientEffects> active =
      new IdentityHashMap<>();
  private final CarriageContraptionEntity entity;
  final CarriageSounds sounds;
  private final CarriageParticles particles;
  private final Vec3 derailParticleOffset;
  private final Set<BlockPos> particleSlice = new HashSet<>();
  private float particleAvgY;

  public static CarriageClientEffects forEntity(CarriageContraptionEntity entity) {
    return active.computeIfAbsent(entity, CarriageClientEffects::new);
  }

  private CarriageClientEffects(CarriageContraptionEntity entity) {
    if (!entity.level().isClientSide() || entity.getCarriage() == null)
      throw new IllegalArgumentException(
          "Native carriage effects require an actual bound client front");
    this.entity = entity;
    sounds = new CarriageSounds(entity);
    particles = new CarriageParticles(entity);
    derailParticleOffset = offsetRandomly(entity.level().getRandom(), 1.5f).multiply(1, .25f, 1);
    portalCutoffChanged();
  }

  private static Vec3 offsetRandomly(RandomSource random, float radius) {
    return new Vec3(
        (random.nextFloat() - .5f) * 2 * radius,
        (random.nextFloat() - .5f) * 2 * radius,
        (random.nextFloat() - .5f) * 2 * radius);
  }

  @Override
  public void tick(Carriage.DimensionalCarriageEntity dimensional) {
    var client = Minecraft.getInstance();
    if (entity.isRemoved() || client.level != entity.level() || client.getConnection() == null) {
      stop();
      return;
    }
    sounds.tick(dimensional);
    particles.tick(dimensional);
    if (entity.getCarriage().train.derailed && entity.level().getRandom().nextFloat() < 1 / 20f) {
      var point = entity.position().add(derailParticleOffset);
      entity
          .level()
          .addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, point.x, point.y, point.z, 0, .04, 0);
    }
    if (dimensional.pivot != null) spawnPortalParticles(dimensional);
  }

  @Override
  public void portalCutoffChanged() {
    particleSlice.clear();
    particleAvgY = 0;
    if (!(entity.getContraption() instanceof CarriageContraption body)) return;
    var forward = body.getAssemblyDirection().getClockWise();
    boolean alongX = forward.getAxis() == Direction.Axis.X;
    for (var pos : body.getBlocks().keySet()) {
      if (!body.atSeam(pos)) continue;
      int horizontal = (alongX ? pos.getX() : pos.getZ()) * forward.getAxisDirection().getStep();
      particleSlice.add(new BlockPos(horizontal, pos.getY(), 0));
      particleAvgY += pos.getY();
    }
    if (!particleSlice.isEmpty()) particleAvgY /= particleSlice.size();
  }

  private void spawnPortalParticles(Carriage.DimensionalCarriageEntity dimensional) {
    if (particleSlice.isEmpty()) return;
    var pivot = dimensional.pivot.getLocation().add(0, 1.5f, 0);
    boolean alongX = Mth.equal(pivot.x, Math.round(pivot.x));
    int extraFlip = Direction.fromYRot(entity.yaw).getAxisDirection().getStep();
    var emitter = pivot.add(0, particleAvgY, 0);
    double speed = entity.position().distanceTo(entity.getPrevPositionVec());
    int size = (int) (particleSlice.size() * Mth.clamp(4 - speed * 4, 0, 4));
    var random = entity.level().getRandom();
    for (var original : particleSlice) {
      if (size != 0 && random.nextInt(size) != 0) continue;
      var pos = alongX ? new BlockPos(0, original.getY(), original.getX()) : original;
      var point = pivot.add(pos.getX() * extraFlip, pos.getY(), pos.getZ() * extraFlip);
      var data =
          new CubeParticleData(.25f, 0, .5f, .65f + (random.nextFloat() - .5f) * .25f, 4, false);
      var motion = point.subtract(emitter).normalize().scale(.325f);
      motion =
          CarriageBogey.rotate(
                  motion, random.nextFloat() * 360, alongX ? Direction.Axis.X : Direction.Axis.Z)
              .add(offsetRandomly(random, .25f));
      entity.level().addParticle(data, point.x, point.y, point.z, motion.x, motion.y, motion.z);
    }
  }

  @Override
  public void stop() {
    sounds.stop();
    active.remove(entity, this);
  }

  public static void clearAll() {
    for (var effects : List.copyOf(active.values())) effects.stop();
    active.clear();
  }
}
