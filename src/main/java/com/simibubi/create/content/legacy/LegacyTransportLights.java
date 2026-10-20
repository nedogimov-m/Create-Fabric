package com.simibubi.create.content.legacy;

import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.belt.transport.*;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;

/** Source processing radius over real Depot slots and exact native belt transport identities. */
final class LegacyTransportLights {
 interface Light {
  boolean valid();
  boolean pay(TransactionContext transaction);
 }
 static boolean process(ChromaticCompoundItem item,ItemEntity source,ItemStack input,BlockPos pos){
  var level=(ServerLevel)source.level();var owner=LoadedLevelAccess.blockEntity(level,pos);if(owner==null||owner.isRemoved()||BlockEntityBehaviour.get(owner,TransportedItemStackHandlerBehaviour.TYPE)==null)return false;
  var depot=BlockEntityBehaviour.get(owner,DepotBehaviour.TYPE);
  if(depot!=null){
   var slot=depot.inventory.inputs.getFirst();var variant=slot.getResource();long amount=slot.getAmount();var sample=slot.stack();if(!(sample.getItem() instanceof BlockItem block)||!LegacyLightSources.visibleLight(source,level,source.position(),pos,block.getBlock().defaultBlockState()))return false;
   Light light=new Light(){
    @Override public boolean valid(){return depot.isActiveWorldOwner()&&slot.getAmount()==amount&&slot.getResource().equals(variant)&&amount>0;}
    @Override public boolean pay(TransactionContext transaction){return valid()&&slot.extract(variant,1,transaction)==1;}
   };
   return LegacyConversions.emitCharged(item,source,input,light);
  }
  if(!(owner instanceof BeltBlockEntity belt))return false;var controller=belt.getControllerBE();if(controller==null||!controller.chainLoaded())return false;var inventory=controller.getInventory();
  for(var transport:inventory.getTransportedItems()){
   if(Math.abs(transport.beltPosition-belt.index-.5f)>=.51f||!(transport.stack.getItem() instanceof BlockItem block))continue;BlockState state=block.getBlock().defaultBlockState();if(!LegacyLightSources.visibleLight(source,level,source.position(),pos,state))continue;
   var variant=ItemVariant.of(transport.stack);int count=transport.stack.getCount();
   Light light=new Light(){
    @Override public boolean valid(){return !belt.isRemoved()&&!controller.isRemoved()&&LoadedLevelAccess.blockEntity(level,pos)==belt&&belt.getControllerBE()==controller&&controller.chainLoaded()&&inventory.byId(transport.id)==transport&&Math.abs(transport.beltPosition-belt.index-.5f)<.51f&&transport.stack.getCount()==count&&variant.matches(transport.stack);}
    @Override public boolean pay(TransactionContext transaction){return valid()&&inventory.extractLegacyLight(transport,variant,count,transaction);}
   };
   if(LegacyConversions.emitCharged(item,source,input,light))return true;
  }
  return false;
 }
 private LegacyTransportLights(){}
}
