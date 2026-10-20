package com.simibubi.create.content.logistics.itemHatch;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.item.LoadedItemStorage;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
/** Real shared Fabric transactions move original complete variants between actual finite owners. */
public final class ItemHatchTransfer {
 public enum Result { NO_STORAGE,NO_INSERTION,INSERTED }
 private record Access(Player player,Level level,BlockPos hatch){}
 private static final ThreadLocal<Set<Access>> ACTIVE=ThreadLocal.withInitial(HashSet::new);
 public static Result deposit(Level level,BlockPos pos,ItemHatchBlockEntity hatch,Player player,boolean heldOnly){
  var access=new Access(player,level,pos.immutable());var active=ACTIVE.get();if(!active.add(access))return Result.NO_INSERTION;
  try{
   var target=pos.relative(hatch.getBlockState().getValue(ItemHatchBlock.FACING));var owner=LoadedLevelAccess.blockEntity(level,target);if(owner==null)return Result.NO_STORAGE;
   var destination=LoadedItemStorage.find(level,target,null);if(destination==null)return Result.NO_STORAGE;
   var inventory=PlayerInventoryStorage.of(player);boolean movedAny=false;int selected=player.getInventory().getSelectedSlot();
   for(int slot=heldOnly?selected:9;slot<(heldOnly?selected+1:36);slot++){
    var source=inventory.getSlot(slot);if(source.isResourceBlank()||source.getAmount()<=0)continue;var original=source.getResource();ItemStack sample=original.toStack();
    if(!sample.getItem().canFitInsideContainerItems()&&!PackageItem.isPackage(sample))continue;
    if(!hatch.filtering.getFilter().isEmpty()&&!hatch.filtering.test(sample))continue;
    try(var tx=Transaction.openOuter()){
     long offered=source.getAmount();long inserted=destination.insert(original,offered,tx);if(inserted<=0)continue;
     if(inserted>offered||source.extract(original,inserted,tx)!=inserted)throw new IllegalStateException("Actual hatch source changed during native transaction");
     if(LoadedLevelAccess.blockEntity(level,target)!=owner||LoadedLevelAccess.blockEntity(level,pos)!=hatch||hatch.isRemoved())continue;
     tx.commit();movedAny=true;
    }
   }
   return movedAny?Result.INSERTED:Result.NO_INSERTION;
  }finally{active.remove(access);if(active.isEmpty())ACTIVE.remove();}
 }
 private ItemHatchTransfer(){}
}
