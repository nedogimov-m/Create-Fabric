package com.simibubi.create.content.kinetics.saw;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.*;
import java.util.*;

/** Original single-item processing slot and retained output batch, with transactional hand-off. */
public final class SawInventory extends SnapshotParticipant<SawInventory.Snapshot> implements Storage<ItemVariant> {
    private final SawBlockEntity owner;
    private ItemStack input = ItemStack.EMPTY;
    private List<ItemStack> outputs = new ArrayList<>();
    public float remainingTime = -1, recipeDuration;
    public boolean appliedRecipe;
    public int recipeIndex;
    private Identifier recipe;
    private boolean sourceRecipeIndex;
    record Snapshot(ItemStack input, List<ItemStack> outputs, float remaining, float duration, boolean applied, int index, Identifier recipe, boolean sourceRecipeIndex) {}
    SawInventory(SawBlockEntity owner) { this.owner = owner; }
    public ItemStack input() { return input.copy(); }
    public List<ItemStack> outputs() { return outputs.stream().map(ItemStack::copy).toList(); }
    public Identifier selectedRecipe() { return recipe; }
    public boolean isEmpty() { return input.isEmpty() && outputs.isEmpty(); }
    @Override protected Snapshot createSnapshot() { return new Snapshot(input.copy(), outputs(), remainingTime, recipeDuration, appliedRecipe, recipeIndex, recipe, sourceRecipeIndex); }
    @Override protected void readSnapshot(Snapshot s) { input = s.input.copy(); outputs = new ArrayList<>(s.outputs.stream().map(ItemStack::copy).toList()); remainingTime = s.remaining; recipeDuration = s.duration; appliedRecipe = s.applied; recipeIndex = s.index; recipe = s.recipe; sourceRecipeIndex = s.sourceRecipeIndex; }
    @Override protected void onFinalCommit() { owner.contentsChanged(); }
    @Override public long insert(ItemVariant resource, long amount, TransactionContext tx) {
        StoragePreconditions.notBlankNotNegative(resource, amount);
        if (amount == 0 || !owner.canProcess() || !isEmpty()) return 0;
        long accepted=Math.min(amount,com.simibubi.create.infrastructure.config.AllConfigs.server().recipes.bulkCutting.get()?resource.toStack().getMaxStackSize():1);
        updateSnapshots(tx); input = resource.toStack((int)accepted); appliedRecipe = false;
        var selected = owner.selectRecipe(input, recipeIndex + 1); recipeIndex = selected.index(); recipe = selected.id();
        sourceRecipeIndex = false;
        remainingTime = recipeDuration = selected.duration() * Math.max(1, input.getCount() / 5); return accepted;
    }
    @Override public long extract(ItemVariant resource, long amount, TransactionContext tx) { StoragePreconditions.notBlankNotNegative(resource, amount); return 0; }
    @Override public Iterator<StorageView<ItemVariant>> iterator() {
        var views = new ArrayList<StorageView<ItemVariant>>();
        for (int slot = -1; slot < outputs.size(); slot++) { int index = slot;
            views.add(new StorageView<>() {
                private ItemStack current() { return index < 0 ? input : index < outputs.size() ? outputs.get(index) : ItemStack.EMPTY; }
                @Override public boolean isResourceBlank() { return current().isEmpty(); }
                @Override public ItemVariant getResource() { return ItemVariant.of(current()); }
                @Override public long getAmount() { return current().getCount(); }
                @Override public long getCapacity() { return current().isEmpty() ? 1 : current().getMaxStackSize(); }
                @Override public long extract(ItemVariant resource, long amount, TransactionContext tx) { StoragePreconditions.notBlankNotNegative(resource, amount); return 0; }
            });
        }
        return views.iterator();
    }
    void apply(List<ItemStack> results) {
        input = ItemStack.EMPTY; outputs.clear();
        for (var result : results) {
            var stack = result.copy();
            for(var existing:outputs)if(!stack.isEmpty()&&ItemStack.isSameItemSameComponents(existing,stack)){int moved=Math.min(stack.getCount(),Math.max(0,existing.getMaxStackSize()-existing.getCount()));if(moved>0){existing.grow(moved);stack.shrink(moved);}}
            while (!stack.isEmpty()) outputs.add(stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
        }
        appliedRecipe = true; sourceRecipeIndex = false; remainingTime = recipeDuration = 20; owner.contentsChanged();
    }
    void exportTo(Storage<ItemVariant> target) {exportTo(target,false);}
    void exportTo(Storage<ItemVariant> target,boolean single) {
        try (Transaction tx = Transaction.openOuter()) {
            for (var stack : outputs) {
                if(stack.isEmpty())continue;
                long moved=target.insert(ItemVariant.of(stack),stack.getCount(),tx);
                if(moved>0){updateSnapshots(tx);stack.shrink((int)moved);}
                if(single)break;
            }
            outputs.removeIf(ItemStack::isEmpty); tx.commit();
        }
    }
    public List<ItemStack> collect() {
        var stacks = new ArrayList<>(outputs()); if (!input.isEmpty()) stacks.add(input.copy());
        clear(); return stacks;
    }
    public void clear() { input = ItemStack.EMPTY; outputs.clear(); remainingTime = -1; recipeDuration = 0; appliedRecipe = false; recipe = null; sourceRecipeIndex = false; owner.contentsChanged(); }
    void resumeSourceRecipeSelection() {
        if (!sourceRecipeIndex || appliedRecipe || input.isEmpty()) return;
        // The pinned save has an INDEX, never a recipe ID. Resolve that selector against the real
        // current candidates, retaining the saved timer; it is not an asserted old recipe identity.
        var selection = owner.selectRecipe(input, remainingTime == -1 ? recipeIndex + 1 : recipeIndex);
        if (selection.id() != null) recipeIndex = selection.index();
        recipe = selection.id(); sourceRecipeIndex = false;
        if (remainingTime == -1) remainingTime = recipeDuration = selection.duration() * Math.max(1, input.getCount() / 5);
        owner.contentsChanged();
    }
    public void save(ValueOutput out) {
        if (!input.isEmpty()) out.store("Input", ItemStack.CODEC, input);
        out.store("Outputs", ItemStack.CODEC.listOf(), outputs); out.putFloat("ProcessingTime", remainingTime); out.putFloat("RecipeTime", recipeDuration); out.putBoolean("AppliedRecipe", appliedRecipe); out.putInt("RecipeIndex", recipeIndex);
        if (recipe != null) out.store("Recipe", Identifier.CODEC, recipe);
        if (sourceRecipeIndex) out.putBoolean("SourceRecipeIndex", true);
    }
    public void load(ValueInput in) {
        input = in.read("Input", ItemStack.CODEC).orElse(ItemStack.EMPTY); outputs = new ArrayList<>(in.read("Outputs", ItemStack.CODEC.listOf()).orElse(List.of()));
        float time = in.getFloatOr("ProcessingTime", -1), duration = in.getFloatOr("RecipeTime", 0);
        remainingTime = Float.isFinite(time) ? Math.clamp(time, -1, 1000000) : -1; recipeDuration = Float.isFinite(duration) ? Math.clamp(duration, 0, 1000000) : 0;
        appliedRecipe = in.getBooleanOr("AppliedRecipe", false); recipeIndex = Math.max(0, in.getIntOr("RecipeIndex", 0)); recipe = in.read("Recipe", Identifier.CODEC).orElse(null);
        sourceRecipeIndex = in.getBooleanOr("SourceRecipeIndex", false);
    }
}
