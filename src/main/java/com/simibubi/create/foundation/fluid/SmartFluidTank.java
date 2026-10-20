package com.simibubi.create.foundation.fluid;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.minecraft.world.level.storage.*;
import java.util.function.Consumer;

/** A single fluid reservoir with committed change notifications and explicit structural resizing. */
public class SmartFluidTank extends SingleVariantStorage<FluidVariant> {
    private long capacity;
    private final Consumer<FluidStack> changed;
    public SmartFluidTank(long capacity, Consumer<FluidStack> changed) { this.capacity = capacity; this.changed = changed; }
    @Override protected FluidVariant getBlankVariant() { return FluidVariant.blank(); }
    @Override protected long getCapacity(FluidVariant variant) { return capacity; }
    @Override protected void onFinalCommit() { changed.accept(getFluid()); }
    public FluidStack getFluid() { return new FluidStack(variant, amount); }
    public boolean isEmpty() { return isResourceBlank() || getFluidAmount() == 0; }
    public long getFluidAmount() { return amount; }
    public void setCapacity(long capacity) {
        if (Transaction.isOpen()) throw new IllegalStateException("Cannot restructure a tank during a transfer transaction");
        this.capacity = Math.max(0, capacity);
        amount = Math.min(amount, this.capacity); if (amount == 0) variant = FluidVariant.blank();
    }
    public void setFluid(FluidStack stack) {
        if (Transaction.isOpen()) throw new IllegalStateException("Cannot replace tank contents during a transfer transaction");
        amount = Math.min(capacity, stack.getAmount()); variant = amount == 0 ? FluidVariant.blank() : stack.variant(); onFinalCommit();
    }
    public long fill(FluidStack stack) { if (stack.isEmpty()) return 0; try (var tx = Transaction.openOuter()) { long filled = insert(stack.variant(), stack.getAmount(), tx); tx.commit(); return filled; } }
    public void clear() { setFluid(FluidStack.EMPTY); }
    public void save(ValueOutput out) { SingleVariantStorage.writeValue(this, FluidVariant.CODEC, out); }
    public void load(ValueInput in) {
        SingleVariantStorage.readValue(this, FluidVariant.CODEC, FluidVariant::blank, in);
        // NeoForge FluidStack stores an id and millibuckets. New saves always use Fabric variants/units.
        String oldId = in.getStringOr("id", "");
        if (!oldId.isEmpty()) {
            var id = oldId.equals("neoforge:milk")?com.simibubi.create.Create.asResource("milk"):net.minecraft.resources.Identifier.tryParse(oldId);
            var fluid = id == null ? null : net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(id);
            var components = in.read("components", net.minecraft.core.component.DataComponentPatch.CODEC).orElse(net.minecraft.core.component.DataComponentPatch.EMPTY);
            variant = fluid == null ? FluidVariant.blank() : FluidVariant.of(fluid, components);
            amount = Math.max(0, Math.min(capacity / 81, in.getLongOr("amount", 0))) * 81;
        }
        amount = variant.isBlank() ? 0 : Math.clamp(amount, 0, capacity);
        if (amount == 0) variant = FluidVariant.blank();
    }
}
