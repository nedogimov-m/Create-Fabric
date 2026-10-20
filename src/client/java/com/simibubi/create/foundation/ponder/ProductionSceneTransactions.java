package com.simibubi.create.foundation.ponder;

import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;

/** Genuine scene mutation of the single actual Millstone input slot, through its transaction. */
public final class ProductionSceneTransactions {
    public static void setMillInput(MillstoneBlockEntity millstone, ItemStack stack) {
        if (!millstone.isVirtual()) throw new IllegalArgumentException("Scene input requires its virtual owner");
        try (var transaction = Transaction.openOuter()) {
            millstone.inventory.inputs.getFirst().setStack(stack, transaction);
            transaction.commit();
        }
    }

    private ProductionSceneTransactions() {}
}
