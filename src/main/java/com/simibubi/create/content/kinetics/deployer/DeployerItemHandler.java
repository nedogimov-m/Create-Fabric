package com.simibubi.create.content.kinetics.deployer;

import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;

/** The live hand is insertable; collected inventory is output-only. */
public final class DeployerItemHandler extends SnapshotParticipant<DeployerItemHandler.Snapshot> implements Storage<ItemVariant> {
    record Snapshot(ItemStack held,List<ItemStack> tokens,List<Integer> counts){}
    private final DeployerBlockEntity owner;
    public DeployerItemHandler(DeployerBlockEntity owner){this.owner=owner;}
    @Override protected Snapshot createSnapshot(){return new Snapshot(owner.heldItem.copy(),new ArrayList<>(owner.overflowItems),owner.overflowItems.stream().map(ItemStack::getCount).toList());}
    @Override protected void readSnapshot(Snapshot snapshot){owner.heldItem=snapshot.held;owner.overflowItems.clear();owner.overflowItems.addAll(snapshot.tokens);for(int i=0;i<snapshot.tokens.size();i++)snapshot.tokens.get(i).setCount(snapshot.counts.get(i));}
    @Override protected void onFinalCommit(){owner.overflowItems.removeIf(ItemStack::isEmpty);owner.inventoryChanged();}
    @Override public long insert(ItemVariant resource,long amount,TransactionContext tx){
        StoragePreconditions.notBlankNotNegative(resource,amount);if(!owner.accepts(resource.toStack()))return 0;
        var held=owner.heldItem;if(!held.isEmpty()&&!resource.matches(held))return 0;long moved=Math.min(amount,resource.toStack().getMaxStackSize()-held.getCount());if(moved<=0)return 0;updateSnapshots(tx);owner.heldItem=resource.toStack((int)(held.getCount()+moved));return moved;
    }
    @Override public long extract(ItemVariant resource,long amount,TransactionContext tx){
        StoragePreconditions.notBlankNotNegative(resource,amount);long moved=0;
        for(var output:owner.overflowItems){long taken=extractOutput(output,resource,amount-moved,tx);moved+=taken;if(moved==amount)return moved;}
        return moved+extractHeld(resource,amount-moved,tx);
    }
    private long extractOutput(ItemStack token,ItemVariant resource,long amount,TransactionContext tx){if(amount==0||!owner.overflowItems.contains(token)||!resource.matches(token))return 0;long moved=Math.min(amount,token.getCount());if(moved>0){updateSnapshots(tx);token.shrink((int)moved);}return moved;}
    private long extractHeld(ItemVariant resource,long amount,TransactionContext tx){if(amount==0||!resource.matches(owner.heldItem)||!owner.filter.isEmpty()&&owner.accepts(owner.heldItem))return 0;long moved=Math.min(amount,owner.heldItem.getCount());if(moved>0){updateSnapshots(tx);owner.heldItem=owner.heldItem.copyWithCount(owner.heldItem.getCount()-(int)moved);}return moved;}
    public void consumeOne(TransactionContext tx){updateSnapshots(tx);var item=owner.heldItem.getItem();owner.heldItem=owner.heldItem.copyWithCount(owner.heldItem.getCount()-1);var remainder=item.getCraftingRemainder();if(remainder!=null){if(owner.heldItem.isEmpty())owner.heldItem=remainder.create();else owner.overflowItems.add(remainder.create());}}
    @Override public Iterator<StorageView<ItemVariant>> iterator(){
        var views=new ArrayList<StorageView<ItemVariant>>();for(var token:owner.overflowItems)views.add(view(token));views.add(view(null));return views.iterator();
    }
    private StorageView<ItemVariant> view(ItemStack token){return new StorageView<>(){
        private ItemStack stack(){return token==null?owner.heldItem:owner.overflowItems.contains(token)?token:ItemStack.EMPTY;}
        @Override public long extract(ItemVariant resource,long amount,TransactionContext tx){StoragePreconditions.notBlankNotNegative(resource,amount);return token==null?extractHeld(resource,amount,tx):extractOutput(token,resource,amount,tx);}
        @Override public boolean isResourceBlank(){return stack().isEmpty();}
        @Override public ItemVariant getResource(){return ItemVariant.of(stack());}
        @Override public long getAmount(){return stack().getCount();}
        @Override public long getCapacity(){return stack().getMaxStackSize();}
    };}
}
