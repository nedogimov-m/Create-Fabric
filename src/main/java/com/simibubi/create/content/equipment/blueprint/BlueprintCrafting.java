package com.simibubi.create.content.equipment.blueprint;

import com.simibubi.create.content.equipment.blueprint.BlueprintEntity.BlueprintSection;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import java.util.*;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

/** Read-only preparation, exact native owners, and atomic payment of all source recipe slots. */
public final class BlueprintCrafting {
 private static final Set<ServerPlayer> ACTIVE=Collections.newSetFromMap(new IdentityHashMap<>());
 private record Cost(int slot,ItemStack original,ItemVariant variant,int count,int originalCount){}
 private record Plan(CraftingInput input,List<ItemStack> grid,List<Cost> costs,boolean complete,List<ItemStack> available,List<ItemStack> missing){}
 public record Preview(boolean empty,boolean craftable,ItemStack result,List<ItemStack> available,List<ItemStack> missing){}
 private static boolean live(ServerPlayer player,BlueprintSection section){return !(player instanceof FakePlayer)&&player.isAlive()&&!player.isRemoved()&&section.canPlayerUse(player)&&player.level().getServer().getPlayerList().getPlayer(player.getUUID())==player;}
 private static Plan prepare(ServerPlayer player,net.minecraft.world.SimpleContainer ghosts,int[] reserved){
  var inventory=player.getInventory();var grid=new ArrayList<ItemStack>(Collections.nCopies(9,ItemStack.EMPTY));var costs=new LinkedHashMap<Integer,Cost>();var available=new ArrayList<ItemStack>();var missing=new ArrayList<ItemStack>();boolean complete=true;
  for(int index=0;index<9;index++){
   var requested=FilterItemStack.of(ghosts.getItem(index));if(requested.isEmpty())continue;boolean found=false;
   for(int slot=0;slot<inventory.getContainerSize();slot++){
    var actual=inventory.getItem(slot);if(actual.isEmpty()||actual.getCount()<=reserved[slot]||!requested.test(player.level(),actual))continue;
    var sample=actual.copyWithCount(1);grid.set(index,sample);available.add(sample);reserved[slot]++;var before=costs.get(slot);costs.put(slot,new Cost(slot,actual,ItemVariant.of(actual),before==null?1:before.count+1,actual.getCount()));found=true;break;
   }
   if(!found){complete=false;missing.add(requested.item());}
  }
  return new Plan(CraftingInput.of(3,3,grid),List.copyOf(grid),List.copyOf(costs.values()),complete,available,missing);
 }
 private static boolean unchanged(ServerPlayer player,BlueprintSection section,CompoundTag snapshot,Plan plan){if(!live(player,section)||!snapshot.equals(section.snapshot()))return false;for(var cost:plan.costs){var actual=player.getInventory().getItem(cost.slot);if(actual!=cost.original||actual.getCount()!=cost.originalCount||!cost.variant.matches(actual))return false;}return true;}
 public static int craft(Player actor,BlueprintSection section,boolean batch){
  if(!(actor instanceof ServerPlayer player)||!live(player,section)||BlueprintDelivery.busy(player)||!ACTIVE.add(player))return 0;
  int amount=0;RecipeHolder<CraftingRecipe> cached=null;
  try{
   do{
    if(!live(player,section))break;var snapshot=section.snapshot();var ghosts=section.getItems();var plan=prepare(player,ghosts,new int[player.getInventory().getContainerSize()]);if(!plan.complete)break;
    var level=player.level();if(cached==null)cached=level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,plan.input,level).orElse(null);
    if(cached==null||!cached.value().matches(plan.input,level))break;var result=cached.value().assemble(plan.input);
    if(result.isEmpty()||result.getCount()+amount>64||!unchanged(player,section,snapshot,plan))break;
    var storage=PlayerInventoryStorage.of(player);var delivery=BlueprintDelivery.get(level);BlueprintDelivery.Receipt paid=null;Throwable primary=null;
    try(var tx=Transaction.openOuter()){
     if(result.isEmpty()||result.getCount()+amount>64||!unchanged(player,section,snapshot,plan))return amount;
     for(var cost:plan.costs)if(storage.getSlot(cost.slot).extract(cost.variant,cost.count,tx)!=cost.count)return amount;
     if(!live(player,section)||!snapshot.equals(section.snapshot()))return amount;
     paid=delivery.ownCraft(player,result,cached,plan.grid,tx);
     tx.commit();
    }catch(RuntimeException|Error failure){if(paid==null||!delivery.owns(paid))throw failure;primary=failure;}
    int produced=result.getCount();
    try{delivery.finishCraft(player,paid,cached);}catch(RuntimeException|Error failure){if(primary==null)primary=failure;else primary.addSuppressed(failure);}
    if(primary instanceof RuntimeException failure)throw failure;if(primary instanceof Error failure)throw failure;
    if(amount==0)level.playSound(null,player.blockPosition(),SoundEvents.ITEM_PICKUP,SoundSource.PLAYERS,.2f,1+level.getRandom().nextFloat());amount+=produced;
   }while(batch&&amount<64);
   return amount;
  }finally{ACTIVE.remove(player);}
 }
 public static Preview preview(ServerPlayer player,BlueprintSection section,boolean batch){
  if(!live(player,section))return null;var snapshot=section.snapshot();var ghosts=section.getItems();boolean empty=true;for(int i=0;i<9;i++)if(!ghosts.getItem(i).isEmpty()){empty=false;break;}if(empty)return new Preview(true,false,ItemStack.EMPTY,List.of(),List.of());
  var reserved=new int[player.getInventory().getContainerSize()];var available=new ArrayList<ItemStack>();var missing=new ArrayList<ItemStack>();ItemStack result=ItemStack.EMPTY;boolean craftable=false;int amount=0;RecipeHolder<CraftingRecipe> cached=null;
  do{
   var plan=prepare(player,ghosts,reserved);boolean success=plan.complete,invalid=false;ItemStack produced=ItemStack.EMPTY;
   if(success){if(cached==null)cached=player.level().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,plan.input,player.level()).orElse(null);if(cached!=null&&cached.value().matches(plan.input,player.level()))produced=cached.value().assemble(plan.input);if(produced.isEmpty()){invalid=cached==null;success=false;}else if(produced.getCount()+amount>64)success=false;}
   if(success||amount==0){available.addAll(plan.available);missing.addAll(plan.missing);}
   if(!success){if(amount==0&&!invalid)result=ghosts.getItem(9).copy();break;}
   amount+=produced.getCount();if(result.isEmpty())result=produced.copy();else result.grow(produced.getCount());craftable=true;
  }while(batch&&amount<64);
  if(!live(player,section)||!snapshot.equals(section.snapshot()))return null;
  return new Preview(false,craftable,result,merge(available),merge(missing));
 }
 private static List<ItemStack> merge(List<ItemStack> samples){var merged=new ArrayList<ItemStack>();for(var sample:samples){int remaining=sample.getCount();for(var existing:merged)if(ItemStack.isSameItemSameComponents(existing,sample)){int moved=Math.min(remaining,existing.getMaxStackSize()-existing.getCount());existing.grow(moved);remaining-=moved;if(remaining==0)break;}while(remaining>0){int count=Math.min(remaining,sample.getMaxStackSize());merged.add(sample.copyWithCount(count));remaining-=count;}}return List.copyOf(merged.subList(0,Math.min(9,merged.size())));}
 private BlueprintCrafting(){}
}
