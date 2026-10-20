package com.simibubi.create.foundation.item;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/** Transactional item slots shared by processing machines and their Fabric capability. */
public final class ProcessingInventory {
    public final List<Slot> inputs;
    public final List<Slot> outputs;
    public final Storage<ItemVariant> automation;

    public ProcessingInventory(int inputSlots, int outputSlots, Predicate<ItemVariant> accepts, Runnable changed) {
        this(inputSlots, outputSlots, accepts, changed, false);
    }

    public ProcessingInventory(int inputSlots, int outputSlots, Predicate<ItemVariant> accepts, Runnable changed, boolean extractInput) {
        this(inputSlots, outputSlots, accepts, changed, extractInput, false);
    }

    public ProcessingInventory(int inputSlots, int outputSlots, Predicate<ItemVariant> accepts, Runnable changed, boolean extractInput, boolean uniqueInputs) {
        this(inputSlots, outputSlots, accepts, changed, extractInput, uniqueInputs, null);
    }

    /** Owner-specific automation policy over the same canonical transactional slots. */
    public ProcessingInventory(int inputSlots, int outputSlots, Predicate<ItemVariant> accepts, Runnable changed,
                               boolean extractInput, boolean uniqueInputs,
                               java.util.function.Function<ProcessingInventory, Storage<ItemVariant>> automationFactory) {
        inputs = slots(inputSlots, changed);
        outputs = slots(outputSlots, changed);
        if (automationFactory != null) {
            automation = java.util.Objects.requireNonNull(automationFactory.apply(this));
            return;
        }
        var input = new CombinedStorage<ItemVariant, Slot>(inputs);
        var output = new CombinedStorage<ItemVariant, Slot>(outputs);
        automation = new Storage<>() {
            @Override
            public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
                net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions.notBlankNotNegative(resource, maxAmount);
                if (uniqueInputs && accepts.test(resource)) {
                    for (var slot : inputs) if (slot.getResource().equals(resource)) return slot.insert(resource, maxAmount, transaction);
                    for (var slot : inputs) if (slot.isResourceBlank()) return slot.insert(resource, maxAmount, transaction);
                    return 0;
                }
                return accepts.test(resource) ? input.insert(resource, maxAmount, transaction) : 0;
            }
            @Override
            public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
                long extracted = output.extract(resource, maxAmount, transaction);
                return extracted + (extractInput ? input.extract(resource, maxAmount - extracted, transaction) : 0);
            }
            @Override
            public Iterator<StorageView<ItemVariant>> iterator() {
                if (!extractInput) return output.iterator();
                List<Slot> all = new ArrayList<>(inputs);
                all.addAll(outputs);
                return new CombinedStorage<ItemVariant, Slot>(all).iterator();
            }
        };
    }

    private static List<Slot> slots(int count, Runnable changed) {
        var slots = new ArrayList<Slot>();
        for (int i = 0; i < count; i++) slots.add(new Slot(changed));
        return List.copyOf(slots);
    }

    public boolean insertOutput(ItemStack stack, TransactionContext transaction) {
        if (stack.isEmpty()) return true;
        long inserted = new CombinedStorage<ItemVariant, Slot>(outputs).insert(ItemVariant.of(stack), stack.getCount(), transaction);
        return inserted == stack.getCount();
    }

    public void save(ValueOutput output) {
        for (int i = 0; i < inputs.size(); i++) inputs.get(i).save(output.child("Input" + i));
        for (int i = 0; i < outputs.size(); i++) outputs.get(i).save(output.child("Output" + i));
    }

    public void load(ValueInput input) {
        for (int i = 0; i < inputs.size(); i++) inputs.get(i).load(input.childOrEmpty("Input" + i));
        for (int i = 0; i < outputs.size(); i++) outputs.get(i).load(input.childOrEmpty("Output" + i));
    }

    public static final class Slot extends SingleVariantStorage<ItemVariant> {
        private final Runnable changed;

        Slot(Runnable changed) { this.changed = changed; }
        @Override protected ItemVariant getBlankVariant() { return ItemVariant.blank(); }
        @Override protected long getCapacity(ItemVariant variant) { return variant.isBlank() ? 64 : variant.toStack().getMaxStackSize(); }
        @Override protected void onFinalCommit() { changed.run(); }
        public ItemStack stack() { return isResourceBlank() ? ItemStack.EMPTY : variant.toStack((int) amount); }
        /** Explicit machine-owned replacement, also retaining legitimate source overstacked held items. */
        public void setStack(ItemStack stack, TransactionContext transaction) {
            updateSnapshots(transaction);
            variant = stack.isEmpty() ? ItemVariant.blank() : ItemVariant.of(stack);
            amount = stack.isEmpty() ? 0 : stack.getCount();
        }
        void save(ValueOutput output) { SingleVariantStorage.writeValue(this, ItemVariant.CODEC, output); }
        void load(ValueInput input) { SingleVariantStorage.readValue(this, ItemVariant.CODEC, ItemVariant::blank, input); }
    }
}
