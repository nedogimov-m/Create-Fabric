package com.simibubi.create.content.contraptions.actors.seat;

import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** Invisible, persistent vanilla vehicle. Passenger movement uses the native entity attachment API. */
public final class SeatEntity extends Entity {
    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) { super(type, level); noPhysics = true; }
    public SeatEntity(Level level) { this(AllEntityTypes.SEAT, level); }

    @Override public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        setBoundingBox(getBoundingBox().move(new Vec3(x, y, z).subtract(getBoundingBox().getCenter())));
    }
    @Override protected void positionRider(Entity passenger, MoveFunction callback) {
        if (!hasPassenger(passenger)) return;
        double height = getPassengerRidingPosition(passenger).y - passenger.getVehicleAttachmentPoint(this).y;
        callback.accept(passenger, getX(), 1 / 16d + height + getCustomEntitySeatOffset(passenger), getZ());
    }
    public static double getCustomEntitySeatOffset(Entity entity) {
        if (entity instanceof Slime) return 0;
        if (entity instanceof Parrot || entity instanceof Cat) return 1 / 12f;
        if (entity instanceof Skeleton || entity instanceof Spider) return 1 / 8f;
        if (entity instanceof Wolf) return 1 / 16f;
        if (entity instanceof Frog) return 1.5 / 16f;
        if (entity instanceof PackageEntity) return 3 / 32f;
        return 0;
    }
    @Override public void onPassengerTurned(Entity passenger) { passenger.setYHeadRot(passenger.getYRot()); }
    @Override public void setDeltaMovement(Vec3 velocity) {}
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override public void tick() {
        if (level().isClientSide()) return;
        // Do not turn an entity tick into a loading ticket or read a chunk's pending FULL future.
        if (level().getChunkSource().getChunkNow(getBlockX() >> 4, getBlockZ() >> 4) == null) return;
        if (!isVehicle() || !(LoadedLevelAccess.blockState(level(), blockPosition()).getBlock() instanceof SeatBlock)) { ejectPassengers(); discard(); }
    }
    @Override protected boolean canAddPassenger(Entity passenger) { return !(passenger instanceof FakePlayer) && getPassengers().isEmpty() && super.canAddPassenger(passenger); }
    @Override protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (passenger instanceof TamableAnimal animal) animal.setInSittingPose(true);
    }
    @Override protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (passenger instanceof TamableAnimal animal) animal.setInSittingPose(false);
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) { return super.getDismountLocationForPassenger(passenger).add(0, .5, 0); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {}
    @Override protected void readAdditionalSaveData(ValueInput input) {}
    @Override protected void addAdditionalSaveData(ValueOutput output) {}
}
