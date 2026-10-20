package com.simibubi.create.content.trains.schedule;

import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Source two ghost slots backed by the actual selected schedule; samples never become real stock. */
public final class ScheduleMenu extends AbstractContainerMenu {
    public record Opening(ItemStack schedule, int ownerSlot) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Opening> CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Opening::schedule, ByteBufCodecs.VAR_INT, Opening::ownerSlot, Opening::new);
    }
    public static final ExtendedMenuType<ScheduleMenu, Opening> TYPE = Registry.register(
            BuiltInRegistries.MENU, Create.asResource("schedule"),
            new ExtendedMenuType<>((id, inventory, opening) -> new ScheduleMenu(id, inventory, opening.schedule, opening.ownerSlot), Opening.CODEC));
    private final Inventory inventory;
    private final ItemStack contentHolder;
    private final int ownerSlot;
    public final SimpleContainer ghostInventory = new SimpleContainer(2);
    public boolean slotsActive = true;
    public int targetSlotsActive = 1;

    public ScheduleMenu(int id, Inventory inventory, ItemStack schedule, int ownerSlot) {
        super(TYPE, id);
        if (!schedule.is(AllItems.SCHEDULE) || ownerSlot < 0 || ownerSlot > 8)
            throw new IllegalArgumentException("Actual selected native schedule required");
        this.inventory = inventory;
        this.contentHolder = schedule;
        this.ownerSlot = ownerSlot;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(playerSlot(inventory, col + row * 9 + 9, 46 + col * 18, 140 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(playerSlot(inventory, col, 46 + col * 18, 198));
        for (int i = 0; i < 2; i++) {
            final int target = i;
            addSlot(new Slot(ghostInventory, i, 54 + 20 * i, 88) {
                @Override public boolean isActive() { return slotsActive && target < targetSlotsActive; }
                @Override public boolean mayPlace(ItemStack item) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
                @Override public boolean isFake() { return true; }
            });
        }
    }
    private Slot playerSlot(Inventory inventory, int index, int x, int y) {
        return new Slot(inventory, index, x, y) {
            @Override public boolean isActive() { return slotsActive; }
        };
    }
    public ItemStack contentHolder() { return contentHolder; }
    @Override public boolean stillValid(Player player) {
        return player.getInventory() == inventory && inventory.getSelectedSlot() == ownerSlot
                && inventory.getSelectedItem() == contentHolder && contentHolder.is(AllItems.SCHEDULE);
    }
    @Override public void clicked(int index, int button, ContainerInput input, Player player) {
        if (!player.level().isClientSide() && !stillValid(player)) return;
        if (!slotsActive || index == ownerSlot + 27 || input == ContainerInput.SWAP && button == ownerSlot) return;
        if (index >= 36 && index < 38) {
            if (index - 36 >= targetSlotsActive) return;
            if (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) {
                ghostInventory.setItem(index - 36, getCarried().copyWithCount(1));
                broadcastChanges();
            }
            return;
        }
        super.clicked(index, button, input, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!slotsActive || index < 0 || index >= 38 || index == ownerSlot + 27) return ItemStack.EMPTY;
        if (index >= 36) {
            if (index - 36 < targetSlotsActive) ghostInventory.setItem(index - 36, ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        ItemStack sample = getSlot(index).getItem();
        if (sample.isEmpty()) return ItemStack.EMPTY;
        for (int i = 0; i < targetSlotsActive; i++) if (ghostInventory.getItem(i).isEmpty()) {
            ghostInventory.setItem(i, sample.copyWithCount(1));
            broadcastChanges();
            break;
        }
        return ItemStack.EMPTY;
    }
    @Override public boolean canTakeItemForPickAll(ItemStack item, Slot slot) {
        return slotsActive && slot.container == inventory && slot.getContainerSlot() != ownerSlot;
    }
    @Override public boolean canDragTo(Slot slot) {
        return slotsActive && slot.container == inventory && slot.getContainerSlot() != ownerSlot;
    }
    public static void register() { /* Initializes the actual native menu type. */ }
}
