package com.simibubi.create.content.contraptions.behaviour.dispenser;

import com.simibubi.create.api.contraption.dispenser.DefaultMountedDispenseBehavior;
import com.simibubi.create.api.contraption.dispenser.MountedDispenseBehavior;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.item.MountedItemInventory;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import java.util.ArrayList;
import java.util.List;

/** Invocation-local slot reservation; nested addon behaviours cannot overwrite the pending result. */
public final class MountedDispenseExecution {
    public static final String PENDING_REMAINDERS = "CreatePendingDispenseRemainders";
    private record Invocation(MovementContext context, int slot, Invocation parent) {}
    private static final ThreadLocal<Invocation> CURRENT = new ThreadLocal<>();

    public static void dispense(MountedItemStorage storage, int slot, MountedDispenseBehavior behaviour,
                                MovementContext context, BlockPos pos) {
        MountedItemInventory.requireNoTransaction();
        if (isReserved(context, slot))
            throw new IllegalStateException("Cannot dispense a mounted slot while its result is pending");
        var parent = CURRENT.get();
        CURRENT.set(new Invocation(context, slot, parent));
        try {
            var remainder = behaviour.dispense(storage.getStackInSlot(slot).copy(), context, pos);
            storage.setStackInSlot(slot, remainder);
        } finally {
            if (parent == null) CURRENT.remove();
            else CURRENT.set(parent);
        }
    }

    public static boolean isReserved(MovementContext context, int slot) {
        for (var current = CURRENT.get(); current != null; current = current.parent)
            if (sameActor(current.context,context) && current.slot == slot) return true;
        return false;
    }

    public static int selectedSlot(MovementContext context) {
        for (var current = CURRENT.get(); current != null; current = current.parent)
            if (sameActor(current.context,context)) return current.slot;
        return -1;
    }

    public static void placeItemInInventory(ItemStack stack, MovementContext context, BlockPos pos) {
        if (stack.isEmpty() || context.world.isClientSide()) return;
        MountedItemInventory.requireNoTransaction();
        if (!context.contraption.getStorage().ownsContents())
            throw new IllegalStateException("Cannot return a remainder to a contraption that no longer owns cargo");
        storeOrDrop(stack, context, pos);
    }

    private static void storeOrDrop(ItemStack stack, MovementContext context, BlockPos pos) {
        var variant = ItemVariant.of(stack);
        long remaining = stack.getCount();
        try (var transaction = Transaction.openOuter()) {
            var local = context.getItemStorage();
            if (local != null && local.isAvailable()) {
                for (int slot = 0; slot < local.getSlotCount() && remaining > 0; slot++) {
                    if (!isReserved(context, slot))
                        remaining -= local.getSlot(slot).insert(variant, remaining, transaction);
                }
            }
            if (remaining > 0)
                remaining -= context.contraption.getStorage().getAllItems().insert(variant, remaining, transaction);
            transaction.commit();
        }
        // A world action must never happen inside a transfer transaction, including an aborted simulation.
        if (remaining > 0) {
            var overflow=stack.copyWithCount(Math.toIntExact(remaining));
            if (!DefaultMountedDispenseBehavior.spawnItem(context.world,overflow,
                6,MountedDispenseBehavior.getDispenserNormal(context),pos,context))
                queueRemainder(overflow,context);
        }
    }

    /** Takes ownership when a world entity cannot currently be added; this payload is server-private. */
    public static void queueRemainder(ItemStack stack,MovementContext context) {
        if(stack.isEmpty())return;
        MountedItemInventory.requireNoTransaction();
        if(!context.contraption.getStorage().ownsContents())
            throw new IllegalStateException("Cannot queue a remainder without mounted ownership");
        var pending=new ArrayList<>(readPending(context));
        var copy=stack.copy();
        while(!copy.isEmpty())pending.add(copy.split(Math.min(copy.getCount(),copy.getMaxStackSize())));
        writePending(context,pending);
    }

    public static boolean hasPendingRemainders(MovementContext context) { return !readPending(context).isEmpty(); }

    public static void retryPendingRemainders(MovementContext context,BlockPos pos) {
        drainPending(context,pos);
    }

    /** Call before disassembly mutates any world blocks; false leaves the REMOVED owner intact. */
    public static boolean prepareDisassembly(MovementContext context,BlockPos targetPos) {
        // A native world callback can request disassembly while the selected result is still detached.
        // Keep world ownership until every invocation on this carrier has completed.
        for(var current=CURRENT.get();current!=null;current=current.parent)
            if(current.context.contraption==context.contraption)return false;
        drainPending(context,targetPos);
        return !hasPendingRemainders(context);
    }

    private static boolean sameActor(MovementContext first,MovementContext second) {
        return first==second||first.contraption==second.contraption&&first.localPos.equals(second.localPos);
    }

    private static void drainPending(MovementContext context,BlockPos pos) {
        if(context.world.isClientSide()||!context.contraption.getStorage().ownsContents())return;
        MountedItemInventory.requireNoTransaction();
        var pending=readPending(context);
        if(pending.isEmpty())return;
        context.blockEntityData.remove(PENDING_REMAINDERS);
        for(var stack:pending)storeOrDrop(stack,context,pos);
    }

    private static List<ItemStack> readPending(MovementContext context) {
        if(!context.blockEntityData.contains(PENDING_REMAINDERS))return List.of();
        return TagValueInput.create(ProblemReporter.DISCARDING,context.world.registryAccess(),context.blockEntityData)
            .read(PENDING_REMAINDERS,ItemStack.CODEC.listOf())
            .orElseThrow(()->new IllegalStateException("Invalid pending dispenser remainder payload"));
    }

    private static void writePending(MovementContext context,List<ItemStack> pending) {
        var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,context.world.registryAccess());
        output.store(PENDING_REMAINDERS,ItemStack.CODEC.listOf(),pending);
        context.blockEntityData.put(PENDING_REMAINDERS,output.buildResult().get(PENDING_REMAINDERS).copy());
    }

    private MountedDispenseExecution() {}
}
