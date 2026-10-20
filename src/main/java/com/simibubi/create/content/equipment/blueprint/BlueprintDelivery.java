package com.simibubi.create.content.equipment.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.box.*;
import com.simibubi.create.foundation.mixin.ItemEntityPickupAccessor;
import com.simibubi.create.foundation.utility.*;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.*;
import net.minecraft.world.level.storage.*;

/** Durable paid-output custody and source-order native crafting continuation. */
public final class BlueprintDelivery extends SavedData {
 private static final Set<ServerPlayer> CONTINUING=Collections.newSetFromMap(new IdentityHashMap<>());
 private record Encoded(UUID id,UUID player,BlockPos position,ItemStack loose,Optional<CompoundTag> entity,Optional<ResourceKey<Level>> dimension){
  static final Codec<Encoded> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("Id").forGetter(Encoded::id),UUIDUtil.CODEC.fieldOf("Player").forGetter(Encoded::player),BlockPos.CODEC.fieldOf("Position").forGetter(Encoded::position),ItemStack.OPTIONAL_CODEC.fieldOf("Loose").forGetter(Encoded::loose),CompoundTag.CODEC.optionalFieldOf("Entity").forGetter(Encoded::entity),Level.RESOURCE_KEY_CODEC.optionalFieldOf("Dimension").forGetter(Encoded::dimension)).apply(i,Encoded::new));
 }
 private record EncodedDebt(UUID result,UUID player,Identifier recipe,int width,int height,List<ItemStack> inputs,boolean hookAttempted){
  static final Codec<EncodedDebt> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("Result").forGetter(EncodedDebt::result),UUIDUtil.CODEC.fieldOf("Player").forGetter(EncodedDebt::player),Identifier.CODEC.fieldOf("Recipe").forGetter(EncodedDebt::recipe),Codec.intRange(0,3).fieldOf("Width").forGetter(EncodedDebt::width),Codec.intRange(0,3).fieldOf("Height").forGetter(EncodedDebt::height),ItemStack.OPTIONAL_CODEC.listOf().fieldOf("Inputs").forGetter(EncodedDebt::inputs),Codec.BOOL.fieldOf("HookAttempted").forGetter(EncodedDebt::hookAttempted)).apply(i,EncodedDebt::new));
 }
 public static final Codec<BlueprintDelivery> CODEC=RecordCodecBuilder.create(i->i.group(Encoded.CODEC.listOf().fieldOf("Pending").forGetter(BlueprintDelivery::encodeReceipts),EncodedDebt.CODEC.listOf().optionalFieldOf("Crafts",List.of()).forGetter(data->data.debts.stream().map(Debt::encode).toList())).apply(i,BlueprintDelivery::new));
 public static final SavedDataType<BlueprintDelivery> TYPE=new SavedDataType<>(Create.asResource("blueprint_crafting_delivery"),BlueprintDelivery::new,CODEC,null);
 private final List<Receipt> entries=new ArrayList<>();private final List<Debt> debts=new ArrayList<>();private ServerLevel ownerLevel;private boolean delivering,processingDebt;
 private record Owners(List<Receipt> receipts,List<Debt> debts){}
 private final SnapshotParticipant<Owners> publication=new SnapshotParticipant<>(){
  @Override protected Owners createSnapshot(){return new Owners(List.copyOf(entries),List.copyOf(debts));}
  @Override protected void readSnapshot(Owners snapshot){entries.clear();entries.addAll(snapshot.receipts);debts.clear();debts.addAll(snapshot.debts);setDirty();}
  @Override protected void onFinalCommit(){setDirty();}
 };
 public BlueprintDelivery(){}
 private BlueprintDelivery(List<Encoded> saved,List<EncodedDebt> crafts){for(var entry:saved)entries.add(new Receipt(this,entry));for(var craft:crafts)debts.add(new Debt(craft));}
 static BlueprintDelivery get(ServerLevel level){var data=level.getDataStorage().computeIfAbsent(TYPE);if(data.ownerLevel!=null&&data.ownerLevel!=level)throw new IllegalStateException("Blueprint delivery data belongs to one native level");data.ownerLevel=level;return data;}
 private boolean current(){return NativeSavedDataIdentity.isCurrent(ownerLevel,TYPE,this);}
 private boolean current(Debt debt,Receipt result){return current()&&debts.contains(debt)&&entries.contains(result);}
 private Receipt own(ServerPlayer player,ItemStack actual,TransactionContext transaction){if(!current())throw new IllegalStateException("Retired blueprint journal cannot take actual paid output");publication.updateSnapshots(transaction);var receipt=new Receipt(this,UUID.randomUUID(),player.getUUID(),player.blockPosition(),actual);entries.add(receipt);setDirty();return receipt;}
 Receipt ownCraft(ServerPlayer player,ItemStack actual,RecipeHolder<CraftingRecipe> recipe,List<ItemStack> exactGrid,TransactionContext transaction){if(exactGrid.size()!=9)throw new IllegalArgumentException("Actual blueprint continuation requires all nine input metadata slots");var receipt=own(player,actual,transaction);var metadata=exactGrid.stream().map(ItemStack::copy).toList();debts.add(new Debt(receipt.id,player.getUUID(),recipe.id().identifier(),3,3,metadata,false));return receipt;}
 boolean owns(Receipt receipt){return current()&&receipt.owner==this&&entries.contains(receipt);}
 private Receipt receipt(UUID id){return entries.stream().filter(entry->entry.id.equals(id)).findFirst().orElse(null);}
 private Debt debt(UUID id){return debts.stream().filter(debt->debt.result.equals(id)).findFirst().orElse(null);}
 private boolean ready(Receipt receipt){return owns(receipt)&&debt(receipt.id)==null;}
 static boolean busy(ServerPlayer player){return CONTINUING.contains(player);}
 public static void tick(ServerLevel level){
  var data=get(level);if(data.delivering||data.processingDebt||level.getGameTime()%10!=0)return;
  for(var debt:List.copyOf(data.debts)){var player=level.getServer().getPlayerList().getPlayer(debt.player);if(player!=null&&player.level()==level&&player.isAlive()&&!player.isRemoved())data.continueCraft(player,debt,null);}
  for(var receipt:List.copyOf(data.entries)){var player=level.getServer().getPlayerList().getPlayer(receipt.player);if(player!=null&&player.level()==level&&player.isAlive()&&!player.isRemoved()&&data.ready(receipt))data.deliver(player,receipt);}
 }
 void finishCraft(ServerPlayer player,Receipt result,RecipeHolder<CraftingRecipe> cached){if(!owns(result))return;var debt=debt(result.id);if(debt==null)throw new IllegalStateException("Paid blueprint result has no native crafting continuation");continueCraft(player,debt,cached);}
 private void continueCraft(ServerPlayer player,Debt debt,RecipeHolder<CraftingRecipe> cached){
  if(processingDebt||!current()||!debts.contains(debt)||ownerLevel==null||player.level()!=ownerLevel||ownerLevel.getServer().getPlayerList().getPlayer(player.getUUID())!=player||!player.getUUID().equals(debt.player))return;
  var result=receipt(debt.result);if(result==null)throw new IllegalStateException("Native paid craft continuation lost its sole result receipt");
  RecipeHolder<CraftingRecipe> recipe=cached;
  if(recipe==null){var found=ownerLevel.getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,debt.recipe));if(found.isEmpty()||!(found.get().value() instanceof CraftingRecipe crafting))return;recipe=new RecipeHolder<>(found.get().id(),crafting);}
  if(!recipe.id().identifier().equals(debt.recipe))throw new IllegalArgumentException("Actual native craft continuation recipe changed");
  if(debt.inputs.size()!=debt.width*debt.height||debt.inputs.size()>9)throw new IllegalStateException("Invalid saved native crafting input metadata");
  var input=CraftingInput.of(debt.width,debt.height,debt.inputs.stream().map(ItemStack::copy).toList());Throwable primary=null;if(!CONTINUING.add(player))return;processingDebt=true;
  try{
   if(!debt.hookAttempted){debt.hookAttempted=true;setDirty();try{result.stack.onCraftedBy(player,1);if(current(debt,result))player.triggerRecipeCrafted(recipe,input.items());}catch(RuntimeException|Error failure){primary=failure;}}
   // Source order: the genuine recipe's remaining-items callback follows the native crafted hooks.
   try{
    if(current(debt,result)){
     var remainders=recipe.value().getRemainingItems(input);
     if(current(debt,result))try(var tx=Transaction.openOuter()){
      publication.updateSnapshots(tx);var distinct=Collections.newSetFromMap(new IdentityHashMap<ItemStack,Boolean>());distinct.add(result.stack);
      for(var remainder:remainders)if(!remainder.isEmpty()&&distinct.add(remainder))own(player,remainder,tx);
      if(current(debt,result)){debts.remove(debt);tx.commit();}
     }
    }
   }catch(RuntimeException|Error failure){if(primary==null)primary=failure;else primary.addSuppressed(failure);}
  }finally{processingDebt=false;CONTINUING.remove(player);setDirty();}
  // An unfinished remaining-items callback keeps debt and all material ownership; no hook repeat.
  if(current()&&!debts.contains(debt))for(var owned:List.copyOf(entries))if(owned.player.equals(player.getUUID())&&ready(owned))try{deliver(player,owned);}catch(RuntimeException|Error failure){if(primary==null)primary=failure;else primary.addSuppressed(failure);}
  if(primary instanceof RuntimeException failure)throw failure;if(primary instanceof Error failure)throw failure;
 }
 void deliver(ServerPlayer player,Receipt receipt){
  if(delivering||processingDebt||!current()||receipt.owner!=this||!entries.contains(receipt)||!ready(receipt)||!player.getUUID().equals(receipt.player)||!player.isAlive()||player.isRemoved()||ownerLevel==null||player.level()!=ownerLevel||ownerLevel.getServer().getPlayerList().getPlayer(player.getUUID())!=player)return;
  delivering=true;
  try{
   var level=ownerLevel;receipt.position=player.blockPosition();receipt.reconcileActualInventoryOwner();
   if(receipt.serialized!=null){
    var id=receipt.serialized.read("UUID",UUIDUtil.CODEC).orElse(null);if(id==null)throw new IllegalStateException("Saved actual blueprint drop has no identity");
    for(var world:level.getServer().getAllLevels())if(world.getEntity(id)!=null)return;
    var target=receipt.dimension==null?level:level.getServer().getLevel(receipt.dimension);if(target==null)return;
    var problems=new ProblemReporter.Collector();var entity=EntityType.loadEntityRecursive(TagValueInput.create(problems,target.registryAccess(),receipt.serialized),target,EntitySpawnReason.LOAD,loaded->loaded);
    if(!current())return;
    if(!problems.isEmpty()||!(entity instanceof ItemEntity||entity instanceof PackageEntity)||!entity.getUUID().equals(id))throw new IllegalStateException("Native blueprint delivery entity decoding failed: "+problems.getReport());
    receipt.pending=entity;receipt.serialized=null;setDirty();
   }
   if(receipt.pending!=null){admit(level,receipt);return;}
   if(receipt.stack.isEmpty()){entries.remove(receipt);setDirty();return;}
   var inventory=PlayerInventoryStorage.of(player);
   try(var tx=Transaction.openOuter()){
    var variant=receipt.getResource();long offered=inventory.offer(variant,receipt.getAmount(),tx);
    if(!current())return;
    if(offered>0&&receipt.extract(variant,offered,tx)!=offered)throw new IllegalStateException("Actual blueprint delivery source changed");
    if(!current())return;tx.commit();
   }
   if(!current())return;
   if(receipt.stack.isEmpty()){entries.remove(receipt);setDirty();return;}
   if(!LoadedEntityAccess.isReady(level,player.blockPosition()))return;
   if(receipt.getAmount()>receipt.stack.getMaxStackSize()){
    try(var tx=Transaction.openOuter()){var variant=receipt.getResource();int portion=receipt.stack.getMaxStackSize();var child=own(player,variant.toStack(portion),tx);if(receipt.extract(variant,portion,tx)!=portion)throw new IllegalStateException("Native output portion source changed");if(!current())return;tx.commit();receipt=child;}
    if(!current())return;
   }
   var dropped=player.createItemStackToDrop(receipt.stack,false,false);if(!current()||dropped==null)return;Entity actual=dropped;
   if(PackageItem.isPackage(dropped.getItem())&&((ItemEntityPickupAccessor)dropped).create$pickupDelay()!=32767){if(dropped.getItem().getCount()!=1)throw new IllegalStateException("A native source package endpoint must contain one full cargo item");actual=PackageEntity.fromDroppedItem(level,dropped,dropped.getItem());}
   if(!current())return;receipt.pending=actual;receipt.stack=ItemStack.EMPTY;setDirty();admit(level,receipt);
  }finally{setDirty();delivering=false;}
 }
 private void admit(ServerLevel level,Receipt receipt){
  if(!current()||receipt.owner!=this||!entries.contains(receipt))return;var endpoint=receipt.pending;if(endpoint==null)return;receipt.retainCurrentRejectedCargo();if(receipt.pending==null)return;
  if(!(endpoint.level() instanceof ServerLevel actual)||actual.getServer()!=level.getServer()||actual.getEntity(endpoint.getUUID())!=null||!LoadedEntityAccess.isReady(actual,endpoint.blockPosition()))return;
  try(var acceptance=NativeEntityAcceptance.prepare(endpoint,()->current()&&entries.contains(receipt)&&receipt.pending==endpoint&&!endpoint.isRemoved()&&!payload(endpoint).isEmpty(),insertion->{insertion.requireCommitting(endpoint);entries.remove(receipt);setDirty();})){
   acceptance.add();
  }finally{if(current()&&entries.contains(receipt))receipt.retainCurrentRejectedCargo();}
 }
 private static ItemStack payload(Entity endpoint){if(endpoint instanceof ItemEntity item)return item.getItem();if(endpoint instanceof PackageEntity parcel)return parcel.getBox();throw new IllegalStateException("Unsupported actual blueprint delivery endpoint");}
 private List<Encoded> encodeReceipts(){return entries.stream().map(Receipt::encode).toList();}
 private static final class Debt {
  final UUID result,player;final Identifier recipe;final int width,height;final List<ItemStack> inputs;boolean hookAttempted;
  Debt(UUID result,UUID player,Identifier recipe,int width,int height,List<ItemStack> inputs,boolean attempted){this.result=result;this.player=player;this.recipe=recipe;this.width=width;this.height=height;this.inputs=List.copyOf(inputs);hookAttempted=attempted;}
  Debt(EncodedDebt saved){this(saved.result,saved.player,saved.recipe,saved.width,saved.height,saved.inputs,saved.hookAttempted);}
  EncodedDebt encode(){return new EncodedDebt(result,player,recipe,width,height,inputs,hookAttempted);}
 }
 static final class Receipt extends SingleStackStorage {
  final BlueprintDelivery owner;final UUID id,player;BlockPos position;ItemStack stack;Entity pending;CompoundTag serialized;ResourceKey<Level> dimension;
  Receipt(BlueprintDelivery owner,UUID id,UUID player,BlockPos position,ItemStack stack){this.owner=owner;this.id=id;this.player=player;this.position=position.immutable();this.stack=stack;}
  Receipt(BlueprintDelivery owner,Encoded saved){this(owner,saved.id,saved.player,saved.position,saved.loose);serialized=saved.entity.orElse(null);dimension=saved.dimension.orElse(null);if(serialized!=null&&!stack.isEmpty())throw new IllegalArgumentException("Two owners in blueprint delivery entry");}
  @Override protected ItemStack getStack(){return stack;}
  @Override protected void setStack(ItemStack value){stack=value;owner.setDirty();}
  @Override protected boolean canInsert(ItemVariant variant){return false;}
  @Override protected boolean canExtract(ItemVariant variant){return owner.owns(this);}
  @Override protected void onFinalCommit(){owner.setDirty();}
  void reconcileActualInventoryOwner(){
   if(stack.isEmpty()||owner.ownerLevel==null)return;var server=owner.ownerLevel.getServer();var actor=server.getPlayerList().getPlayer(player);if(actor==null||actor.isRemoved()||actor.level().getEntity(player)!=actor)return;
   for(int slot=0;slot<actor.getInventory().getContainerSize();slot++)if(actor.getInventory().getItem(slot)==stack){stack=ItemStack.EMPTY;owner.setDirty();return;}
  }
  void retainCurrentRejectedCargo(){if(pending==null)return;var current=payload(pending);if(pending.isRemoved()||current.isEmpty()){stack=current;pending=null;serialized=null;dimension=null;owner.setDirty();}}
  Encoded encode(){reconcileActualInventoryOwner();retainCurrentRejectedCargo();if(pending==null)return new Encoded(id,player,position,stack,Optional.ofNullable(serialized),Optional.ofNullable(dimension));var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,pending.registryAccess());if(!pending.save(output))throw new IllegalStateException("Actual pending blueprint item refused native serialization");return new Encoded(id,player,position,ItemStack.EMPTY,Optional.of(output.buildResult()),Optional.of(pending.level().dimension()));}
 }
}
