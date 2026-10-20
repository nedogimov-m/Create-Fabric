package com.simibubi.create.foundation.item;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.storage.base.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Isolated transactional slots, using Create's sparse {items,size} mounted inventory format. */
public final class MountedItemInventory extends CombinedStorage<ItemVariant, MountedItemInventory.Slot> implements SlottedStorage<ItemVariant> {
    private static final Codec<Integer> INDEX = Codec.STRING.comapFlatMap(value -> {
        try { int slot = Integer.parseInt(value); return slot >= 0 ? DataResult.success(slot) : DataResult.error(() -> "Negative slot"); }
        catch (NumberFormatException exception) { return DataResult.error(() -> "Invalid slot: " + value); }
    }, Object::toString);
    private record Contents(Map<Integer, ItemStack> items, int size) {}
    private static final Codec<Contents> CONTENTS = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(INDEX, ItemStack.CODEC).fieldOf("items").forGetter(Contents::items),
        Codec.intRange(0, 65536).fieldOf("size").forGetter(Contents::size)
    ).apply(i, Contents::new));
    public static final Codec<MountedItemInventory> CODEC = CONTENTS.comapFlatMap(data -> {
        if (data.items.keySet().stream().anyMatch(slot -> slot >= data.size)) return DataResult.error(() -> "Item outside mounted inventory");
        for (var stack : data.items.values()) if (stack.getCount() > stack.getMaxStackSize()) return DataResult.error(() -> "Oversized mounted item stack");
        var result = new MountedItemInventory(data.size);
        data.items.forEach(result::setStack);
        return DataResult.success(result);
    }, inventory -> new Contents(inventory.contents(), inventory.getSlotCount()));
    private long version;
    private Runnable changed = () -> {};

    public MountedItemInventory(int size) {
        super(new ArrayList<>());
        if (size < 0 || size > 65536) throw new IllegalArgumentException("Invalid mounted inventory size");
        for (int i = 0; i < size; i++) parts.add(new Slot());
        parts = List.copyOf(parts);
    }
    public void onChange(Runnable changed) { this.changed = Objects.requireNonNull(changed); }
    @Override public int getSlotCount() { return parts.size(); }
    @Override public Slot getSlot(int slot) { return parts.get(slot); }
    @Override public long getVersion() { return version; }
    public ItemStack getStack(int slot) { var value = getSlot(slot); return value.isResourceBlank() ? ItemStack.EMPTY : value.getResource().toStack((int)value.getAmount()); }
    public void setStack(int slot, ItemStack stack) {
        requireNoTransaction();
        if (!stack.isEmpty() && stack.getCount() > stack.getMaxStackSize()) throw new IllegalArgumentException("Oversized mounted item stack");
        var value = getSlot(slot); value.variant = ItemVariant.of(stack); value.amount = stack.isEmpty() ? 0 : stack.getCount();
        value.onFinalCommit();
    }
    public static void requireNoTransaction() {
        if (Transaction.getLifecycle() != Transaction.Lifecycle.NONE) throw new IllegalStateException("Cannot replace mounted contents during a transfer transaction");
    }
    public static MountedItemInventory copy(Container container) {
        var result = new MountedItemInventory(container.getContainerSize());
        for (int i = 0; i < result.getSlotCount(); i++) result.setStack(i, container.getItem(i));
        return result;
    }
    public static MountedItemInventory copy(SlottedStorage<ItemVariant> storage) {
        var result = new MountedItemInventory(storage.getSlotCount());
        for (int i = 0; i < result.getSlotCount(); i++) {
            var slot = storage.getSlot(i);
            if (slot.getAmount() > Integer.MAX_VALUE) throw new IllegalArgumentException("Mounted storage requires finite item slots");
            result.setStack(i, slot.isResourceBlank() ? ItemStack.EMPTY : slot.getResource().toStack((int)slot.getAmount()));
        }
        return result;
    }
    public static MountedItemInventory copy(List<? extends SingleSlotStorage<ItemVariant>> slots) {
        var result = new MountedItemInventory(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            result.setStack(i, slot.isResourceBlank() ? ItemStack.EMPTY : slot.getResource().toStack(Math.toIntExact(slot.getAmount())));
        }
        return result;
    }
    public void applyTo(List<? extends SingleSlotStorage<ItemVariant>> slots) {
        requireNoTransaction();
        if (slots.size() != getSlotCount()) throw new IllegalStateException("Mounted inventory size changed");
        try (var tx = Transaction.openOuter()) {
            for (int i = 0; i < slots.size(); i++) {
                var target = slots.get(i);
                long previousAmount = target.getAmount();
                if (!target.isResourceBlank() && target.extract(target.getResource(), previousAmount, tx) != previousAmount) throw new IllegalStateException("Cannot clear restored inventory slot");
                var source = getSlot(i);
                if (!source.isResourceBlank() && target.insert(source.getResource(), source.getAmount(), tx) != source.getAmount()) throw new IllegalStateException("Cannot restore mounted inventory slot");
            }
            tx.commit();
        }
    }
    private Map<Integer, ItemStack> contents() {
        var result = new LinkedHashMap<Integer, ItemStack>();
        for (int i = 0; i < getSlotCount(); i++) if (!getStack(i).isEmpty()) result.put(i, getStack(i));
        return result;
    }
    public final class Slot extends SingleVariantStorage<ItemVariant> {
        @Override protected ItemVariant getBlankVariant() { return ItemVariant.blank(); }
        @Override protected long getCapacity(ItemVariant item) { return item.isBlank() ? 64 : item.toStack().getMaxStackSize(); }
        @Override protected void onFinalCommit() { version++; changed.run(); }
    }
}
