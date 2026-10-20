package com.simibubi.create.content.redstone.link;

import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.*;

public final class LinkBehaviour extends BlockEntityBehaviour implements IRedstoneLinkable,com.simibubi.create.content.equipment.clipboard.ClipboardCloneable {
    @Override public String getClipboardKey(){return "Frequencies";}
    @Override public boolean writeToClipboard(net.minecraft.core.HolderLookup.Provider registries,net.minecraft.nbt.CompoundTag tag,net.minecraft.core.Direction side){var ops=registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);tag.store("First",ItemStack.OPTIONAL_CODEC,ops,first.getStack());tag.store("Last",ItemStack.OPTIONAL_CODEC,ops,last.getStack());return true;}
    @Override public boolean readFromClipboard(net.minecraft.core.HolderLookup.Provider registries,net.minecraft.nbt.CompoundTag tag,net.minecraft.world.entity.player.Player player,net.minecraft.core.Direction side,boolean simulate){var ops=registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);var first=tag.read("First",ItemStack.OPTIONAL_CODEC,ops);var last=tag.read("Last",ItemStack.OPTIONAL_CODEC,ops);if(first.isEmpty()||last.isEmpty())return false;if(!simulate){setFrequency(true,first.get());setFrequency(false,last.get());}return true;}
    public static final BehaviourType<LinkBehaviour> TYPE = new BehaviourType<>();
    private RedstoneLinkNetworkHandler.Frequency first = RedstoneLinkNetworkHandler.Frequency.EMPTY,
        last = RedstoneLinkNetworkHandler.Frequency.EMPTY;
    private final boolean listening;
    private final IntSupplier transmission;
    private final IntConsumer reception;
    private boolean registered;
    public boolean newPosition = true;

    private LinkBehaviour(BlockEntity owner, boolean listening, IntSupplier transmission, IntConsumer reception) {
        super(owner); this.listening = listening; this.transmission = transmission; this.reception = reception;
    }
    public static LinkBehaviour receiver(BlockEntity owner, IntConsumer signal) { return new LinkBehaviour(owner, true, () -> 0, signal); }
    public static LinkBehaviour transmitter(BlockEntity owner, IntSupplier signal) { return new LinkBehaviour(owner, false, signal, ignored -> {}); }
    @Override public BehaviourType<?> getType() { return TYPE; }
    @Override public boolean isSafeNBT() { return true; }
    @Override public boolean isListening() { return listening; }
    @Override public int getTransmittedStrength() { return listening ? 0 : Math.clamp(transmission.getAsInt(), 0, 15); }
    @Override public void setReceivedStrength(int power) { if (listening && newPosition) reception.accept(Math.clamp(power, 0, 15)); }
    @Override public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() { return Couple.create(first, last); }
    @Override public BlockPos getLocation() { return getPos(); }
    public boolean isRegistered() { return registered; }
    @Override public boolean isAlive() {
        var level = getWorld(); if (!(level instanceof ServerLevel) || blockEntity.isRemoved()) return false;
        var chunk = level.getChunkSource().getChunkNow(getPos().getX() >> 4, getPos().getZ() >> 4);
        return chunk != null && chunk.getFullStatus() != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
            && LoadedLevelAccess.blockEntity(level, getPos()) == blockEntity;
    }
    @Override public void initialize() { ensureNetworkRegistration(); }
    public void ensureNetworkRegistration() {
        if(registered&&!RedstoneLinkNetworkHandler.INSTANCE.isRegistered(getWorld(),this))registered=false;
        if (registered || !isAlive()) return;
        registered = true; newPosition = true; RedstoneLinkNetworkHandler.INSTANCE.addToNetwork(getWorld(), this);
    }
    @Override public void unload() {
        if (!registered) return;
        registered = false; RedstoneLinkNetworkHandler.INSTANCE.removeFromNetwork(getWorld(), this);
    }
    public void notifySignalChange() { if (registered) RedstoneLinkNetworkHandler.INSTANCE.updateNetworkOf(getWorld(), this); }
    public void copyItemsFrom(LinkBehaviour previous) { first = previous.first; last = previous.last; }
    public void setFrequency(boolean firstSlot, ItemStack stack) {
        var next = RedstoneLinkNetworkHandler.Frequency.of(stack);
        var old = firstSlot ? first : last;
        if (ItemStack.matches(old.getStack(), next.getStack())) return;
        boolean wasRegistered = registered; if (wasRegistered) unload();
        if (firstSlot) first = next; else last = next;
        changed(); if (wasRegistered) initialize();
    }
    @Override public void write(ValueOutput output) {
        output.store("FrequencyFirst", ItemStack.OPTIONAL_CODEC, first.getStack());
        output.store("FrequencyLast", ItemStack.OPTIONAL_CODEC, last.getStack());
        output.putLong("LastKnownPosition", getPos().asLong());
    }
    @Override public void read(ValueInput input) {
        newPosition = input.getLongOr("LastKnownPosition", Long.MIN_VALUE) != getPos().asLong();
        first = RedstoneLinkNetworkHandler.Frequency.of(input.read("FrequencyFirst", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        last = RedstoneLinkNetworkHandler.Frequency.of(input.read("FrequencyLast", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }
}
