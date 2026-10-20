package com.simibubi.create.content.logistics.depot;

import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.item.ProcessingInventory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;

/** Native counterpart of source's nine-slot handler; slot zero is the sole insertion slot. */
public final class DepotItemHandler implements Storage<ItemVariant> {
    private final DepotBehaviour behaviour;
    private final ProcessingInventory inventory;
    DepotItemHandler(DepotBehaviour behaviour, ProcessingInventory inventory) {
        this.behaviour = behaviour; this.inventory = inventory;
    }
    public int getSlots() { return 9; }
    private ProcessingInventory.Slot slot(int slot) {
        if (slot < 0 || slot >= getSlots()) throw new IndexOutOfBoundsException(slot);
        return slot == 0 ? inventory.inputs.getFirst() : inventory.outputs.get(slot - 1);
    }
    public ItemStack getStackInSlot(int slot) { var actual=slot(slot);return behaviour.isActiveWorldOwner()?actual.stack():ItemStack.EMPTY; }
    public int getSlotLimit(int slot) { slot(slot); return slot == 0 ? behaviour.getConfiguredMaxStackSize() : 64; }
    public boolean isItemValid(int slot, ItemStack stack) { slot(slot); return slot == 0 && behaviour.isItemValid(stack); }
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        slot(slot); if (!behaviour.isActiveWorldOwner() || slot != 0 || stack.isEmpty()) return stack;
        return behaviour.insert(new TransportedItemStack(stack.copy(), 0), simulate);
    }
    public ItemStack extractItem(int index, int amount, boolean simulate) {
        if (amount < 0) throw new IllegalArgumentException("Negative extraction");
        var slot = slot(index); if (!behaviour.isActiveWorldOwner() || slot.isResourceBlank() || amount == 0) return ItemStack.EMPTY;
        var resource = slot.getResource();
        try (var tx = Transaction.openOuter()) {
            long extracted = slot.extract(resource, amount, tx); if (!simulate) tx.commit();
            return resource.toStack((int) extracted);
        }
    }
    @Override public long insert(ItemVariant resource, long amount, TransactionContext tx) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        return !behaviour.isActiveWorldOwner() || amount == 0 ? 0 : behaviour.insert(new TransportedItemStack(resource.toStack(), 0), amount, tx);
    }
    @Override public long extract(ItemVariant resource, long amount, TransactionContext tx) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        if(!behaviour.isActiveWorldOwner())return 0;
        long total = 0;
        for (var slot : inventory.outputs) total += slot.extract(resource, amount - total, tx);
        return total + inventory.inputs.getFirst().extract(resource, amount - total, tx);
    }
    @Override public boolean supportsInsertion(){return behaviour.isActiveWorldOwner();}
    @Override public boolean supportsExtraction(){return behaviour.isActiveWorldOwner();}
    @Override public Iterator<StorageView<ItemVariant>> iterator() {
        List<StorageView<ItemVariant>> views = new ArrayList<>();
        var slots=new ArrayList<>(inventory.inputs);slots.addAll(inventory.outputs);
        for(var slot:slots)views.add(new StorageView<>(){
            @Override public boolean isResourceBlank(){return !behaviour.isActiveWorldOwner()||slot.isResourceBlank();}
            @Override public ItemVariant getResource(){return behaviour.isActiveWorldOwner()?slot.getResource():ItemVariant.blank();}
            @Override public long getAmount(){return behaviour.isActiveWorldOwner()?slot.getAmount():0;}
            @Override public long getCapacity(){if(!behaviour.isActiveWorldOwner())return 0;int configured=behaviour.getConfiguredMaxStackSize();return slot==inventory.inputs.getFirst()?Math.min(slot.getCapacity(),configured==0?64:configured):slot.getCapacity();}
            @Override public long extract(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);return behaviour.isActiveWorldOwner()?slot.extract(resource,amount,tx):0;}
            @Override public StorageView<ItemVariant> getUnderlyingView(){return slot.getUnderlyingView();}
        });return views.iterator();
    }
}
