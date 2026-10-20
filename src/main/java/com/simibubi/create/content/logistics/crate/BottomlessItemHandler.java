package com.simibubi.create.content.logistics.crate;

import java.util.*;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;

/** Original one-stack supply view and unrestricted void input, expressed as native Storage. */
public final class BottomlessItemHandler implements Storage<ItemVariant>,StorageView<ItemVariant> {
    private final Supplier<ItemStack> supplied;
    public BottomlessItemHandler(Supplier<ItemStack> supplied){this.supplied=supplied;}
    @Override public long insert(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);return amount;}
    @Override public long extract(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);var stack=supplied.get();return stack!=null&&!stack.isEmpty()&&resource.equals(ItemVariant.of(stack))?Math.min(amount,stack.getMaxStackSize()):0;}
    @Override public boolean isResourceBlank(){var stack=supplied.get();return stack==null||stack.isEmpty();}
    @Override public ItemVariant getResource(){var stack=supplied.get();return stack==null?ItemVariant.blank():ItemVariant.of(stack);}
    @Override public long getAmount(){var stack=supplied.get();return stack==null||stack.isEmpty()?0:stack.getMaxStackSize();}
    @Override public long getCapacity(){return getAmount();}
    @Override public Iterator<StorageView<ItemVariant>> iterator(){return List.<StorageView<ItemVariant>>of(this).iterator();}
    @Override public long getVersion(){return ItemStack.hashItemAndComponents(supplied.get()==null?ItemStack.EMPTY:supplied.get());}
}
