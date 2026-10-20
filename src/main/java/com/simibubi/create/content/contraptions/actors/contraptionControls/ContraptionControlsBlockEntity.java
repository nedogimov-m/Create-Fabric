package com.simibubi.create.content.contraptions.actors.contraptionControls;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** Source toggle/filter persistence with native BE packets and tick-owned button animation. */
public class ContraptionControlsBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements BlockEntityBehaviour.Provider, Clearable {
    public final FilteringBehaviour filtering = new FilteringBehaviour(this)
        .withPredicate(stack -> stack.is(ContraptionControlsBlock.CONTROLLED_ITEMS))
        .setLabel(Component.translatable("create.contraptions.contoller.target"));
    public boolean disabled, powered,elevatorMode,elevatorFloorUnreachable;
    public String elevatorFloorLabel="";
    private float elevatorIndicator;
    public void setElevatorIndicator(float angle){elevatorIndicator=angle;}
    private float button, previousButton;
    private long buttonSequence, lastSeenSequence;
    private boolean initialized, hasButtonSequence;
    private long animationTick = Long.MIN_VALUE;

    public ContraptionControlsBlockEntity(BlockPos pos, BlockState state) { this(AllBlockEntityTypes.CONTRAPTION_CONTROLS, pos, state); }
    public ContraptionControlsBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public void pressButton() { previousButton = button = 1; }
    /** Persisted sequence is a visual event; a newly tracking client does not replay old clicks. */
    public void pressButtonAndSync() { pressButton(); buttonSequence++; notifyUpdate(); }
    public float getButton(float partial) { return Mth.lerp(partial, previousButton, button); }
    public float getIndicator() { return elevatorMode?elevatorIndicator:disabled ? 180 : 0; }
    public void tickAnimations() {
        if (level == null || !level.isClientSide() || animationTick == level.getGameTime()) return;
        animationTick = level.getGameTime(); previousButton = button; button *= .875f;
        if (button < 1 / 1024f) button = 0;
    }
    public void updatePoweredState() {
        if (level == null || level.isClientSide()) return;
        boolean next = new com.simibubi.create.foundation.utility.LoadedLevelReader(level).hasNeighborSignal(worldPosition);
        if (powered == next) return;
        powered = next; disabled = next; notifyUpdate();
    }
    public static void tick(Level level, BlockPos pos, BlockState state, ContraptionControlsBlockEntity controls) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, controls)) {
            if (!createTick.shouldTick()) return;

        if (level.isClientSide()) controls.tickAnimations();
        else if (!controls.initialized) { controls.initialized = true; controls.updatePoweredState(); }
    
        }
    }
    public void notifyUpdate() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override public void clearContent() { filtering.setFilter(ItemStack.EMPTY); }
    @Override protected void write(ValueOutput out, boolean clientPacket) {
        super.write(out, clientPacket);  out.putBoolean("Disabled", disabled); out.putBoolean("Powered", powered); out.putLong("ButtonSequence", buttonSequence);
    }
    @Override protected void read(ValueInput in, boolean clientPacket) {
        super.read(in, clientPacket);  disabled = in.getBooleanOr("Disabled", false); powered = in.getBooleanOr("Powered", false);
        long sequence = in.getLongOr("ButtonSequence", 0);
        if (hasButtonSequence && level != null && level.isClientSide() && sequence != lastSeenSequence) pressButton();
        buttonSequence = lastSeenSequence = sequence; hasButtonSequence = true;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return super.getUpdateTag(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    public static void sendStatus(Player player, ItemStack filter, boolean enabled) {
        var state = Component.translatable("create.contraption.controls.actor_toggle." + (enabled ? "on" : "off")).withColor(enabled ? 0x80c71f : 0xf9801d);
        player.sendOverlayMessage(filter.isEmpty()
            ? Component.translatable("create.contraption.controls.all_actor_toggle", state)
            : Component.translatable("create.contraption.controls.specific_actor_toggle", filter.getHoverName().getString(), state));
    }
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (filtering != null) behaviours.add(filtering);
    }
}
