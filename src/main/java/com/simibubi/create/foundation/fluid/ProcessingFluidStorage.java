package com.simibubi.create.foundation.fluid;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Fabric fluid units throughout; one bucket is FluidConstants.BUCKET (81000), never millibuckets. */
public final class ProcessingFluidStorage {
    public final List<Tank> inputs;
    public final List<Tank> outputs;
    public final Storage<FluidVariant> automation;

    public ProcessingFluidStorage(int inputCount, int outputCount, long capacity, Runnable changed) {
        inputs = tanks(inputCount, capacity, changed);
        outputs = tanks(outputCount, capacity, changed);
        List<Tank> all = new ArrayList<>(outputs); all.addAll(inputs);
        var combined = new CombinedStorage<FluidVariant, Tank>(all);
        automation = new Storage<>() {
            @Override public long insert(FluidVariant variant, long amount, TransactionContext transaction) {
                net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions.notBlankNotNegative(variant, amount);
                // A basin keeps each incoming fluid in one segment, as the original multi-tank behaviour does.
                for (Tank tank : inputs) if (tank.getResource().equals(variant)) return tank.insert(variant, amount, transaction);
                for (Tank tank : inputs) if (tank.isResourceBlank()) return tank.insert(variant, amount, transaction);
                return 0;
            }
            @Override public long extract(FluidVariant variant, long amount, TransactionContext transaction) { return combined.extract(variant, amount, transaction); }
            @Override public Iterator<StorageView<FluidVariant>> iterator() { return combined.iterator(); }
        };
    }

    private static List<Tank> tanks(int count, long capacity, Runnable changed) {
        var result = new ArrayList<Tank>();
        for (int i = 0; i < count; i++) result.add(new Tank(capacity, changed));
        return List.copyOf(result);
    }
    public List<Tank> allTanks() { var all = new ArrayList<>(inputs); all.addAll(outputs); return all; }
    public boolean insertOutput(FluidVariant variant, long amount, TransactionContext transaction) {
        return new CombinedStorage<FluidVariant, Tank>(outputs).insert(variant, amount, transaction) == amount;
    }
    public void save(ValueOutput output) {
        for (int i = 0; i < inputs.size(); i++) inputs.get(i).save(output.child("Input" + i));
        for (int i = 0; i < outputs.size(); i++) outputs.get(i).save(output.child("Output" + i));
    }
    public void load(ValueInput input) {
        for (int i = 0; i < inputs.size(); i++) inputs.get(i).load(input.childOrEmpty("Input" + i));
        for (int i = 0; i < outputs.size(); i++) outputs.get(i).load(input.childOrEmpty("Output" + i));
    }
    public static final class Tank extends SingleVariantStorage<FluidVariant> {
        private final long capacity;
        private final Runnable changed;
        private Tank(long capacity, Runnable changed) { this.capacity = capacity; this.changed = changed; }
        @Override protected FluidVariant getBlankVariant() { return FluidVariant.blank(); }
        @Override protected long getCapacity(FluidVariant variant) { return capacity; }
        @Override protected void onFinalCommit() { changed.run(); }
        private void save(ValueOutput output) { SingleVariantStorage.writeValue(this, FluidVariant.CODEC, output); }
        private void load(ValueInput input) { SingleVariantStorage.readValue(this, FluidVariant.CODEC, FluidVariant::blank, input); }
    }
}
