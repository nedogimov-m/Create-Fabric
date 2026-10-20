package com.simibubi.create.impl.contraption.dispenser;

import com.simibubi.create.api.contraption.dispenser.DefaultMountedDispenseBehavior;
import com.simibubi.create.api.contraption.dispenser.MountedDispenseBehavior;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.behaviour.dispenser.MountedDispenseExecution;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Standalone BlockSource inventory; never installed as a second world block entity. */
final class MountedDispenserBlockEntity extends DispenserBlockEntity {
    private final MovementContext context;
    private final int selectedSlot;

    MountedDispenserBlockEntity(BlockPos pos, BlockState state, MovementContext context, ItemStack selected) {
        super(pos, state);
        this.context = context;
        this.selectedSlot = MountedDispenseExecution.selectedSlot(context);
        var storage = context.getItemStorage();
        if (storage != null)
            for (int slot = 0; slot < Math.min(getContainerSize(), storage.getSlotCount()); slot++)
                setItem(slot, storage.getStackInSlot(slot).copy());
        if (selectedSlot >= 0) setItem(selectedSlot, selected);
    }

    @Override public ItemStack insertItem(ItemStack input) {
        var stack = input.copy();
        for (int slot = 0; slot < getContainerSize() && !stack.isEmpty(); slot++) {
            if (MountedDispenseExecution.isReserved(context, slot)) continue;
            var current = getItem(slot);
            if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) continue;
            int accepted = Math.min(stack.getCount(), stack.getMaxStackSize() - current.getCount());
            if (accepted <= 0) continue;
            if (current.isEmpty()) setItem(slot, stack.split(accepted));
            else { current.grow(accepted); stack.shrink(accepted); }
        }
        if (!stack.isEmpty()) {
            try (var transaction = Transaction.openOuter()) {
                long accepted = context.contraption.getStorage().getAllItems()
                    .insert(ItemVariant.of(stack), stack.getCount(), transaction);
                transaction.commit();
                stack.shrink(Math.toIntExact(accepted));
            }
        }
        if (!stack.isEmpty() && !DefaultMountedDispenseBehavior.spawnItem(context.world, stack, 6,
            MountedDispenseBehavior.getDispenserNormal(context), getBlockPos(), context))
            MountedDispenseExecution.queueRemainder(stack,context);
        // Native addToInventoryOrDispense must not attempt a second world action for an owned remainder.
        return ItemStack.EMPTY;
    }

    @Override public void setChanged() {
        // The real inventory gets one write per changed slot when the native call returns.
    }

    void flush() {
        var storage = context.getItemStorage();
        if (storage == null || !storage.isAvailable()) return;
        for (int slot = 0; slot < Math.min(getContainerSize(), storage.getSlotCount()); slot++) {
            if (MountedDispenseExecution.isReserved(context,slot)) continue;
            var next = getItem(slot);
            var old = storage.getStackInSlot(slot);
            if (!ItemStack.matches(next, old)) storage.setStackInSlot(slot, next.copy());
        }
    }
}
