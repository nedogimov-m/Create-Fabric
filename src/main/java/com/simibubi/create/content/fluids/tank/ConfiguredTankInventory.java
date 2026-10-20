package com.simibubi.create.content.fluids.tank;

import com.simibubi.create.foundation.fluid.FluidStack;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.level.storage.ValueInput;

/** The actual tank retains existing fluid above a reduced configured capacity; insertion remains blocked. */
final class ConfiguredTankInventory extends SmartFluidTank {
 private final LongSupplier configuredCapacity;
 ConfiguredTankInventory(LongSupplier capacity,Consumer<FluidStack> changed){super(capacity.getAsLong(),changed);configuredCapacity=capacity;}
 @Override protected long getCapacity(FluidVariant fluid){return Math.max(configuredCapacity.getAsLong(),amount);}
 @Override public void setCapacity(long capacity){super.setCapacity(Math.max(capacity,amount));}
 /** Explicit ownership handoffs (mount, merge, recovery) retain their complete already-owned fluid. */
 @Override public void setFluid(FluidStack fluid){
  if(Transaction.isOpen())throw new IllegalStateException("Cannot replace actual tank contents during a transfer transaction");
  amount=fluid.getAmount();variant=amount==0?FluidVariant.blank():fluid.variant();onFinalCommit();
 }
 @Override public void load(ValueInput input){super.setCapacity(Long.MAX_VALUE);super.load(input);super.setCapacity(Math.max(configuredCapacity.getAsLong(),amount));}
}
