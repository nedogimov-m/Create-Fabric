package com.simibubi.create.content.fluids.tank;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import java.util.function.Consumer;

public final class CreativeFluidTankBlockEntity extends FluidTankBlockEntity {
    public CreativeFluidTankBlockEntity(BlockPos pos, BlockState state) { super(AllBlockEntityTypes.CREATIVE_FLUID_TANK, pos, state); }
    @Override protected SmartFluidTank createInventory() { return new CreativeSmartFluidTank(getCapacityMultiplier(), this::onFluidStackChanged); }
    public static final class CreativeSmartFluidTank extends SmartFluidTank {
        public CreativeSmartFluidTank(long capacity, Consumer<FluidStack> changed) { super(capacity, changed); }
        public void setContainedFluid(FluidStack stack) { setFluid(stack.isEmpty() ? FluidStack.EMPTY : new FluidStack(stack.variant(), getCapacity())); }
        @Override public long insert(FluidVariant resource, long amount, TransactionContext tx) { StoragePreconditions.notBlankNotNegative(resource, amount); return amount; }
        @Override public long extract(FluidVariant resource, long amount, TransactionContext tx) { StoragePreconditions.notBlankNotNegative(resource, amount); return resource.equals(variant) ? Math.min(getCapacity(), amount) : 0; }
        @Override public long getFluidAmount() { return isResourceBlank() ? 0 : getCapacity(); }
        @Override public long getAmount() { return getFluidAmount(); }
        @Override public FluidStack getFluid() { return new FluidStack(getResource(), getFluidAmount()); }
        @Override public void setCapacity(long capacity) { var fluid = getFluid(); super.setCapacity(capacity); if (!fluid.isEmpty()) setContainedFluid(fluid); }
        @Override public void load(ValueInput in) { super.load(in); if (!isResourceBlank()) amount = getCapacity(); }
    }
}
