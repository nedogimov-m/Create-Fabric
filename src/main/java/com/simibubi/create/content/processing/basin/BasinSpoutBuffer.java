package com.simibubi.create.content.processing.basin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.fluid.FluidStack;
import com.simibubi.create.foundation.item.LoadedItemStorage;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Already-paid outputs belong here until a real target transaction or saved world-drop handoff commits. */
public final class BasinSpoutBuffer extends SnapshotParticipant<BasinSpoutBuffer.Snapshot> {
    public record Fluid(FluidVariant variant, long amount) {
        public static final Codec<Fluid> CODEC = RecordCodecBuilder.<Fluid>create(i -> i.group(
            FluidVariant.CODEC.fieldOf("variant").forGetter(Fluid::variant),
            Codec.LONG.fieldOf("amount").forGetter(Fluid::amount)).apply(i, Fluid::new))
            .validate(value -> value.amount >= 0 && (value.variant.isBlank() == (value.amount == 0))
                ? DataResult.success(value) : DataResult.error(() -> "Invalid basin queued fluid"));
    }
    record Snapshot(List<ItemStack> items, List<Fluid> fluids) {}
    private final BasinBlockEntity owner;
    private List<ItemStack> items = List.of();
    private List<Fluid> fluids = List.of();
    private boolean flushing;
    BasinSpoutBuffer(BasinBlockEntity owner) { this.owner = owner; }
    public boolean isEmpty() { return items.isEmpty() && fluids.isEmpty(); }
    public List<ItemStack> items() { return items.stream().map(ItemStack::copy).toList(); }
    public List<Fluid> fluids() { return List.copyOf(fluids); }
    @Override protected Snapshot createSnapshot() { return new Snapshot(items, fluids); }
    @Override protected void readSnapshot(Snapshot snapshot) { items = snapshot.items; fluids = snapshot.fluids; }
    @Override protected void onFinalCommit() { owner.notifyChangeOfContents(); }
    public void append(List<ItemStack> paidItems, List<Fluid> paidFluids, TransactionContext transaction) {
        var nextItems = new ArrayList<>(items); var nextFluids = new ArrayList<>(fluids);
        for (var stack : paidItems) if (!stack.isEmpty()) nextItems.add(stack.copy());
        for (var fluid : paidFluids) if (!fluid.variant.isBlank() && fluid.amount > 0) nextFluids.add(fluid);
        updateSnapshots(transaction); items = List.copyOf(nextItems); fluids = List.copyOf(nextFluids);
    }
    private boolean current() {
        return owner.getLevel() instanceof ServerLevel level && !owner.isRemoved()
            && LoadedLevelAccess.blockEntity(level, owner.getBlockPos()) == owner;
    }
    public void flush() {
        if (flushing || isEmpty() || !current()) return;
        flushing = true;
        try {
            var level = (ServerLevel) owner.getLevel();
            var side = owner.getBlockState().getValue(BasinBlock.FACING);
            if (side == Direction.DOWN) { BasinOverflowRecovery.capture(this, level); return; }
            var target = owner.getBlockPos().relative(side).below();
            if (!LoadedLevelAccess.isLoaded(level, target)) return;
            var be = LoadedLevelAccess.blockEntity(level, target);
            var filter = be == null ? null : BlockEntityBehaviour.get(be, FilteringBehaviour.TYPE);
            if (filter != null && filter.isRecipeFilter()) filter = null;
            var targetItems = LoadedItemStorage.find(level, target, side.getOpposite());
            var targetFluids = FluidStorage.SIDED.find(level, target, LoadedLevelAccess.blockState(level, target), be, side.getOpposite());
            // Snapshots are immutable lists: callbacks cannot mutate an element behind a source guard.
            var originalItems = items; var originalFluids = fluids;
            try (Transaction transaction = Transaction.openOuter()) {
                var nextItems = new ArrayList<ItemStack>(); var nextFluids = new ArrayList<Fluid>();
                for (var stack : originalItems) {
                    if (targetItems == null || filter != null && !filter.test(stack)) { nextItems.add(stack); continue; }
                    long moved = targetItems.insert(ItemVariant.of(stack), stack.getCount(), transaction);
                    if (moved < 0 || moved > stack.getCount()) throw new IllegalStateException("Invalid native basin item insertion result");
                    if (moved < stack.getCount()) nextItems.add(stack.copyWithCount(stack.getCount() - (int) moved));
                }
                for (var fluid : originalFluids) {
                    if (targetFluids == null) { nextFluids.add(fluid); continue; }
                    // Original queued fluids are all-or-nothing, unlike its item spout.
                    try (Transaction nested = transaction.openNested()) {
                        long moved = targetFluids.insert(fluid.variant, fluid.amount, nested);
                        if (moved == fluid.amount) nested.commit(); else nextFluids.add(fluid);
                    }
                }
                if (!current() || items != originalItems || fluids != originalFluids) return;
                updateSnapshots(transaction); items = List.copyOf(nextItems); fluids = List.copyOf(nextFluids);
                transaction.commit();
            }
        } finally { flushing = false; }
    }
    void captureRemoved() {
        if (owner.getLevel() instanceof ServerLevel level && !isEmpty()) BasinOverflowRecovery.capture(this, level);
    }
    // Only the final concrete recovery owner calls these callback-free source operations.
    BasinBlockEntity owner() { return owner; }
    Object itemOwner() { return items; }
    Object fluidOwner() { return fluids; }
    void clearPure() { items = List.of(); fluids = List.of(); }
    public void save(ValueOutput output) {
        output.store("Overflow", ItemStack.CODEC.listOf(), items);
        output.store("FluidOverflow", Fluid.CODEC.listOf(), fluids);
    }
    public void load(ValueInput input) {
        items = List.copyOf(input.read("Overflow", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()).stream().filter(stack -> !stack.isEmpty()).toList());
        fluids = List.copyOf(input.read("FluidOverflow", Fluid.CODEC.listOf()).orElse(List.of()).stream().filter(fluid -> fluid.amount > 0).toList());
    }
}
