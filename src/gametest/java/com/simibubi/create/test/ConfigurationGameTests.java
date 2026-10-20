package com.simibubi.create.test;

import com.google.gson.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.infrastructure.config.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;

/** Real files, actual native configuration consumers and persisted item/fluid ownership. Runtime UNRUN. */
public final class ConfigurationGameTests {
 private static final BlockPos P=new BlockPos(2,2,2);
 private static long insert(Storage<ItemVariant> storage,long amount){try(var tx=Transaction.openOuter()){long moved=storage.insert(ItemVariant.of(Items.APPLE),amount,tx);tx.commit();return moved;}}
 private static long items(Storage<ItemVariant> storage){long sum=0;for(var view:storage)sum+=view.getAmount();return sum;}
 private static long fill(FluidTankBlockEntity tank,long amount){try(var tx=Transaction.openOuter()){long moved=tank.storage().insert(FluidVariant.of(Fluids.WATER),amount,tx);tx.commit();return moved;}}
 private static void tickTanks(FluidTankBlockEntity... tanks){for(int round=0;round<5;round++)for(var tank:tanks)FluidTankBlockEntity.tick(tank.getLevel(),tank.getBlockPos(),tank.getBlockState(),tank);}
 @GameTest public void realScopeFilePreservesUnknownValuesAndRejectsInvalidUpdateAtomically(GameTestHelper h)throws Exception{
  var directory=Files.createTempDirectory("create-full-config-");var file=directory.resolve("server.json");
  try{
   Files.writeString(file,"{\"logistics\":{\"vaultCapacity\":37},\"foreign\":{\"keep\":[1,2,3]}}");
   var config=new CServer();var store=config.store();store.reload(file);
   h.assertValueEqual(config.logistics.vaultCapacity.get(),37,"Actual saved source field loaded");
   config.logistics.vaultCapacity.set(41);var document=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
   h.assertTrue(document.getAsJsonObject("foreign").getAsJsonArray("keep").size()==3,"Unknown real JSON keys survive write");
   var previous=Files.readAllBytes(file);boolean rejected=false;
   try{config.logistics.vaultCapacity.set(2049);}catch(IllegalArgumentException expected){rejected=true;}
   h.assertTrue(rejected&&config.logistics.vaultCapacity.get()==41&&Arrays.equals(previous,Files.readAllBytes(file)),"Invalid source bound changes neither file nor published snapshot");
   var restored=new CServer();restored.store().reload(file);h.assertValueEqual(restored.logistics.vaultCapacity.get(),41,"Source setting survives actual file reload");
  }finally{Files.deleteIfExists(file);Files.deleteIfExists(directory);}h.succeed();
 }
 @GameTest public void separateWorldStoresAndRemoteSnapshotCannotReplaceAuthoritativeValues(GameTestHelper h)throws Exception{
  var directory=Files.createTempDirectory("create-config-world-scopes-");var first=directory.resolve("world-one.json");var second=directory.resolve("world-two.json");
  try{
   var one=new CServer();var two=new CServer();one.store().reload(first);two.store().reload(second);one.logistics.vaultCapacity.set(35);two.logistics.vaultCapacity.set(12);
   var remote=one.store().serverDocument();remote.getAsJsonObject("logistics").addProperty("vaultCapacity",7);remote.getAsJsonObject("trains").getAsJsonObject("trainStats").addProperty("trainTopSpeed",.1234567890123d);one.store().installRemote(remote);one.store().installRemoteValues(Map.of(one.trains.trainTopSpeed.key(),.12345679f));
   h.assertTrue(one.trains.trainTopSpeed.get()==.1234567890123d,"Legacy float mirrors cannot round the complete typed remote server snapshot");
   h.assertValueEqual(one.logistics.vaultCapacity.get(),7,"Client reads actual installed remote snapshot");h.assertValueEqual(one.logistics.vaultCapacity.getServer(),35,"Authoritative value remains world-owned");h.assertValueEqual(two.logistics.vaultCapacity.getServer(),12,"Another world's backing scope stays independent");
   one.store().clearRemote();h.assertValueEqual(one.logistics.vaultCapacity.get(),35,"Disconnect restores local authoritative value");
  }finally{Files.deleteIfExists(first);Files.deleteIfExists(second);Files.deleteIfExists(directory);}h.succeed();
 }
 @GameTest public void vaultCapacityReductionRetainsExtendedSlotOwnersAcrossNativeSave(GameTestHelper h){
  var setting=AllConfigs.server().logistics.vaultCapacity;int previous=setting.getServer();
  try{
   setting.set(37);h.setBlock(P,AllBlocks.ITEM_VAULT);var vault=h.getBlockEntity(P,ItemVaultBlockEntity.class);var original=vault.inventory.inputs.getFirst();
   h.assertValueEqual(insert(vault.getInventoryOfBlock(),37*64L),37*64L,"Actual native vault accepts configured thirty-seven slots");
   setting.set(1);h.assertValueEqual(insert(vault.getInventoryOfBlock(),1),0L,"Filled configured active slot refuses new insertion after reduction");h.assertTrue(original==vault.inventory.inputs.getFirst(),"Original slot identity remains canonical");
   var tag=vault.saveWithFullMetadata(h.getLevel().registryAccess());var restored=(ItemVaultBlockEntity)BlockEntity.loadStatic(vault.getBlockPos(),vault.getBlockState(),tag,h.getLevel().registryAccess());
   h.assertValueEqual(items(restored.getInventoryOfBlock()),37*64L,"All inactive extended contents survive actual native save/load");
   try(var tx=Transaction.openOuter()){h.assertValueEqual(restored.getInventoryOfBlock().extract(ItemVariant.of(Items.APPLE),37*64L,tx),37*64L,"Every retained slot remains extractable");}
   h.assertValueEqual(items(restored.getInventoryOfBlock()),37*64L,"Aborted extraction retains the single restored owner");
   try(var tx=Transaction.openOuter()){restored.getInventoryOfBlock().extract(ItemVariant.of(Items.APPLE),37*64L,tx);tx.commit();}h.assertValueEqual(items(restored.getInventoryOfBlock()),0L,"Committed extraction retires all restored stock");
  }finally{setting.set(previous);}h.succeed();
 }
 @GameTest public void loweredTankCapacityBlocksRefillAndPreservesNativeSaveContents(GameTestHelper h){
  var setting=AllConfigs.server().fluids.fluidTankCapacity;int previous=setting.getServer();long bucket=FluidConstants.BUCKET;
  try{
   setting.set(8);h.setBlock(P,AllBlocks.FLUID_TANK);var tank=h.getBlockEntity(P,FluidTankBlockEntity.class);h.assertValueEqual(fill(tank,8*bucket),8*bucket,"Actual source eight-bucket tank filled");
   setting.set(1);h.assertValueEqual(fill(tank,1),0L,"Configuration reduction creates no insertion headroom");
   try(var tx=Transaction.openOuter()){h.assertValueEqual(tank.storage().extract(FluidVariant.of(Fluids.WATER),bucket,tx),bucket,"Existing fluid remains extractable");tx.commit();}
   h.assertValueEqual(fill(tank,1),0L,"Capacity floor follows extraction immediately, preventing a refill above configured capacity");
   var tag=tank.saveWithFullMetadata(h.getLevel().registryAccess());var restored=(FluidTankBlockEntity)BlockEntity.loadStatic(tank.getBlockPos(),tank.getBlockState(),tag,h.getLevel().registryAccess());h.assertValueEqual(restored.getTankInventory().getFluidAmount(),7*bucket,"Actual decoder retains seven already-owned buckets under one-bucket configuration");
  }finally{setting.set(previous);}h.succeed();
 }
 @GameTest public void overfullNativeTankMergeAndSplitDoNotDuplicateOrDiscardConfigurationOverflow(GameTestHelper h){
  var setting=AllConfigs.server().fluids.fluidTankCapacity;int previous=setting.getServer();long bucket=FluidConstants.BUCKET;
  try{
   setting.set(8);h.setBlock(P,AllBlocks.FLUID_TANK);h.setBlock(P.above(2),AllBlocks.FLUID_TANK);var first=h.getBlockEntity(P,FluidTankBlockEntity.class);var last=h.getBlockEntity(P.above(2),FluidTankBlockEntity.class);
   fill(first,6*bucket);fill(last,6*bucket);setting.set(1);h.setBlock(P.above(),AllBlocks.FLUID_TANK);var middle=h.getBlockEntity(P.above(),FluidTankBlockEntity.class);tickTanks(first,middle,last);
   h.assertValueEqual(first.getHeight(),3,"Actual connectivity forms three-block tank");h.assertValueEqual(first.getTankInventory().getFluidAmount()+middle.getTankInventory().getFluidAmount()+last.getTankInventory().getFluidAmount(),12*bucket,"Merge transfers all original fluid to actual controller once");
   com.simibubi.create.api.connectivity.ConnectivityHandler.splitMulti(first);
   h.assertValueEqual(first.getTankInventory().getFluidAmount()+middle.getTankInventory().getFluidAmount()+last.getTankInventory().getFluidAmount(),12*bucket,"Splitting all surviving blocks retains exactly the same already-owned fluid");
  }finally{setting.set(previous);}h.succeed();
 }
 @GameTest public void stressDisableRestartsActualKineticsAndKeepsRealDiagnosticTotals(GameTestHelper h){
  var config=AllConfigs.server().kinetics;var store=AllConfigs.store(ConfigScope.SERVER);var key="kinetics.stressValues.v2.capacity.creative_motor";var previousCapacity=store.definition(key).getServer();boolean previousDisable=config.disableStress.getServer();
  try{
   store.importValues(Map.of(key,0d),true);config.disableStress.set(false);h.setBlock(P,AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.UP));h.setBlock(P.above(),AllBlocks.MILLSTONE);var mill=h.getBlockEntity(P.above(),KineticBlockEntity.class);KineticNetworks.track(h.getLevel(),h.getBlockEntity(P,KineticBlockEntity.class));KineticNetworks.track(h.getLevel(),mill);KineticNetworks.tick(h.getLevel());
   h.assertTrue(mill.isOverstressed()&&mill.getSpeed()==0&&mill.getStressImpact()>0,"Actual loaded graph stops under paid stress with zero generation capacity");double impact=mill.getStressImpact();
   config.disableStress.set(true);KineticNetworks.tick(h.getLevel());h.assertTrue(!mill.isOverstressed()&&mill.getSpeed()!=0&&mill.getStressImpact()==impact,"Configuration bypasses native stoppage while retaining diagnostic load");
  }finally{store.importValues(Map.of(key,previousCapacity),true);config.disableStress.set(previousDisable);}h.succeed();
 }
 @GameTest public void actualAxeTransformerProducesOptionalDisplayRecipesWithoutWorldEdits(GameTestHelper h){
  var setting=AllConfigs.server().recipes.displayLogStrippingRecipes;boolean previous=setting.getServer();
  try{
   setting.set(true);var recipes=com.simibubi.create.foundation.data.recipe.LogStrippingFakeRecipes.createRecipes(h.getLevel().registryAccess());
   h.assertTrue(recipes.stream().anyMatch(holder->holder.id().identifier().getPath().equals("stripped_oak_log_via_vanilla_stripping")&&holder.value().getProcessedItem().test(new net.minecraft.world.item.ItemStack(Items.OAK_LOG))&&holder.value().results().getFirst().item()==Items.STRIPPED_OAK_LOG),"Real26.3 axe transformer yields the source display conversion");
   setting.set(false);h.assertTrue(com.simibubi.create.foundation.data.recipe.LogStrippingFakeRecipes.createRecipes(h.getLevel().registryAccess()).isEmpty(),"Source flag removes generated display recipes");
  }finally{setting.set(previous);}h.succeed();
 }
 @GameTest public void nativeCastingRejectsPartialSmallFillAndCommitsExactPaidTransfer(GameTestHelper h){
  var setting=AllConfigs.server().recipes.allowCastingBySpout;boolean previous=setting.getServer();long millibucket=81;
  try{
   setting.set(true);h.setBlock(P,AllBlocks.SPOUT);h.setBlock(P.below(2),AllBlocks.SPOUT);var source=h.getBlockEntity(P,com.simibubi.create.content.fluids.spout.SpoutBlockEntity.class);var destination=h.getBlockEntity(P.below(2),com.simibubi.create.content.fluids.spout.SpoutBlockEntity.class);var water=FluidVariant.of(Fluids.WATER);
   source.tank.fill(new com.simibubi.create.foundation.fluid.FluidStack(water,600*millibucket));
   h.assertValueEqual(com.simibubi.create.compat.tconstruct.SpoutCasting.INSTANCE.transferPaid(h.getLevel(),destination.getBlockPos(),source),0L,"Source small-fill policy refuses a partly filled real single reservoir");h.assertValueEqual(source.tank.getAmount(),600*millibucket,"Rejected preview does not debit actual source");h.assertValueEqual(destination.tank.getAmount(),0L,"Rejected preview does not credit actual target");
   destination.tank.fill(new com.simibubi.create.foundation.fluid.FluidStack(water,400*millibucket));setting.set(false);h.assertValueEqual(com.simibubi.create.compat.tconstruct.SpoutCasting.INSTANCE.transferPaid(h.getLevel(),destination.getBlockPos(),source),0L,"Disabled source option blocks actual transfer");setting.set(true);
   h.assertValueEqual(com.simibubi.create.compat.tconstruct.SpoutCasting.INSTANCE.transferPaid(h.getLevel(),destination.getBlockPos(),source),600*millibucket,"Exact headroom accepts the fully paid available batch");h.assertValueEqual(source.tank.getAmount(),0L,"Successful commit retires real source stock");h.assertValueEqual(destination.tank.getAmount(),FluidConstants.BUCKET,"Successful commit credits one target bucket exactly");
  }finally{setting.set(previous);}h.succeed();
 }

}
