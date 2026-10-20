package com.simibubi.create.content.kinetics.crusher;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.*;
import java.util.*;

/** A single batch input; outputs cannot be extracted until the crusher ejects them. */
public final class CrushingInventory extends SnapshotParticipant<CrushingInventory.Snapshot> implements Storage<ItemVariant> {
    private final CrushingWheelControllerBlockEntity owner;
    private ItemStack input = ItemStack.EMPTY;
    private final List<ItemStack> outputs = new ArrayList<>();
    public float remainingTime;
    public boolean appliedRecipe;
    record Snapshot(ItemStack input, List<ItemStack> outputs, float remainingTime, boolean appliedRecipe) {}
    CrushingInventory(CrushingWheelControllerBlockEntity owner) { this.owner = owner; }
    public ItemStack getInput() { return input.copy(); }
    public List<ItemStack> getOutputs() { return outputs.stream().map(ItemStack::copy).toList(); }
    public boolean isEmpty() { return input.isEmpty() && outputs.isEmpty(); }
    @Override protected Snapshot createSnapshot() { return new Snapshot(input.copy(), getOutputs(), remainingTime, appliedRecipe); }
    @Override protected void readSnapshot(Snapshot state) { input = state.input.copy(); outputs.clear(); state.outputs.forEach(s -> outputs.add(s.copy())); remainingTime = state.remainingTime; appliedRecipe = state.appliedRecipe; }
    @Override protected void onFinalCommit() { owner.contentsChanged(); }
    @Override public long insert(ItemVariant resource, long amount, TransactionContext tx) { return insert(resource, amount, tx, false); }
    long insert(ItemVariant resource, long amount, TransactionContext tx, boolean entityIntake) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        if (amount == 0 || !isEmpty() || !entityIntake && owner.hasEntity()) return 0;
        int accepted = (int) Math.min(amount, resource.toStack().getMaxStackSize());
        updateSnapshots(tx); input = resource.toStack(accepted); appliedRecipe = false; remainingTime = owner.duration(input);
        return accepted;
    }
    @Override public long extract(ItemVariant resource, long amount, TransactionContext tx) { StoragePreconditions.notBlankNotNegative(resource, amount); return 0; }
    @Override public Iterator<StorageView<ItemVariant>> iterator() {
        var views = new ArrayList<StorageView<ItemVariant>>();
        for (int slot = -1; slot < outputs.size(); slot++) {
            final int index = slot;
            views.add(new StorageView<>() {
                private ItemStack current() { return index < 0 ? input : index < outputs.size() ? outputs.get(index) : ItemStack.EMPTY; }
                @Override public boolean isResourceBlank() { return current().isEmpty(); }
                @Override public ItemVariant getResource() { return ItemVariant.of(current()); }
                @Override public long getAmount() { return current().getCount(); }
                @Override public long getCapacity() { return current().isEmpty() ? 64 : current().getMaxStackSize(); }
                @Override public long extract(ItemVariant resource, long amount, TransactionContext tx) { StoragePreconditions.notBlankNotNegative(resource, amount); return 0; }
            });
        }
        return views.iterator();
    }
    void apply(List<ItemStack> results) { input = ItemStack.EMPTY; outputs.clear(); results.forEach(s -> outputs.add(s.copy())); appliedRecipe = true; owner.contentsChanged(); }
    boolean exportTo(Storage<ItemVariant> target) {
        boolean changed = false;
        try (Transaction tx = Transaction.openOuter()) {
            for (var stack : outputs) {
                long moved = target.insert(ItemVariant.of(stack), stack.getCount(), tx);
                if (moved == 0) continue;
                updateSnapshots(tx); stack.shrink((int) moved); changed = true;
            }
            if (changed) { outputs.removeIf(ItemStack::isEmpty); tx.commit(); }
        }
        return changed;
    }
    public void clear() { input = ItemStack.EMPTY; outputs.clear(); appliedRecipe = false; remainingTime = 0; owner.contentsChanged(); }
    public void save(ValueOutput out) {
        if (!input.isEmpty()) out.store("Input", ItemStack.CODEC, input);
        out.store("Outputs", ItemStack.CODEC.listOf(), outputs); out.putFloat("ProcessingTime", remainingTime); out.putBoolean("AppliedRecipe", appliedRecipe);
    }
    public void load(ValueInput in) {
        input = in.read("Input", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        outputs.clear(); outputs.addAll(in.read("Outputs", ItemStack.CODEC.listOf()).orElse(List.of()));
        float time = in.getFloatOr("ProcessingTime", 0); remainingTime = Float.isFinite(time) ? Math.clamp(time, 0, 1000000) : 0;
        appliedRecipe = in.getBooleanOr("AppliedRecipe", false);
    }
}
