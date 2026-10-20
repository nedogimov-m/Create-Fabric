package com.simibubi.create.content.kinetics.belt;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

public final class BeltBlockEntity extends KineticBlockEntity {
    private BlockPos controller;
    public int beltLength;
    public int index;
    private DyeColor color;
    public enum CasingType { NONE, ANDESITE, BRASS }
    private CasingType casing = CasingType.NONE;
    private final BeltInventory inventory = new BeltInventory(this);
    private long passengerTick = Long.MIN_VALUE;
    private final java.util.Set<java.util.UUID> movedPassengers = new java.util.HashSet<>();
    public BeltBlockEntity(BlockPos pos, BlockState state) { super(AllBlockEntityTypes.BELT, pos, state); }
    // Source belts discover their real sloped chain neighbors in the shaft plane.
    @Override protected boolean canPropagateDiagonally(com.simibubi.create.content.kinetics.base.IRotate block, BlockState state) {
        return state.hasProperty(BeltBlock.SLOPE) && (state.getValue(BeltBlock.SLOPE) == BeltSlope.UPWARD
            || state.getValue(BeltBlock.SLOPE) == BeltSlope.DOWNWARD);
    }
    @Override public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff,
            boolean connectedViaAxes, boolean connectedViaCogs) {
        if (target instanceof BeltBlockEntity belt && !connectedViaAxes)
            return getController().equals(belt.getController()) ? 1 : 0;
        return 0;
    }
    public void configure(BlockPos controller, int index, int length) {
        if (controller.equals(this.controller) && this.index == index && beltLength == length) return;
        if (isController() && !controller.equals(worldPosition)) inventory.ejectAll();
        this.controller = controller.immutable(); this.index = index; beltLength = length; contentsChanged();
    }
    /** Source Ponder restore reconnects the actual local belt controller without moving cargo. */
    public void setController(BlockPos controller) { this.controller = controller; }
    public BlockPos getController() { return controller == null ? worldPosition : controller; }
    public boolean isController() { return controller != null && controller.equals(worldPosition); }
    public BeltBlockEntity getControllerBE() { return level == null ? null : BeltHelper.getSegmentBE(level, getController()); }
    public BeltInventory getInventory() { return inventory; }
    public Direction getBeltFacing() { return getBlockState().getValue(BeltBlock.HORIZONTAL_FACING); }
    public Direction getMovementFacing(){var axis=getBeltFacing().getAxis();return Direction.get(getBeltMovementSpeed()<0^axis==Direction.Axis.X?Direction.AxisDirection.NEGATIVE:Direction.AxisDirection.POSITIVE,axis);}
    public boolean hasPulley() { return getBlockState().getValue(BeltBlock.PART) != BeltPart.MIDDLE; }
    public DyeColor getColor() { return color; }
    public CasingType getCasingType() { return getBlockState().getValue(BeltBlock.CASING) ? casing == CasingType.NONE ? CasingType.ANDESITE : casing : CasingType.NONE; }
    public void setCasingType(CasingType type) {
        if (level == null || level.isClientSide()) return;
        casing = type;
        level.setBlockAndUpdate(worldPosition, getBlockState().setValue(BeltBlock.CASING, type != CasingType.NONE));
        contentsChanged();
    }
    public boolean applyColor(DyeColor dye) {
        var master = getControllerBE();
        if (master == null || !master.chainLoaded()) return false;
        var chain = BeltBlock.getBeltChain(level, master.getBlockPos());
        if (chain.stream().allMatch(pos -> BeltHelper.getSegmentBE(level, pos).color == dye)) return false;
        if (!level.isClientSide()) for (var pos : chain) {
            var segment = BeltHelper.getSegmentBE(level, pos);
            if (segment != null) { segment.color = dye; segment.contentsChanged(); }
        }
        return true;
    }
    public float getBeltMovementSpeed() { return getSpeed() / 480f; }
    public float getDirectionAwareBeltMovementSpeed() { return getBeltMovementSpeed() * getBeltFacing().getAxisDirection().getStep() * (getBeltFacing().getAxis() == Direction.Axis.X ? -1 : 1); }
    public boolean chainLoaded() {
        if (level == null || !isController() || beltLength < 2 || getBlockState().getValue(BeltBlock.PART) != BeltPart.START) return false;
        var chain = BeltBlock.getBeltChain(level, worldPosition);
        if (chain.size() != beltLength || level.getBlockState(chain.getLast()).getValue(BeltBlock.PART) != BeltPart.END) return false;
        for (int i = 0; i < beltLength; i++) {
            var location = chain.get(i);
            var segment = BeltHelper.getSegmentBE(level, location);
            if (segment == null || !segment.getController().equals(worldPosition) || segment.index != i || segment.beltLength != beltLength) return false;
        }
        return true;
    }
    public Storage<ItemVariant> storage(Direction side) {
        var master = getControllerBE();
        return master == null || !BeltBlock.canTransportObjects(getBlockState()) ? null : master.inventory.segment(index, side);
    }
    public void contentsChanged() { setChanged(); if (level instanceof ServerLevel) sync(); }
    public void collectItems(Player player) {
        var storage = storage(Direction.UP); if (storage == null) return;
        for (var view : storage) {
            var resource = view.getResource(); long amount;
            try (Transaction transaction = Transaction.openOuter()) { amount = view.extract(resource, view.getAmount(), transaction); transaction.commit(); }
            if (amount > 0) player.getInventory().placeItemBackInInventory(resource.toStack((int) amount), net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }
    private void transportEntities() {
        if (level == null || getSpeed() == 0 || !BeltBlock.canTransportObjects(getBlockState())) return;
        var area = new AABB(worldPosition).expandTowards(0, 1, 0);
        var master = getControllerBE(); if (master == null || !master.chainLoaded()) return;
        if (master.passengerTick != level.getGameTime()) { master.passengerTick = level.getGameTime(); master.movedPassengers.clear(); }
        var state = getBlockState(); float movement = getDirectionAwareBeltMovementSpeed();
        for (Entity entity : level.getEntities(null, area)) {
            if (!entity.isAlive()||com.simibubi.create.content.equipment.armor.DivingBootsItem.isWornBy(entity)) continue;
            var facing = getBeltFacing();
            double offset = entity.position().subtract(Vec3.atCenterOf(master.getBlockPos()))
                    .dot(new Vec3(facing.getStepX(), 0, facing.getStepZ())) + 0.5;
            if (offset < 0 || offset >= master.beltLength || (int) offset != index) continue;
            double surface = BeltHelper.getVectorForOffset(master, (float) offset).y + 5f / 16;
            if (entity.getY() < surface - 0.15 || entity.getY() > surface + 0.9) continue;
            if (entity instanceof ItemEntity || entity instanceof com.simibubi.create.content.logistics.box.PackageEntity) {
                if (level.isClientSide() || entity.getDeltaMovement().y > 0.01) continue;
                var stack=entity instanceof ItemEntity item?item.getItem():((com.simibubi.create.content.logistics.box.PackageEntity)entity).getBox();
                if(!com.simibubi.create.content.logistics.box.PackageEntity.centerPackage(entity,BeltHelper.getVectorForOffset(master,(float)offset).add(0,5f/16,0)))continue;
                if(entity instanceof com.simibubi.create.content.logistics.box.PackageEntity&&com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,worldPosition.above()) instanceof com.simibubi.create.content.logistics.tunnel.BeltTunnelBlockEntity)continue;
                var storage = storage(Direction.UP); if (storage == null) continue;
                try (Transaction transaction = Transaction.openOuter()) {
                    long inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), transaction);
                    if (inserted == 0) continue;
                    transaction.commit();
                    var remaining = stack.copy(); remaining.shrink((int) inserted);
                    if (remaining.isEmpty()) entity.discard(); else if(entity instanceof ItemEntity item)item.setItem(remaining);
                }
            } else {
                if (entity instanceof Player player && (player.isShiftKeyDown()&&!com.simibubi.create.content.equipment.armor.CardboardArmorHandler.testForStealth(player) || player.getAbilities().flying || player.isSpectator())) continue;
                var slope = state.getValue(BeltBlock.SLOPE);
                boolean onSlope = slope.isDiagonal() && offset >= 0.5 && offset <= master.beltLength - 0.5;
                double dy = !onSlope ? 0 : slope == BeltSlope.UPWARD ? movement : -movement;
                double center = facing.getAxis() == Direction.Axis.X ? worldPosition.getZ() + 0.5 - entity.getZ() : worldPosition.getX() + 0.5 - entity.getX();
                if (Math.abs(center) > 0.75) continue;
                if (!master.movedPassengers.add(entity.getUUID())) continue;
                double correction = center * Math.min(Math.abs(movement), 0.1) * 4;
                var delta = new Vec3(facing.getStepX() * movement + (facing.getAxis() == Direction.Axis.Z ? correction : 0), dy,
                        facing.getStepZ() * movement + (facing.getAxis() == Direction.Axis.X ? correction : 0));
                LivingEntity living = entity instanceof LivingEntity mob && !(entity instanceof Player) ? mob : null;
                double step = living == null ? 0 : living.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT);
                if (living != null) {
                    living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue(1);
                    living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 10, 1, false, false));
                }
                try { entity.move(MoverType.SELF, delta); }
                finally { if (living != null) living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue(step); }
                entity.setOnGround(true);
                entity.resetFallDistance();
                if (entity instanceof net.minecraft.server.level.ServerPlayer player) player.connection.resetFlyingTicks();
            }
        }
    }
    public static void tick(Level level, BlockPos pos, BlockState state, BeltBlockEntity belt) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, belt)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level, pos, state, belt);
        if (belt.beltLength == 0 && !level.isClientSide() && level.getGameTime() % 5 == 0) BeltBlock.initBelt(level, pos);
        if (belt.isController()) belt.inventory.tick();
        belt.transportEntities();
    
        }
    }
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (isController()) inventory.ejectAll();
        super.preRemoveSideEffects(pos, state);
    }
    @Override protected void write(ValueOutput output, boolean clientPacket) {
        super.write(output, clientPacket);
        if (controller != null) output.store("Controller", BlockPos.CODEC, controller);
        output.putBoolean("IsController", isController()); output.putInt("Length", beltLength); output.putInt("Index", index);
        if (color != null) output.store("Dye", DyeColor.CODEC, color);
        output.putString("Casing", getCasingType().name());
        if (isController()) inventory.save(output.child("Inventory"));
    }
    @Override protected void read(ValueInput input, boolean clientPacket) {
        super.read(input, clientPacket);
        controller = input.read("Controller", BlockPos.CODEC).orElse(null);
        if (input.getBooleanOr("IsController", false)) controller = worldPosition;
        beltLength = input.getIntOr("Length", 0); index = input.getIntOr("Index", 0);
        if (beltLength < 0 || beltLength == 1 || index < 0 || isController() && index != 0 || beltLength == 0 && index != 0 || beltLength > 0 && index >= beltLength)
            throw new IllegalArgumentException("Invalid persisted belt length/index");
        color = input.read("Dye", DyeColor.CODEC).orElse(null);
        try { casing = CasingType.valueOf(input.getStringOr("Casing", "NONE").toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { casing = CasingType.NONE; }
        if (isController()) inventory.load(input.childOrEmpty("Inventory"));
    }
    private final com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour directBeltInput = com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.createNative(this);
    private final com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour transportedItemHandler = com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.createNative(this);
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (directBeltInput != null) behaviours.add(directBeltInput);
        if (transportedItemHandler != null) behaviours.add(transportedItemHandler);
    }
}
