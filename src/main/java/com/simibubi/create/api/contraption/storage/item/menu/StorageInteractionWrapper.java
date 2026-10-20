package com.simibubi.create.api.contraption.storage.item.menu;

import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import java.util.*;
import java.util.function.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Vanilla menus mutate ItemStacks in place. Flush changed menu stacks back into transactional slots. */
public class StorageInteractionWrapper implements Container {
    private record Slot(MountedItemStorage storage, int index) {}
    private final List<Slot> slots = new ArrayList<>();
    private final ItemStack[] displayed, observed;
    private final Predicate<Player> stillValid;
    private final Consumer<Player> onClose;
    public StorageInteractionWrapper(List<MountedItemStorage> storages, Predicate<Player> stillValid, Consumer<Player> onClose) {
        this.stillValid = stillValid; this.onClose = onClose;
        for (var storage : storages) for (int i = 0; i < storage.getSlotCount(); i++) slots.add(new Slot(storage, i));
        displayed = new ItemStack[slots.size()]; observed = new ItemStack[slots.size()];
        Arrays.fill(displayed, ItemStack.EMPTY); Arrays.fill(observed, ItemStack.EMPTY);
    }
    private ItemStack refresh(int slot) {
        var binding = slots.get(slot); var current = binding.storage.getStackInSlot(binding.index);
        if (!ItemStack.matches(current, observed[slot])) { displayed[slot] = current.copy(); observed[slot] = current.copy(); }
        return displayed[slot];
    }
    @Override public int getContainerSize() { return slots.size(); }
    @Override public boolean isEmpty() { for (int i = 0; i < slots.size(); i++) if (!getItem(i).isEmpty()) return false; return true; }
    @Override public ItemStack getItem(int slot) { return refresh(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        if (amount <= 0) return ItemStack.EMPTY;
        var stack = getItem(slot); if (stack.isEmpty()) return ItemStack.EMPTY;
        var taken = stack.split(amount); setItem(slot, stack); return taken;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { var result = getItem(slot).copy(); setItem(slot, ItemStack.EMPTY); return result; }
    @Override public void setItem(int slot, ItemStack stack) {
        var binding = slots.get(slot); if (!binding.storage.isAvailable()) return;
        var copy = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(Math.min(stack.getCount(), stack.getMaxStackSize()));
        binding.storage.setStackInSlot(binding.index, copy); displayed[slot] = copy.copy(); observed[slot] = copy.copy();
    }
    @Override public void setChanged() {
        for (int i = 0; i < slots.size(); i++) if (!ItemStack.matches(displayed[i], observed[i])) setItem(i, displayed[i]);
    }
    @Override public boolean stillValid(Player player) { return slots.stream().allMatch(slot -> slot.storage.isAvailable()) && stillValid.test(player); }
    @Override public void stopOpen(net.minecraft.world.entity.ContainerUser user) { if (user instanceof Player player) onClose.accept(player); }
    @Override public void clearContent() { for (int i = 0; i < slots.size(); i++) setItem(i, ItemStack.EMPTY); }
    @Override public int getMaxStackSize() { return 64; }
}
