package com.simibubi.create.content.contraptions.actors.roller;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.shapes.Shapes;

public class RollerBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements BlockEntityBehaviour.Provider {
    public enum RollingMode {
        TUNNEL_PAVE, STRAIGHT_FILL, WIDE_FILL;
        public Component label() { return Component.translatable("create.contraptions.roller_mode." + name().toLowerCase(java.util.Locale.ROOT)); }
    }
    public final FilteringBehaviour filtering = new FilteringBehaviour(this).withPredicate(this::isValidMaterial).withCallback(stack -> shareValuesToAdjacent()).setLabel(Component.translatable("create.contraptions.mechanical_roller.pave_material"));
    private RollingMode mode = RollingMode.TUNNEL_PAVE;
    private boolean dontPropagate;
    private float manuallyAnimatedSpeed;
    public RollerBlockEntity(BlockPos pos, BlockState state) { this(AllBlockEntityTypes.MECHANICAL_ROLLER, pos, state); }
    public RollerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public RollingMode getMode() { return mode; }
    public void setMode(RollingMode mode) { this.mode = java.util.Objects.requireNonNull(mode); shareValuesToAdjacent(); notifyUpdate(); }
    public float getAnimatedSpeed() { return manuallyAnimatedSpeed; }
    public void setAnimatedSpeed(float speed) { manuallyAnimatedSpeed = Float.isFinite(speed) ? speed : 0; }
    public boolean isValidMaterial(ItemStack stack) {
        if (stack.isEmpty()) return true;
        var state = RollerMovementBehaviour.getStateToPaveWith(stack);
        if (state.isAir() || state.getBlock() instanceof EntityBlock || state.getBlock() instanceof StairBlock) return false;
        var shape = state.getShape(level, worldPosition);
        return !shape.isEmpty() && shape.bounds().equals(Shapes.block().bounds()) && !state.getCollisionShape(level, worldPosition).isEmpty();
    }
    public void searchForSharedValues() {
        if (level == null || level.isClientSide()) return;
        var state = getBlockState(); var side = state.getValue(RollerBlock.FACING).getClockWise();
        for (int sign : new int[]{1, -1}) {
            var pos = worldPosition.relative(side, sign);
            if (LoadedLevelAccess.blockState(level, pos) == state && LoadedLevelAccess.blockEntity(level, pos) instanceof RollerBlockEntity other) {
                acceptSharedValues(other.mode, other.filtering.getFilter()); shareValuesToAdjacent(); break;
            }
        }
    }
    protected void acceptSharedValues(RollingMode mode, ItemStack filter) {
        dontPropagate = true;
        try { filtering.setFilter(filter.copy()); this.mode = mode; } finally { dontPropagate = false; }
        notifyUpdate();
    }
    public void shareValuesToAdjacent() {
        if (dontPropagate || level == null || level.isClientSide()) return;
        var state = getBlockState(); var side = state.getValue(RollerBlock.FACING).getClockWise();
        for (int sign : new int[]{1, -1}) for (int i = 1; i < 100; i++) {
            var pos = worldPosition.relative(side, i * sign);
            if (LoadedLevelAccess.blockState(level, pos) != state || !(LoadedLevelAccess.blockEntity(level, pos) instanceof RollerBlockEntity other)) break;
            other.acceptSharedValues(mode, filtering.getFilter());
        }
    }
    public void notifyUpdate() {
        setChanged(); if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override protected void write(ValueOutput out, boolean clientPacket) { super.write(out, clientPacket);  out.putInt("ScrollValue", mode.ordinal()); }
    @Override protected void read(ValueInput in, boolean clientPacket) { super.read(in, clientPacket);  mode = RollingMode.values()[Math.clamp(in.getIntOr("ScrollValue", 0), 0, 2)]; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var out = TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, registries); write(out, true); return out.buildResult();
    }
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (filtering != null) behaviours.add(filtering);
    }
}
