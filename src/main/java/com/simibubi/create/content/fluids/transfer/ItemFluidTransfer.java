package com.simibubi.create.content.fluids.transfer;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;

/** Detached single-item context: simulations can exchange containers without changing the input stack. */
final class ItemFluidTransfer extends SingleStackStorage {
    private ItemStack stack;
    final ContainerItemContext context = ContainerItemContext.ofSingleSlot(this);
    ItemFluidTransfer(ItemStack input) { stack = input.copyWithCount(1); }
    @Override protected ItemStack getStack() { return stack; }
    @Override protected void setStack(ItemStack stack) { this.stack = stack; }
    ItemStack result() { return stack.copy(); }

    static long fill(ItemStack input, FluidVariant variant, long amount, java.util.function.Consumer<ItemStack> result) {
        var item = new ItemFluidTransfer(input);
        var storage = item.context.find(FluidStorage.ITEM);
        if (storage == null) return 0;
        try (var tx = Transaction.openNested(Transaction.getCurrentUnsafe())) {
            long filled = storage.insert(variant, amount, tx);
            if (filled > 0) result.accept(item.result());
            // Always abort the detached handler; applying a plan changes the real machine and item atomically.
            return filled;
        }
    }
}
