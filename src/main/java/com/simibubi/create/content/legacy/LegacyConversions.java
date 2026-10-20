package com.simibubi.create.content.legacy;

import com.simibubi.create.*;
import com.simibubi.create.content.equipment.TreeFertilizerSimulation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.foundation.mixin.LegacyBeaconAccessor;
import com.simibubi.create.foundation.utility.*;
import com.simibubi.create.infrastructure.config.AllConfigs;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

/** Genuine current item/world owners; source's retired references are metadata, never another paid input. */
final class LegacyConversions {
 private static final Set<ItemEntity> ACTIVE=Collections.newSetFromMap(new IdentityHashMap<>());
 static boolean actual(ItemEntity entity,ItemStack stack){return entity.level() instanceof ServerLevel level&&level.getServer().isSameThread()&&TreeFertilizerSimulation.active(level)==null&&!entity.isRemoved()&&level.getEntity(entity.getUUID())==entity&&entity.getItem()==stack&&!stack.isEmpty()&&LoadedEntityAccess.isReady(level,entity.blockPosition());}
 static void finishSource(ItemEntity source,ItemStack expected){if(source.isRemoved()||source.getItem()!=expected||!(source.level() instanceof ServerLevel level)||level.getEntity(source.getUUID())!=source)return;((LegacyItemData)source).create$legacySyncStack();if(source.getItem()==expected&&expected.isEmpty()&&!source.isRemoved())source.discard();}
 static void update(ChromaticCompoundItem item,ItemStack original,ItemEntity entity){
  if(!actual(entity,original)||!ACTIVE.add(entity))return;
  try{
   var level=(ServerLevel)entity.level();var config=AllConfigs.server().recipes.serverSettings();int minimum=level.getMinY();boolean inVoid=entity.getY()<minimum&&entity.getY()-entity.getDeltaMovement().y<minimum-10&&config.enableShadowSteelRecipe();var current=original;
   if(inVoid){var shadow=new ItemStack(AllItems.SHADOW_STEEL,original.getCount());LegacyItemData.of(entity).putBoolean("JustCreated",true);entity.setItem(shadow);current=entity.getItem();if(!actual(entity,current)||current!=shadow)return;}
   if(!config.enableRefinedRadianceRecipe())return;
   if(item.getLight(original)>=config.lightSourceCountForRefinedRadiance()){emitRadiance(entity,current,original,inVoid);return;}
   int x=Mth.floor(entity.getX()),z=Mth.floor(entity.getZ());if(!LoadedLevelAccess.isLoaded(level,new BlockPos(x,minimum,z)))return;int localHeight=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);var test=new BlockPos.MutableBlockPos(x,Math.min(Mth.floor(entity.getY()),localHeight),z);boolean beacon=false;
   while(test.getY()>minimum){test.move(Direction.DOWN);var state=LoadedLevelAccess.blockState(level,test);if(state.getLightDampening()>=15&&!state.is(Blocks.BEDROCK))break;if(state.is(Blocks.BEACON)){var be=LoadedLevelAccess.blockEntity(level,test);beacon=be instanceof BeaconBlockEntity&& !((LegacyBeaconAccessor)be).create$legacyBeamSections().isEmpty();break;}}
   if(beacon){if(!actual(entity,current))return;LegacyItemData.of(entity).putBoolean("JustCreated",true);entity.setItem(new ItemStack(AllItems.REFINED_RADIANCE,current.getCount()));return;}
   if(level.getRandom().nextFloat()>.5f)return;var random=BlockPos.containing(LegacyRandom.offset(entity.position(),level.getRandom(),3));if(!LoadedLevelAccess.isLoaded(level,random))return;var state=LoadedLevelAccess.blockState(level,random);
   var behaviour=BlockEntityBehaviour.get(level,random,TransportedItemStackHandlerBehaviour.TYPE);
   if(behaviour==null)item.checkLight(original,entity,level,original,entity.position(),random,state);else LegacyTransportLights.process(item,entity,original,random);
  }finally{ACTIVE.remove(entity);}
 }
 static boolean emitCharged(ChromaticCompoundItem item,ItemEntity source,ItemStack input,LegacyTransportLights.Light light){
  if(!actual(source,input)||!light.valid())return false;var actual=new LegacyCompoundSource(source,input);var drops=LegacyMaterialDrops.get((ServerLevel)source.level());var at=source.position();var motion=source.getDeltaMovement();LegacyMaterialDrops.Receipt receipt;
  try(var transaction=Transaction.openOuter()){
   if(!light.valid()||!actual.valid())return false;var part=actual.takeOne(transaction);if(!light.pay(transaction))return false;
   // Pinned source reads the same original alias after split. A singleton is now empty and
   // its native component getter returns 0, intentionally retaining upstream's reset-to-1 quirk.
   part.set(AllDataComponents.CHROMATIC_COMPOUND_COLLECTING_LIGHT,item.getLight(input)+1);receipt=drops.own(part,at,motion,10,transaction);transaction.commit();
  }
  drops.emit((ServerLevel)source.level(),receipt);finishSource(source,input);return true;
 }
 private static void emitRadiance(ItemEntity source,ItemStack input,ItemStack retiredCompoundMetadata,boolean wasVoid){
  if(!actual(source,input))return;var level=(ServerLevel)source.level();var actual=new LegacyCompoundSource(source,input);var drops=LegacyMaterialDrops.get(level);LegacyMaterialDrops.Receipt receipt;
  try(var transaction=Transaction.openOuter()){
   // Upstream intentionally constructs plain Radiance: the paid part's components are consumed.
   actual.takeOne(transaction);receipt=drops.own(new ItemStack(AllItems.REFINED_RADIANCE),source.position(),source.getDeltaMovement(),0,true,transaction);transaction.commit();
  }
  // Retire the source before entering native add callbacks. A refused add still owns one paid result.
  // The source void branch left its old compound reference behind as metadata; it is not paid twice.
  if(actualRemaining(source,input)){
   if(wasVoid){retiredCompoundMetadata.remove(AllDataComponents.CHROMATIC_COMPOUND_COLLECTING_LIGHT);retiredCompoundMetadata.setCount(input.getCount());source.setItem(retiredCompoundMetadata);finishSource(source,retiredCompoundMetadata);}
   else{input.remove(AllDataComponents.CHROMATIC_COMPOUND_COLLECTING_LIGHT);finishSource(source,input);}
  }
  drops.emit(level,receipt);
 }
 private static boolean actualRemaining(ItemEntity source,ItemStack stack){return !source.isRemoved()&&source.getItem()==stack&&source.level().getEntity(source.getUUID())==source;}
 private LegacyConversions(){}
}
