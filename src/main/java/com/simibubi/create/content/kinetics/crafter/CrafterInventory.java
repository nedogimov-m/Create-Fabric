package com.simibubi.create.content.kinetics.crafter;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.*;

public final class CrafterInventory extends SingleVariantStorage<ItemVariant> {
    private final MechanicalCrafterBlockEntity owner;
    CrafterInventory(MechanicalCrafterBlockEntity owner) { this.owner = owner; }
    @Override protected ItemVariant getBlankVariant() { return ItemVariant.blank(); }
    @Override protected long getCapacity(ItemVariant variant) { return 1; }
    @Override protected boolean canInsert(ItemVariant resource) { return owner.phase == MechanicalCrafterBlockEntity.Phase.IDLE && !owner.covered; }
    @Override protected boolean canExtract(ItemVariant resource) { return false; }
    @Override protected void onFinalCommit() { owner.inputChanged(); }
    /** Source item-handler call, backed by this same canonical Fabric transaction slot. */
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (slot != 0) throw new IndexOutOfBoundsException(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = insert(ItemVariant.of(stack), stack.getCount(), transaction);
            if (!simulate) transaction.commit();
            return stack.copyWithCount(stack.getCount() - (int) inserted);
        }
    }
    public ItemStack stack() { return isResourceBlank() ? ItemStack.EMPTY : variant.toStack((int) amount); }
    void take(TransactionContext tx) { updateSnapshots(tx); variant = ItemVariant.blank(); amount = 0; }
    /** The source arm is explicitly allowed to extract the real local crafter slot. */
    public long extractForArm(ItemVariant resource,long maximum,TransactionContext transaction) {
        net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions.notBlankNotNegative(resource,maximum);
        if(maximum==0||!variant.equals(resource))return 0;
        long taken=Math.min(maximum,amount);
        if(taken==0)return 0;
        updateSnapshots(transaction);amount-=taken;if(amount==0)variant=ItemVariant.blank();return taken;
    }
    public ItemStack remove() { var stack = stack(); try (Transaction tx = Transaction.openOuter()) { take(tx); tx.commit(); } return stack; }
    void save(ValueOutput out) { SingleVariantStorage.writeValue(this, ItemVariant.CODEC, out); }
    void load(ValueInput in) { SingleVariantStorage.readValue(this, ItemVariant.CODEC, ItemVariant::blank, in); }
}
