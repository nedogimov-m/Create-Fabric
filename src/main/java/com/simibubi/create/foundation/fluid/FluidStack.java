package com.simibubi.create.foundation.fluid;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;

/** Fluid identity includes data components. Amounts are Fabric units, not millibuckets. */
public final class FluidStack {
    public static final FluidStack EMPTY = new FluidStack(FluidVariant.blank(), 0);
    private final FluidVariant variant;
    private long amount;
    public FluidStack(FluidVariant variant, long amount) { this.variant = variant; this.amount = Math.max(0, amount); }
    public FluidVariant variant() { return variant; }
    public boolean isEmpty() { return variant.isBlank() || amount == 0; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = Math.max(0, amount); }
    public void shrink(long amount) { setAmount(this.amount - amount); }
    public FluidStack copy() { return new FluidStack(variant, amount); }
    public static boolean isSameFluidSameComponents(FluidStack first, FluidStack second) { return first.variant.equals(second.variant); }
}
