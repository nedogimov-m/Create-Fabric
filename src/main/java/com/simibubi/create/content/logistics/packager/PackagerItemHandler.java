package com.simibubi.create.content.logistics.packager;

import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/** One outbound box, with transactional insertion directly unpacking an incoming box. */
public final class PackagerItemHandler implements Storage<ItemVariant>,StorageView<ItemVariant> {
    private final PackagerBlockEntity owner;
    PackagerItemHandler(PackagerBlockEntity owner){this.owner=owner;}
    @Override public long insert(ItemVariant resource,long amount,TransactionContext transaction){StoragePreconditions.notBlankNotNegative(resource,amount);if(amount==0||!owner.heldBox.isEmpty()||!owner.queuedExitingPackages.isEmpty())return 0;return owner.unwrapBox(resource.toStack(),transaction)?1:0;}
    @Override public long extract(ItemVariant resource,long amount,TransactionContext transaction){StoragePreconditions.notBlankNotNegative(resource,amount);if(amount==0||owner.animationTicks!=0||owner.heldBox.isEmpty()||!resource.equals(ItemVariant.of(owner.heldBox)))return 0;owner.snapshot(transaction);owner.heldBox=net.minecraft.world.item.ItemStack.EMPTY;return 1;}
    @Override public Iterator<StorageView<ItemVariant>> iterator(){return List.<StorageView<ItemVariant>>of(this).iterator();}
    @Override public boolean isResourceBlank(){return owner.heldBox.isEmpty();}
    @Override public ItemVariant getResource(){return ItemVariant.of(owner.heldBox);}
    @Override public long getAmount(){return owner.heldBox.isEmpty()?0:1;}
    @Override public long getCapacity(){return 1;}
    @Override public long getVersion(){return owner.storageVersion;}
}
