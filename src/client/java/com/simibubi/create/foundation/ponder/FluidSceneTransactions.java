package com.simibubi.create.foundation.ponder;

import com.simibubi.create.foundation.fluid.FluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

/** Source scene amounts cross the millibucket boundary exactly once; owners use Fabric units. */
public final class FluidSceneTransactions {
    public static Fluid still(Fluid fluid) {
        return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    }

    public static FluidStack stack(Fluid fluid, long millibuckets) {
        return new FluidStack(FluidVariant.of(fluid), units(millibuckets));
    }

    public static FluidStack copyWithMillibuckets(FluidStack stack, long millibuckets) {
        return new FluidStack(stack.variant(), units(millibuckets));
    }

    public static long fill(Storage<FluidVariant> storage, FluidStack stack) {
        if (stack.isEmpty()) return 0;
        try (var transaction = Transaction.openOuter()) {
            long filled = storage.insert(stack.variant(), stack.getAmount(), transaction);
            transaction.commit();
            return filled;
        }
    }

    public static long drainMillibuckets(Storage<FluidVariant> storage, long millibuckets) {
        long maximum = units(millibuckets);
        if (maximum == 0) return 0;
        try (var transaction = Transaction.openOuter()) {
            for (var view : storage.nonEmptyViews()) {
                long drained = storage.extract(view.getResource(), maximum, transaction);
                if (drained == 0) continue;
                transaction.commit();
                return drained;
            }
        }
        return 0;
    }

    private static long units(long millibuckets) {
        if (millibuckets < 0) throw new IllegalArgumentException("Negative source fluid amount");
        return Math.multiplyExact(millibuckets, FluidConstants.BUCKET / 1000);
    }

    private FluidSceneTransactions() {}
}
