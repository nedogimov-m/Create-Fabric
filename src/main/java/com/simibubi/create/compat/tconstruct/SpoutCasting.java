package com.simibubi.create.compat.tconstruct;

import com.simibubi.create.api.behaviour.spouting.BlockSpoutingBehaviour;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.foundation.fluid.FluidStack;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;

/** Source single-reservoir casting policy, expressed through actual Fabric fluid transactions. */
public enum SpoutCasting implements BlockSpoutingBehaviour {
 INSTANCE;
 private Storage<FluidVariant> target(Level level,BlockPos pos,FluidStack available){
  if(!AllConfigs.server().recipes.allowCastingBySpout.get()||available.isEmpty()||!level.isLoaded(pos)||LoadedLevelAccess.blockEntity(level,pos)==null)return null;
  var storage=FluidStorage.SIDED.find(level,pos,Direction.UP);if(storage==null)return null;
  var views=storage.iterator();if(!views.hasNext())return null;var only=views.next();if(views.hasNext()||!only.isResourceBlank()&&!only.getResource().equals(available.variant()))return null;
  return storage;
 }
 private long quote(Storage<FluidVariant> storage,FluidStack available){
  try(var preview=Transaction.openOuter()){
   // One original millibucket is81 native units; source allows partial fills only from a bucket or more.
   long offered=available.getAmount()<FluidConstants.BUCKET?Math.addExact(available.getAmount(),81):available.getAmount();
   long accepted=storage.insert(available.variant(),offered,preview);
   if(accepted<0||accepted>offered)throw new IllegalStateException("Casting target violated its actual native insertion contract");
   return available.getAmount()<FluidConstants.BUCKET&&accepted>available.getAmount()?0:Math.min(accepted,available.getAmount());
  }
 }
 @Override public long fillBlock(Level level,BlockPos pos,SpoutBlockEntity spout,FluidStack available,boolean simulate){
  var storage=target(level,pos,available);if(storage==null)return 0;long required=quote(storage,available);if(simulate||required==0)return required;
  try(var tx=Transaction.openOuter()){long inserted=storage.insert(available.variant(),required,tx);if(inserted<0||inserted>required)throw new IllegalStateException("Casting target violated its actual native insertion contract");tx.commit();return inserted;}
 }
 /** Production spouts debit their real reservoir and credit the casting target in one transaction. */
 public long transferPaid(Level level,BlockPos pos,SpoutBlockEntity spout){
  if(level.isClientSide()||spout.getLevel()!=level||LoadedLevelAccess.blockEntity(level,spout.getBlockPos())!=spout)return 0;
  var available=spout.tank.getFluid();var storage=target(level,pos,available);if(storage==null)return 0;long required=quote(storage,available);if(required==0)return 0;
  try(var tx=Transaction.openOuter()){
   long paid=spout.tank.extract(available.variant(),required,tx);if(paid!=required)return 0;
   long accepted=storage.insert(available.variant(),paid,tx);if(accepted!=paid)return 0;
   tx.commit();return paid;
  }
 }
}
