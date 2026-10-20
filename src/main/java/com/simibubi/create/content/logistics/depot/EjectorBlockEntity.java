package com.simibubi.create.content.logistics.depot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.data.IntAttached;
import net.createmod.catnip.data.Pair;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Source weighted ejector with genuine kinetic charge and transactional depot/flight stock owners. */
public final class EjectorBlockEntity extends KineticBlockEntity implements BlockEntityBehaviour.Provider {
    public enum State { CHARGED, LAUNCHING, RETRACTING }
    public final DepotBehaviour depotBehaviour = new DepotBehaviour(this);
    private final EntityLauncher launcher = new EntityLauncher(1, 0);
    private final LerpedFloat lidProgress = LerpedFloat.linear().startWithValue(1);
    private final List<IntAttached<ItemStack>> launchedItems = new ArrayList<>();
    private State state = State.RETRACTING;
    private boolean powered, launch, initialized;
    private int minimumStackSize, scanCooldown;
    private ItemStack trackedItem;
    private Pair<Vec3, BlockPos> earlyTarget;
    private float earlyTargetTime;
    private UUID placementOwner;
    private long placementNonce, placementExpires;
    private final Map<UUID, LaunchReceipt> launches = new HashMap<>();
    private static final class LaunchReceipt {
        final long nonce, expires;
        boolean award, glider;
        LaunchReceipt(long nonce, long expires, boolean award, boolean glider) {
            this.nonce = nonce; this.expires = expires; this.award = award; this.glider = glider;
        }
    }
    private record Flights(List<IntAttached<ItemStack>> items, int tracked, int cooldown) {}
    private final SnapshotParticipant<Flights> flights = new SnapshotParticipant<>() {
        @Override protected Flights createSnapshot() {
            var copy = new ArrayList<IntAttached<ItemStack>>(); int tracked = -1;
            for (var item : launchedItems) {
                if (item.getValue() == trackedItem) tracked = copy.size();
                copy.add(IntAttached.with(item.getFirst(), item.getValue().copy()));
            }
            return new Flights(copy, tracked, scanCooldown);
        }
        @Override protected void readSnapshot(Flights snapshot) {
            launchedItems.clear(); launchedItems.addAll(snapshot.items);
            trackedItem = snapshot.tracked < 0 ? null : launchedItems.get(snapshot.tracked).getValue();
            scanCooldown = snapshot.cooldown;
        }
        @Override protected void onFinalCommit() { setChanged(); sync(); }
    };
    private static final Codec<IntAttached<ItemStack>> FLIGHT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("Location").forGetter(IntAttached::getFirst),
            ItemStack.CODEC.fieldOf("Item").forGetter(IntAttached::getValue)
    ).apply(i, IntAttached::with));

    public EjectorBlockEntity(BlockPos pos, BlockState state) {
        super(AllBlockEntityTypes.WEIGHTED_EJECTOR, pos, state);
        depotBehaviour.enableMerging();
        depotBehaviour.withMaxStackSize(() -> minimumStackSize).acceptingWhen(() -> this.state == State.CHARGED)
                .pullingFrom(side -> side != getFacing());
    }
    @Override public void sync() { super.sync(); }
    public EntityLauncher getLauncher() { return launcher; }
    public State getState() { return state; }
    public boolean isPowered() { return powered; }
    public int getMinimumStackSize() { return minimumStackSize; }
    public void setMinimumStackSize(int value) {
        if (value < 0 || value > 64) throw new IllegalArgumentException("Source stack setting is0..64");
        minimumStackSize = value; setChanged(); sync();
    }
    public List<IntAttached<ItemStack>> getLaunchedItems() {
        return launchedItems.stream().map(item -> IntAttached.with(item.getFirst(), item.getValue().copy())).toList();
    }
    public Pair<Vec3, BlockPos> getEarlyTarget() { return earlyTarget; }
    public float getEarlyTargetTime() { return earlyTargetTime; }
    public Direction getFacing() { return getBlockState().getValue(EjectorBlock.HORIZONTAL_FACING); }
    public float getLidProgress(float partial) { return lidProgress.getValue(partial); }
    public AABB activationBounds() { return new AABB(worldPosition).inflate(-1 / 16f, 0, -1 / 16f); }

    public void beginPlacement(ServerPlayer player) {
        placementOwner = player.getUUID(); placementNonce = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        placementExpires = level.getGameTime() + 200;
        ServerPlayNetworking.send(player, new EjectorPlacementPacket.ClientBoundRequest(worldPosition, placementNonce));
    }
    public boolean acceptPlacement(ServerPlayer player, long nonce, int horizontal, int vertical, Direction facing) {
        if (!player.getUUID().equals(placementOwner) || nonce != placementNonce || level.getGameTime() > placementExpires
                || !facing.getAxis().isHorizontal() || horizontal < 1 || horizontal > EjectorConfig.get(level).maxDistance()) return false;
        var target = worldPosition.relative(facing, horizontal).above(vertical);
        if (!level.isInValidBounds(target)) return false;
        placementOwner = null; placementNonce = 0;
        level.setBlockAndUpdate(worldPosition, getBlockState().setValue(EjectorBlock.HORIZONTAL_FACING, facing));
        setTarget(horizontal, vertical); return true;
    }
    public void setTarget(int horizontal, int vertical) { launcher.set(Math.max(1, horizontal), vertical); setChanged(); sync(); }
    public BlockPos getTargetPosition() { return worldPosition.relative(getFacing(), launcher.getHorizontalDistance()).above(launcher.getVerticalDistance()); }
    public void updateSignal() {
        if (level == null) return;
        boolean value = LoadedLevelAccess.getBestNeighborSignal(level, worldPosition) > 0;
        if (value == powered) return; powered = value; setChanged(); sync();
    }
    public void activate() { if(!depotBehaviour.isActiveWorldOwner())return;launch = true; nudgeEntities(); }
    public boolean canPlayerTrigger(ServerPlayer player) {
        return !isRemoved() && !powered && state == State.CHARGED && launcher.getHorizontalDistance() != 0
                && player.isAlive() && !player.isSuppressingBounce() && activationBounds().intersects(player.getBoundingBox());
    }
    private void nudgeEntities() {
        if (level == null) return;
        for (var entity : level.getEntitiesOfClass(Entity.class, activationBounds()))
            if (entity.isAlive() && entity.getPistonPushReaction() != PushReaction.IGNORE_ENTITY && !(entity instanceof Player))
                entity.setPos(entity.getX(), entity.getY() + .125f, entity.getZ());
    }
    public void activateDeferred() {
        if (!depotBehaviour.isActiveWorldOwner()) return;
        if (state != State.CHARGED && !(level.isClientSide() && state == State.LAUNCHING)) return;
        if (level.isClientSide() && !isVirtual()) return; // Native player prediction is delivered to the actual launched player below.
        launchItems();
        for (var entity : level.getEntitiesOfClass(Entity.class, activationBounds())) {
            if (!entity.isAlive() || entity instanceof ItemEntity || entity instanceof PackageEntity
                    || entity.getPistonPushReaction() == PushReaction.IGNORE_ENTITY) continue;
            entity.setOnGround(false);
            if (entity instanceof ServerPlayer player) launchPlayer(player);
            else if (!(entity instanceof Player)) {
                entity.setPos(worldPosition.getX() + .5f, worldPosition.getY() + 1, worldPosition.getZ() + .5f);
                launcher.applyMotion(entity, getFacing());
            }
        }
        lidProgress.chase(1, .8f, Chaser.EXP); state = State.LAUNCHING;
        level.playSound(null, worldPosition, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, .35f, 1f);
        level.playSound(null, worldPosition, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, .1f, 1.4f);
    }
    private void launchPlayer(ServerPlayer player) {
        player.teleportTo(worldPosition.getX() + .5f, worldPosition.getY() + 1, worldPosition.getZ() + .5f);
        player.setOnGround(false); launcher.applyMotion(player, getFacing());
        boolean glider = LivingEntity.canGlideUsing(player.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST);
        if (glider) {
            player.setXRot(-35); player.setYRot(getFacing().toYRot());
            player.setDeltaMovement(player.getDeltaMovement().scale(.75f)); deployElytra(player);
        }
        long nonce = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        boolean maxed = (long) launcher.getHorizontalDistance() * launcher.getHorizontalDistance()
                + (long) launcher.getVerticalDistance() * launcher.getVerticalDistance() >= 625;
        launches.put(player.getUUID(), new LaunchReceipt(nonce, level.getGameTime() + Math.max(40, (int) launcher.getTotalFlyingTicks() + 20), maxed, glider));
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        ServerPlayNetworking.send(player, new EjectorLaunchPacket(worldPosition, nonce, launcher.getHorizontalDistance(), launcher.getVerticalDistance(), getFacing(), glider));
    }
    public boolean deployElytra(Player player) {
        return LivingEntity.canGlideUsing(player.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST)
                && (player.isFallFlying() || player.tryToStartFallFlying());
    }
    private LaunchReceipt recentLaunch(ServerPlayer player, long nonce) {
        var receipt = launches.get(player.getUUID());
        return receipt != null && receipt.nonce == nonce && level.getGameTime() <= receipt.expires ? receipt : null;
    }
    public boolean acceptAward(ServerPlayer player, long nonce) {
        var receipt = recentLaunch(player, nonce); if (receipt == null || !receipt.award) return false;
        receipt.award = false; EjectorAwardPacket.award(player); return true;
    }
    public boolean acceptElytra(ServerPlayer player, long nonce) {
        var receipt = recentLaunch(player, nonce); if (receipt == null || !receipt.glider || !deployElytra(player)) return false;
        receipt.glider = false; return true;
    }

    private void launchItems() {
        Direction facing = getFacing().getOpposite();
        if (AbstractFunnelBlock.getFunnelFacing(LoadedLevelAccess.blockState(level, worldPosition.above())) == facing) {
            var target = depotBehaviour.directInput.exportingFunnel(facing);
            if (target != null) try (var tx = Transaction.openOuter()) { depotBehaviour.exportTo(target, tx); tx.commit(); }
            return;
        }
        try (var tx = Transaction.openOuter()) {
            flights.updateSnapshots(tx);
            for (var stack : depotBehaviour.takeAll(tx)) {
                if (trackedItem == null && scanCooldown == 0) { scanCooldown = EjectorConfig.get(level).scanInterval(); trackedItem = stack; }
                launchedItems.add(IntAttached.withZero(stack));
            }
            tx.commit();
        }
        if (!level.isClientSide())
            for (var side : Direction.values()) {
                var position = worldPosition.relative(side); var neighbor = LoadedLevelAccess.blockState(level, position);
                if (neighbor.getBlock() instanceof ObserverBlock && neighbor.getValue(ObserverBlock.FACING) == side.getOpposite())
                    level.neighborShapeChanged(side.getOpposite(), position, worldPosition, getBlockState(), 2, 512);
            }
    }
    private void ejectIfTriggered() {
        if (powered || depotBehaviour.getPresentStackSize() == 0 || depotBehaviour.getPresentStackSize() < minimumStackSize) return;
        var held = depotBehaviour.getHeldItem(); if (held != null && held.beltPosition < .49f) return;
        Direction facing = getFacing().getOpposite();
        if (AbstractFunnelBlock.getFunnelFacing(LoadedLevelAccess.blockState(level, worldPosition.above())) == facing && held != null) {
            var remainder = depotBehaviour.directInput.tryExportingToBeltFunnel(held.stack, facing, true);
            if (remainder == null || !remainder.isEmpty()) return;
        }
        var target = getTargetOpenInv();
        if (target != null && held != null && target.handleInsertion(held.stack, Direction.UP, true).getCount() == held.stack.getCount()) return;
        activate(); setChanged(); sync();
    }
    public DirectBeltInputBehaviour getTargetOpenInv() {
        var target = earlyTarget != null ? earlyTarget.getSecond()
                : worldPosition.above(launcher.getVerticalDistance()).relative(getFacing(), Math.max(1, launcher.getHorizontalDistance()));
        return BlockEntityBehaviour.get(level, target, DirectBeltInputBehaviour.TYPE);
    }
    public Vec3 getLaunchedItemLocation(float time) { return launcher.getGlobalPos(time, getFacing().getOpposite(), worldPosition); }
    public Vec3 getLaunchedItemMotion(float time) { return launcher.getGlobalVelocity(time, getFacing().getOpposite(), worldPosition).scale(.5f); }
    private boolean scanTrajectoryForObstacles(int time) {
        if (time <= 2) return false;
        var source = getLaunchedItemLocation(time); var target = getLaunchedItemLocation(time + 1);
        if(!LoadedLevelAccess.isLoaded(level,BlockPos.containing(source))||!LoadedLevelAccess.isLoaded(level,BlockPos.containing(target)))return false;
        var hit = level.clip(new ClipContext(source, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        boolean miss = hit.getType() == HitResult.Type.MISS;
        if (!miss) {
            var block = LoadedLevelAccess.blockState(level, hit.getBlockPos());
            if (block.getBlock() instanceof FunnelBlock && block.hasProperty(FunnelBlock.EXTRACTING) && block.getValue(FunnelBlock.EXTRACTING)) miss = true;
        }
        if (miss) {
            if (earlyTarget != null && earlyTargetTime < time + 1) { earlyTarget = null; earlyTargetTime = 0; }
            return false;
        }
        var hitPosition = hit.getLocation();
        earlyTarget = Pair.of(hitPosition.add(Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()).scale(.25f)), hit.getBlockPos());
        earlyTargetTime = (float) (time + source.distanceTo(hitPosition) / source.distanceTo(target)); setChanged(); sync(); return true;
    }
    private boolean placeItemAtTarget(IntAttached<ItemStack> flight, float maxTime) {
        try (var tx = Transaction.openOuter()) {
            flights.updateSnapshots(tx);
            var target = getTargetOpenInv(); var stack = flight.getValue();
            long inserted = target == null ? 0 : target.insert(stack, Direction.UP, tx);
            if (stack == trackedItem) trackedItem = null;
            flight.setSecond(stack.copyWithCount(stack.getCount() - (int) inserted)); tx.commit();
        }
        if (!flight.getValue().isEmpty()) {
            var position = earlyTarget != null ? earlyTarget.getFirst() : getLaunchedItemLocation(maxTime);
            var item = new ItemEntity(level, position.x, position.y, position.z, flight.getValue().copy());
            item.setDeltaMovement(getLaunchedItemMotion(maxTime)); item.setDefaultPickUpDelay();
            if (!level.addFreshEntity(item)) return false; // The flight remains the sole owner if a native spawn is refused.
        }
        if (trackedItem == flight.getValue()) trackedItem = null;
        return true;
    }
    public float getWindUpSpeed() {
        int horizontal = launcher.getHorizontalDistance(), vertical = launcher.getVerticalDistance();
        float distance = horizontal == 0 && vertical == 0 ? 1 : Mth.sqrt(horizontal * horizontal + vertical * vertical);
        return Math.abs(getSpeed()) / 256f / distance;
    }
    public static void tick(Level level, BlockPos pos, BlockState block, EjectorBlockEntity ejector) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, block, ejector)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level, pos, block, ejector); ejector.tick();
    
        }
    }
    public void tick() {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, worldPosition, getBlockState(), this)) {
            if (!createTick.shouldTick()) return;

        if (!initialized) { initialized = true; updateSignal(); }
         State previous = state;
        float totalTime = Math.max(3, (float) launcher.getTotalFlyingTicks());
        if (scanCooldown > 0) scanCooldown--;
        if (launch) { launch = false; activateDeferred(); }
        for (int index = 0; index < launchedItems.size();) {
            var flight = launchedItems.get(index);
            boolean hit = (!level.isClientSide() || isVirtual()) && flight.getValue() == trackedItem && scanTrajectoryForObstacles(flight.getFirst());
            float maxTime = earlyTarget == null ? totalTime : Math.min(earlyTargetTime, totalTime);
            if ((hit || flight.exceeds((int) maxTime)) && (level.isClientSide() && !isVirtual() || placeItemAtTarget(flight, maxTime))) {
                if (flight.getValue() == trackedItem) trackedItem = null;
                launchedItems.remove(index); setChanged();
            } else { flight.increment(); index++; }
        }
        if (state == State.LAUNCHING) {
            lidProgress.chase(1, .8f, Chaser.EXP); lidProgress.tickChaser();
            if (lidProgress.getValue() > 1 - 1 / 16f && (!level.isClientSide() || isVirtual())) { state = State.RETRACTING; lidProgress.setValue(1); }
        }
        if (state == State.CHARGED) {
            lidProgress.setValue(0); lidProgress.updateChaseSpeed(0);
            if (!level.isClientSide() || isVirtual()) ejectIfTriggered();
        }
        if (state == State.RETRACTING) {
            if (lidProgress.getChaseTarget() == 1 && !lidProgress.settled()) lidProgress.tickChaser();
            else {
                lidProgress.updateChaseTarget(0); lidProgress.updateChaseSpeed(0);
                if (lidProgress.getValue() == 0 && (!level.isClientSide() || isVirtual())) { state = State.CHARGED; lidProgress.setValue(0); sync(); }
                lidProgress.setValue(Mth.clamp(lidProgress.getValue() - getWindUpSpeed(), 0, 1));
                int rate = (int) (1 / (getWindUpSpeed() * 5)) + 1;
                if ((int) level.getGameTime() % rate == 0 && !level.isClientSide())
                    level.playSound(null, worldPosition, SoundEvents.WOODEN_BUTTON_CLICK_OFF, SoundSource.BLOCKS, .125f, 1.5f - lidProgress.getValue());
            }
        }
        if (!level.isClientSide()) { setChanged(); launches.entrySet().removeIf(entry -> entry.getValue().expires < level.getGameTime()); }
        if (state != previous) { setChanged(); sync(); }
    
        }
    }
    public void dropFlyingItems() {
        if (!depotBehaviour.isActiveWorldOwner()) return;
        dropOwnedFlyingItems();
    }
    private void dropOwnedFlyingItems() {
        if (isRemoved() || level == null || level.isClientSide()) return;
        for (int index = 0; index < launchedItems.size();) {
            var flight = launchedItems.get(index); var position = getLaunchedItemLocation(flight.getFirst());
            var item = new ItemEntity(level, position.x, position.y, position.z, flight.getValue().copy());
            item.setDeltaMovement(getLaunchedItemMotion(flight.getFirst())); item.setDefaultPickUpDelay();
            if (level.addFreshEntity(item)) launchedItems.remove(index); else index++;
        }
        trackedItem = null; setChanged();
    }
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState block) {
         dropOwnedFlyingItems(); super.preRemoveSideEffects(pos, block);
    }
    @Override public void invalidate() {  launches.clear(); super.invalidate(); }
    @Override protected void write(ValueOutput out, boolean clientPacket) {
        super.write(out, clientPacket); 
        out.putInt("HorizontalDistance", launcher.getHorizontalDistance()); out.putInt("VerticalDistance", launcher.getVerticalDistance());
        out.putBoolean("Powered", powered); out.putString("State", state.name()); out.putInt("MinimumStackSize", minimumStackSize);
        out.store("Lid", CompoundTag.CODEC, lidProgress.writeNBT()); out.store("LaunchedItems", FLIGHT_CODEC.listOf(), launchedItems.stream().filter(item -> !item.getValue().isEmpty()).toList());
        if (earlyTarget != null) {
            out.store("EarlyTarget", Vec3.CODEC, earlyTarget.getFirst()); out.store("EarlyTargetPos", BlockPos.CODEC, earlyTarget.getSecond());
            out.putFloat("EarlyTargetTime", earlyTargetTime);
        }
    }
    @Override protected void read(ValueInput in, boolean clientPacket) {
        super.read(in, clientPacket); 
        launcher.set(in.getIntOr("HorizontalDistance", 1), in.getIntOr("VerticalDistance", 0)); launcher.clamp(EjectorConfig.server().maxDistance());
        powered = in.getBooleanOr("Powered", false);
        try { state = State.valueOf(in.getStringOr("State", "CHARGED")); } catch (IllegalArgumentException ignored) { state = State.RETRACTING; }
        minimumStackSize = Math.clamp(in.getIntOr("MinimumStackSize", in.getIntOr("ScrollValue", 0)), 0, 64);
        var lid = in.read("Lid", CompoundTag.CODEC).orElseGet(CompoundTag::new);
        lidProgress.readNBT(lid, false); lidProgress.chase(lidProgress.getChaseTarget(), lid.getFloatOr("Speed", 0), Chaser.EXP);
        launchedItems.clear(); launchedItems.addAll(in.read("LaunchedItems", FLIGHT_CODEC.listOf()).orElse(List.of()));
        earlyTarget = null; earlyTargetTime = 0;
        var early = in.read("EarlyTarget", Vec3.CODEC); var earlyPos = in.read("EarlyTargetPos", BlockPos.CODEC);
        if(earlyPos.isEmpty())earlyPos=in.child("EarlyTargetPos").map(value->new BlockPos(value.getIntOr("X",0),value.getIntOr("Y",0),value.getIntOr("Z",0)));
        if (early.isPresent() && earlyPos.isPresent()) { earlyTarget = Pair.of(early.get(), earlyPos.get()); earlyTargetTime = in.getFloatOr("EarlyTargetTime", 0); }
        in.read("ForceAngle", Codec.FLOAT).ifPresent(value -> lidProgress.startWithValue(value));
        launch = false; trackedItem = null; scanCooldown = 0; placementOwner = null; launches.clear();
    }
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (depotBehaviour != null) {
            behaviours.add(depotBehaviour);
            behaviours.add(depotBehaviour.directInput);
            behaviours.add(depotBehaviour.transportedHandler);
        }
    }
}
