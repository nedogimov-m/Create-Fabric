package com.simibubi.create.content.trains.signal;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.Objects;
import net.fabricmc.fabric.api.menu.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Real native signal editor lifetime, exact owner authority and actual server state snapshots. */
public final class SignalMenu extends AbstractContainerMenu {
    public record Opening(BlockPos pos, long revision, SignalBlock.SignalType type,
                          SignalBlockEntity.SignalState state, String status, GlobalPos target, boolean bound) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Opening> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeBlockPos(value.pos); buffer.writeVarLong(value.revision); buffer.writeEnum(value.type); buffer.writeEnum(value.state);
            buffer.writeUtf(value.status, 64); buffer.writeBoolean(value.target != null);
            if (value.target != null) GlobalPos.STREAM_CODEC.encode(buffer, value.target);
            buffer.writeBoolean(value.bound);
        }, buffer -> new Opening(buffer.readBlockPos(), buffer.readVarLong(), buffer.readEnum(SignalBlock.SignalType.class),
            buffer.readEnum(SignalBlockEntity.SignalState.class), buffer.readUtf(64), buffer.readBoolean() ? GlobalPos.STREAM_CODEC.decode(buffer) : null, buffer.readBoolean()));
        public Opening { pos = pos.immutable(); if (revision < 0) throw new IllegalArgumentException("Negative signal menu revision"); }
    }
    public static final ExtendedMenuType<SignalMenu, Opening> TYPE = Registry.register(BuiltInRegistries.MENU,
        Create.asResource("track_signal"), new ExtendedMenuType<>(SignalMenu::new, Opening.CODEC));
    private final Inventory inventory;
    private final SignalBlockEntity owner;
    private Opening opening;
    private int ticks;
    public SignalMenu(int id, Inventory inventory, Opening opening) {
        super(TYPE, id); this.inventory = inventory; this.opening = opening;
        owner = inventory.player.level().isClientSide() ? null
            : LoadedLevelAccess.blockEntity(inventory.player.level(), opening.pos()) instanceof SignalBlockEntity signal ? signal : null;
    }
    public Opening opening() { return opening; }
    public SignalBlockEntity owner() { return owner; }
    public void updateSnapshot(Opening next) { if (next.pos().equals(opening.pos())) opening = next; }
    public static boolean canEdit(Player player, BlockPos pos) {
        return !player.isSpectator() && player.mayBuild() && player.isWithinBlockInteractionRange(pos, 1)
            && player.level().mayInteract(player, pos) && LoadedLevelAccess.isLoaded(player.level(), pos);
    }
    @Override public boolean stillValid(Player player) {
        if (player.getInventory() != inventory || !canEdit(player, opening.pos())) return false;
        return player.level().isClientSide() ? LoadedLevelAccess.blockEntity(player.level(), opening.pos()) instanceof SignalBlockEntity
            : owner != null && !owner.isRemoved() && LoadedLevelAccess.blockEntity(player.level(), opening.pos()) == owner;
    }
    public static Opening snapshot(SignalBlockEntity signal) {
        var target = signal.targeting().selection(); var point = signal.getSignal();
        return new Opening(signal.getBlockPos(), signal.revision(), point == null ? signal.getBlockState().getValue(SignalBlock.TYPE) : point.getTypeFor(signal.owner()),
            signal.getState(), signal.targeting().status().name(), target == null ? null : target.target(), point != null);
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges(); if (owner == null || !(inventory.player instanceof ServerPlayer player) || !stillValid(player) || ++ticks < 10) return;
        ticks = 0; var next = snapshot(owner);
        if (!next.equals(opening)) { opening = next; ServerPlayNetworking.send(player, new SignalSnapshotPacket(containerId, next)); }
    }
    public static void open(Player player, SignalBlockEntity signal) {
        if (!(player instanceof ServerPlayer server)) return;
        var opening = snapshot(signal);
        server.openMenu(new ExtendedMenuProvider<Opening>() {
            @Override public Component getDisplayName() { return Component.translatable("block.create.track_signal"); }
            @Override public Opening getScreenOpeningData(ServerPlayer ignored) { return opening; }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player ignored) { return new SignalMenu(id, inventory, opening); }
        });
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    public static void register() { SignalConfigurationPacket.register(); SignalSnapshotPacket.register(); }
}
