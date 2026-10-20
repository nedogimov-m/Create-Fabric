package com.simibubi.create.content.legacy;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** A transaction over the actual source stack count, never a copied inventory or reconstructed owner. */
final class LegacyCompoundSource extends SnapshotParticipant<Integer> {
 final ItemEntity entity;final ItemStack stack;final ItemVariant variant;final net.minecraft.core.component.DataComponentPatch components;final int count;
 LegacyCompoundSource(ItemEntity entity,ItemStack stack){this.entity=entity;this.stack=stack;variant=ItemVariant.of(stack);components=stack.getComponentsPatch();count=stack.getCount();}
 boolean valid(){return LegacyConversions.actual(entity,stack)&&stack.getCount()==count&&variant.matches(stack);}
 ItemStack takeOne(TransactionContext transaction){if(!valid())throw new IllegalStateException("Actual legacy compound source changed before native payment");updateSnapshots(transaction);return stack.split(1);}
 @Override protected Integer createSnapshot(){return stack.getCount();}
 @Override protected void readSnapshot(Integer originalCount){
  if(entity.getItem()!=stack)throw new IllegalStateException("Cannot restore an uncommitted source count into a changed native owner");
  // Native ItemStack hides its component patch while empty. Restore only this exact reference's
  // count before comparing its underlying unchanged metadata; no native callback runs here.
  int current=stack.getCount();stack.setCount(originalCount);if(!components.equals(stack.getComponentsPatch())){stack.setCount(current);throw new IllegalStateException("Cannot restore an uncommitted source count with changed native components");}
 }
}
