package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.funnel.*;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.belt.transport.*;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;

public class FunnelGameTests {
    private static final BlockPos POS=new BlockPos(3,3,3);
    private static FunnelBlockEntity normal(GameTestHelper h,boolean brass,boolean extracting){h.setBlock(POS,(brass?AllBlocks.BRASS_FUNNEL:AllBlocks.ANDESITE_FUNNEL).defaultBlockState().setValue(FunnelBlock.FACING,Direction.NORTH).setValue(FunnelBlock.EXTRACTING,extracting));return h.getBlockEntity(POS,FunnelBlockEntity.class);}
    private static ChestBlockEntity chest(GameTestHelper h,BlockPos pos){h.setBlock(pos,Blocks.CHEST);return h.getBlockEntity(pos,ChestBlockEntity.class);}
    private static void tick(FunnelBlockEntity funnel){FunnelBlockEntity.tick(funnel.getLevel(),funnel.getBlockPos(),funnel.getBlockState(),funnel);}
    private static long count(Storage<ItemVariant> storage,Item item){try(var tx=Transaction.openOuter()){return storage.extract(ItemVariant.of(item),10000,tx);}}
    private static Storage<ItemVariant> storage(GameTestHelper h,BlockPos pos){var at=h.absolutePos(pos);return FunnelTransfer.find(h.getLevel(),at,Direction.UP);}
    private static FunnelBlockEntity surface(GameTestHelper h,boolean brass,boolean push){h.setBlock(POS.below(),AllBlocks.DEPOT);h.setBlock(POS,(brass?AllBlocks.BRASS_BELT_FUNNEL:AllBlocks.ANDESITE_BELT_FUNNEL).defaultBlockState().setValue(BeltFunnelBlock.HORIZONTAL_FACING,Direction.NORTH).setValue(BeltFunnelBlock.SHAPE,push?BeltFunnelBlock.Shape.PUSHING:BeltFunnelBlock.Shape.PULLING));return h.getBlockEntity(POS,FunnelBlockEntity.class);}
    @GameTest public void collectingUsesPredicateButIgnoresExtractionCountAndRollsBackSimulation(GameTestHelper h){
        var chest=chest(h,POS.south());var funnel=normal(h,true,false);funnel.filtering.setFilter(new ItemStack(Items.APPLE));funnel.filtering.setAmount(16,false);
        h.assertValueEqual(funnel.insert(new ItemStack(Items.APPLE,7),true).getCount(),0,"Simulation accepts seven");h.assertTrue(chest.isEmpty(),"Simulation never commits");
        h.assertValueEqual(funnel.insert(new ItemStack(Items.CARROT,7),false).getCount(),7,"Predicate rejects carrot");h.assertTrue(funnel.insert(new ItemStack(Items.APPLE,7),false).isEmpty(),"Collecting ignores exact sixteen");h.assertValueEqual(count(storage(h,POS.south()),Items.APPLE),7L,"Inserted once");h.succeed();
    }
    @GameTest public void nestedAbortPreservesBothInventoriesCooldownAndFlap(GameTestHelper h){
        chest(h,POS.south());var funnel=normal(h,true,false);float flap=funnel.getFlap(1);
        try(var outer=Transaction.openOuter()){try(var inner=outer.openNested()){h.assertValueEqual(funnel.insert(ItemVariant.of(Items.APPLE),8,true,inner),8L,"Nested transfer accepted");inner.commit();}}
        h.assertValueEqual(count(storage(h,POS.south()),Items.APPLE),0L,"Outer abort restores destination");h.assertValueEqual(funnel.getFlap(1),flap,"Aborted transfer has no animation");h.assertValueEqual(funnel.extractionCooldown,0,"No aborted cooldown");h.succeed();
    }
    @GameTest public void exactExtractionCombinesSlotsAndWorldOverflowBlocksNextBatch(GameTestHelper h){
        var chest=chest(h,POS.south());chest.setItem(0,new ItemStack(Items.APPLE,3));chest.setItem(1,new ItemStack(Items.APPLE,4));var funnel=normal(h,true,true);funnel.filtering.setAmount(8,false);tick(funnel);h.assertValueEqual(count(storage(h,POS.south()),Items.APPLE),7L,"Insufficient exact batch retained");chest.setItem(2,new ItemStack(Items.APPLE,9));tick(funnel);h.assertValueEqual(count(storage(h,POS.south()),Items.APPLE),8L,"Eight extracted across slots");for(int i=0;i<12;i++)tick(funnel);h.assertValueEqual(count(storage(h,POS.south()),Items.APPLE),8L,"Entity prevents accumulation");var entities=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(POS)).expandTowards(0,-1,0));h.assertValueEqual(entities.size(),1,"One world output");h.assertValueEqual(entities.getFirst().getItem().getCount(),8,"Exact output size");h.succeed();
    }
    @GameTest public void andesiteExtractsOneAndBrassUpToUsesConfiguredLimit(GameTestHelper h){
        var chest=chest(h,POS.south());chest.setItem(0,new ItemStack(Items.APPLE,32));var funnel=surface(h,false,true);tick(funnel);h.assertValueEqual(count(storage(h,POS.south()),Items.APPLE),31L,"Andesite batch one");h.assertValueEqual(h.getBlockEntity(POS.below(),DepotBlockEntity.class).getHeldItem().getCount(),1,"Surface receives one");h.succeed();
    }
    @GameTest public void fullDestinationRollsBackSourceAndLaterVariantCanFit(GameTestHelper h){
        var source=chest(h,POS.south());source.setItem(0,new ItemStack(Items.APPLE,20));source.setItem(1,new ItemStack(Items.CARROT,20));var destination=chest(h,POS.north());for(int slot=0;slot<destination.getContainerSize();slot++)destination.setItem(slot,new ItemStack(Items.STONE,64));destination.setItem(0,new ItemStack(Items.CARROT,60));
        var from=storage(h,POS.south());var to=storage(h,POS.north());
        try(var tx=Transaction.openOuter()){h.assertTrue(FunnelTransfer.move(from,to,stack->true,8,true,true,tx).isEmpty(),"Four free carrot spaces cannot accept an exact eight-item batch");tx.commit();}
        h.assertValueEqual(count(from,Items.APPLE),20L,"Blocked first variant retained");h.assertValueEqual(count(from,Items.CARROT),20L,"Failed partial insertion restores source");h.assertValueEqual(count(to,Items.CARROT),60L,"Failed partial insertion restores destination");
        try(var tx=Transaction.openOuter()){h.assertValueEqual(to.extract(ItemVariant.of(Items.CARROT),8,tx),8L,"Free room for a full batch");tx.commit();}
        try(var tx=Transaction.openOuter()){var moved=FunnelTransfer.move(from,to,stack->true,8,true,true,tx);h.assertTrue(moved.is(Items.CARROT)&&moved.getCount()==8,"Blocked apple cannot hide the later fitting carrot batch");}
        h.assertValueEqual(count(from,Items.CARROT),20L,"Outer abort restores the full staged source batch");h.assertValueEqual(count(to,Items.CARROT),52L,"Outer abort restores the freed destination");
        try(var tx=Transaction.openOuter()){var moved=FunnelTransfer.move(from,to,stack->true,8,true,true,tx);h.assertTrue(moved.is(Items.CARROT)&&moved.getCount()==8,"Exact later batch commits");tx.commit();}
        h.assertValueEqual(count(from,Items.APPLE),20L,"Blocked first variant retained");h.assertValueEqual(count(from,Items.CARROT),12L,"Next fitting variant selected");h.assertValueEqual(count(to,Items.CARROT),60L,"Whole batch inserted");h.assertValueEqual(count(to,Items.STONE),(destination.getContainerSize()-1)*64L,"Unrelated destination stock conserved");h.succeed();
    }
    @GameTest public void occupiedOrdinaryDepotDoesNotMergeAndFunnelRetriesAfterItIsEmptied(GameTestHelper h){
        var source=chest(h,POS.south());source.setItem(0,new ItemStack(Items.APPLE,20));source.setItem(1,new ItemStack(Items.CARROT,20));var funnel=surface(h,true,true);funnel.filtering.setAmount(8,false);var depot=h.getBlockEntity(POS.below(),DepotBlockEntity.class);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(depot.inventory.automation.insert(ItemVariant.of(Items.CARROT),60,tx),60L,"Empty ordinary depot receives its one held stack");tx.commit();}
        tick(funnel);h.assertValueEqual(count(storage(h,POS.south()),Items.APPLE),20L,"Occupied depot retains source apples");h.assertValueEqual(count(storage(h,POS.south()),Items.CARROT),20L,"Occupied depot retains source carrots");h.assertValueEqual(funnel.extractionCooldown,0,"Failed insertion has no cooldown");
        try(var tx=Transaction.openOuter()){h.assertValueEqual(depot.inventory.automation.extract(ItemVariant.of(Items.CARROT),8,tx),8L,"Remove eight from the held stack");tx.commit();}
        tick(funnel);h.assertValueEqual(depot.getHeldItem().getCount(),52,"Source ordinary depot still refuses merging despite twelve free spaces");h.assertTrue(depot.depotBehaviour.getIncomingItems().isEmpty(),"No second incoming owner bypasses non-merging policy");h.assertValueEqual(count(storage(h,POS.south()),Items.CARROT),20L,"Compatible variant also remains source-owned");h.assertValueEqual(funnel.extractionCooldown,0,"Non-merging refusal has no cooldown");
        try(var tx=Transaction.openOuter()){h.assertValueEqual(depot.inventory.automation.extract(ItemVariant.of(Items.CARROT),52,tx),52L,"Empty the actual held owner");tx.commit();}
        tick(funnel);h.assertTrue(depot.getHeldItem().is(Items.APPLE)&&depot.getHeldItem().getCount()==8,"Empty depot admits exactly one configured batch");h.assertValueEqual(count(storage(h,POS.south()),Items.APPLE),12L,"Accepted batch paid once");h.assertValueEqual(count(storage(h,POS.south()),Items.CARROT),20L,"Other variant untouched");h.assertValueEqual(funnel.extractionCooldown,8,"Committed batch starts source cooldown");h.succeed();
    }
    @GameTest public void poweredFunnelStopsCollectionAndResetsExtractionCooldown(GameTestHelper h){
        chest(h,POS.south());var funnel=normal(h,true,false);funnel.extractionCooldown=6;h.setBlock(POS,funnel.getBlockState().setValue(AbstractFunnelBlock.POWERED,true));tick(funnel);h.assertValueEqual(funnel.extractionCooldown,0,"Power resets cooldown");h.assertValueEqual(funnel.insert(new ItemStack(Items.APPLE,7),false).getCount(),7,"Power denies collection");h.succeed();
    }
    @GameTest public void normalAndBeltConversionPreserveInstalledFilterAmountAndCooldown(GameTestHelper h){
        var funnel=normal(h,true,true);var filter=FilterGameTests.filter(false,false,new ItemStack(Items.APPLE));funnel.filtering.setFilter(filter);funnel.filtering.setAmount(17,false);funnel.extractionCooldown=5;
        h.setBlock(POS.below(),AllBlocks.DEPOT);var converted=h.getBlockEntity(POS,FunnelBlockEntity.class);h.assertTrue(converted.getBlockState().getBlock() instanceof BeltFunnelBlock,"Support creates belt funnel");h.assertValueEqual(converted.filtering.count,17,"Batch count survived conversion");h.assertTrue(ItemStack.isSameItemSameComponents(converted.filtering.getFilter(),filter),"Installed filter survived conversion");h.assertValueEqual(converted.extractionCooldown,5,"Cooldown survived conversion");
        h.setBlock(POS.below(),Blocks.AIR);var restored=h.getBlockEntity(POS,FunnelBlockEntity.class);h.assertTrue(restored.getBlockState().getBlock() instanceof FunnelBlock,"Removing support restores normal funnel");h.assertValueEqual(restored.filtering.count,17,"Count survived return");h.assertTrue(ItemStack.isSameItemSameComponents(restored.filtering.getFilter(),filter),"Filter survived return");h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(POS)).inflate(1)).isEmpty(),"No conversion drops");h.succeed();
    }
    @GameTest public void configuredFilterConsumesOnceRefundsAndDropsOnlyOnDestruction(GameTestHelper h){
        var funnel=normal(h,true,true);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,FilterGameTests.filter(false,false,new ItemStack(Items.APPLE)));funnel.filtering.applyPlayerSample(player,InteractionHand.MAIN_HAND);h.assertTrue(player.getMainHandItem().isEmpty(),"Real filter consumed");funnel.setRemoved();funnel.clearRemoved();h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(POS)).inflate(1)).isEmpty(),"Unload never drops filter");h.setBlock(POS,Blocks.AIR);var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(POS)).inflate(1));h.assertValueEqual(drops.stream().filter(entity->entity.getItem().is(AllItems.FILTER)).mapToInt(entity->entity.getItem().getCount()).sum(),1,"Destruction returns exactly one filter");h.succeed();
    }
    @GameTest public void depotPullingPrefersProcessedOutputAndIgnoresCount(GameTestHelper h){
        chest(h,POS.south());var funnel=surface(h,true,false);funnel.filtering.setAmount(32,false);var depot=h.getBlockEntity(POS.below(),DepotBlockEntity.class);
        try(var tx=Transaction.openOuter()){depot.inventory.inputs.getFirst().insert(ItemVariant.of(Items.APPLE),7,tx);depot.inventory.outputs.getFirst().insert(ItemVariant.of(Items.CARROT),3,tx);tx.commit();}
        tick(funnel);h.assertValueEqual(count(storage(h,POS.south()),Items.CARROT),3L,"Output pulled without thirty-two requirement");h.assertValueEqual(depot.getHeldItem().getCount(),7,"Input waits for outputs");tick(funnel);h.assertValueEqual(count(storage(h,POS.south()),Items.APPLE),7L,"Input can be pulled next tick");h.succeed();
    }
    @GameTest public void amountPacketsRejectInvalidSidesRangesAndRemotePlayers(GameTestHelper h){
        var funnel=normal(h,true,true);var player=h.makeMockPlayer(GameType.SURVIVAL);var pos=h.absolutePos(POS);player.setPos(Vec3.atCenterOf(pos).add(0,0,-2));
        h.assertTrue(com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringSettings.apply(h.getLevel(),player,new com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringSettings.SetFilter(pos,Direction.NORTH,InteractionHand.MAIN_HAND,17,false)),"Valid exact amount applied");
        h.assertValueEqual(funnel.filtering.count,17,"Exact count stored");
        h.assertFalse(com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringSettings.apply(h.getLevel(),player,new com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringSettings.SetFilter(pos,Direction.NORTH,InteractionHand.MAIN_HAND,65,false)),"Oversized count rejected");
        h.assertFalse(com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringSettings.apply(h.getLevel(),player,new com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringSettings.SetFilter(pos,Direction.SOUTH,InteractionHand.MAIN_HAND,5,true)),"Hidden side rejected");
        player.setPos(Vec3.atCenterOf(pos).add(0,0,100));h.assertFalse(com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringSettings.apply(h.getLevel(),player,new com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringSettings.SetFilter(pos,Direction.NORTH,InteractionHand.MAIN_HAND,5,true)),"Remote packet rejected");h.succeed();
    }
    @GameTest public void brassWorldExtractionRespectsUpToLimitAndSavesThroughNativeCodec(GameTestHelper h){
        var source=chest(h,POS.south());source.setItem(0,new ItemStack(Items.APPLE,20));var funnel=normal(h,true,true);funnel.filtering.setFilter(new ItemStack(Items.APPLE));funnel.filtering.setAmount(5,true);tick(funnel);h.assertValueEqual(count(storage(h,POS.south()),Items.APPLE),15L,"Brass up-to extraction capped at five");
        var tag=funnel.saveWithFullMetadata(h.getLevel().registryAccess());var restored=(FunnelBlockEntity)BlockEntity.loadStatic(funnel.getBlockPos(),funnel.getBlockState(),tag,h.getLevel().registryAccess());h.assertValueEqual(restored.filtering.count,5,"Amount restored");h.assertTrue(restored.filtering.upTo&&restored.filtering.getFilter().is(Items.APPLE),"Predicate and mode restored");h.assertValueEqual(restored.extractionCooldown,8,"Source cooldown tag restored");h.succeed();
    }
    @GameTest(maxTicks=65) public void drainFinishesEmptyingBeforePullingFunnelExportsItsContainer(GameTestHelper h){
        var base=POS.below();h.setBlock(base,AllBlocks.ITEM_DRAIN);chest(h,POS.east());
        h.setBlock(POS,AllBlocks.BRASS_BELT_FUNNEL.defaultBlockState().setValue(BeltFunnelBlock.HORIZONTAL_FACING,Direction.WEST).setValue(BeltFunnelBlock.SHAPE,BeltFunnelBlock.Shape.PULLING));var funnel=h.getBlockEntity(POS,FunnelBlockEntity.class);funnel.filtering.setAmount(32,false);var drain=h.getBlockEntity(base,com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity.class);
        try(var tx=Transaction.openOuter()){drain.storage(Direction.WEST).insert(ItemVariant.of(Items.WATER_BUCKET),1,tx);tx.commit();}
        h.runAfterDelay(12,()->{h.assertTrue(drain.getHeldItemStack().is(Items.WATER_BUCKET),"Funnel cannot pull an unprocessed bucket");h.assertValueEqual(count(storage(h,POS.east()),Items.WATER_BUCKET),0L,"Full bucket retained");});
        h.succeedWhen(()->{h.assertValueEqual(drain.tank.getAmount(),net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET,"One bucket emptied");h.assertValueEqual(count(storage(h,POS.east()),Items.BUCKET),1L,"Container pulled after processing without count requirement");});
    }
    @GameTest public void stoppedBeltRejectsFunnelExtractionAndKeepsSource(GameTestHelper h){
        var belt=belt(h);belt.setNetworkState(0,0,false,new com.simibubi.create.content.kinetics.KineticGraph.Stress(0,0,false),java.util.Map.of());var source=chest(h,POS.south());source.setItem(0,new ItemStack(Items.APPLE,12));h.setBlock(POS,AllBlocks.BRASS_BELT_FUNNEL.defaultBlockState().setValue(BeltFunnelBlock.HORIZONTAL_FACING,Direction.NORTH).setValue(BeltFunnelBlock.SHAPE,BeltFunnelBlock.Shape.PUSHING));var funnel=h.getBlockEntity(POS,FunnelBlockEntity.class);tick(funnel);h.assertValueEqual(count(storage(h,POS.south()),Items.APPLE),12L,"Stopped belt retains source inventory");h.assertTrue(belt.getInventory().getTransportedItems().isEmpty(),"No cargo inserted onto stopped belt");h.succeed();
    }
    @GameTest public void funnelItemPlacesAgainstContainerWithoutOpeningIt(GameTestHelper h){
        var container=POS.south();chest(h,container);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(h.absolutePos(POS)).add(0,0,-2));player.setYRot(180);player.setXRot(0);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.ANDESITE_FUNNEL,2));var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(container)).add(0,0,-.5),Direction.NORTH,h.absolutePos(container),false);var result=net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(player,h.getLevel(),InteractionHand.MAIN_HAND,hit);h.assertTrue(result.consumesAction(),"Placement intercepts container interaction");h.assertTrue(h.getBlockState(POS).is(AllBlocks.ANDESITE_FUNNEL),"Funnel placed against chest");h.assertValueEqual(player.getMainHandItem().getCount(),1,"One item consumed");h.succeed();
    }
    @GameTest(maxTicks=150) public void upwardSawSupportsFunnelAndOnlyExportsCompletedCuttingOutputs(GameTestHelper h){
        var base=POS.below();h.setBlock(base,AllBlocks.MECHANICAL_SAW.defaultBlockState().setValue(com.simibubi.create.content.kinetics.saw.SawBlock.FACING,Direction.UP));var saw=h.getBlockEntity(base,com.simibubi.create.content.kinetics.saw.SawBlockEntity.class);h.setBlock(base.north(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING,Direction.SOUTH));h.getBlockEntity(base.north(),com.simibubi.create.content.kinetics.base.KineticBlockEntity.class).setGeneratedSpeed(256);var destination=chest(h,POS.east());h.setBlock(POS,AllBlocks.BRASS_BELT_FUNNEL.defaultBlockState().setValue(BeltFunnelBlock.HORIZONTAL_FACING,Direction.WEST).setValue(BeltFunnelBlock.SHAPE,BeltFunnelBlock.Shape.PULLING));h.assertTrue(BeltFunnelBlock.isOnValidBelt(h.getBlockState(POS),h.getLevel(),h.absolutePos(POS)),"Upward saw supports belt funnels");
        // The source saw cycles all matching cutting/stonecutting recipes unless its output is filtered.
        saw.setFilter(new ItemStack(AllBlocks.SHAFT));try(var tx=Transaction.openOuter()){h.assertValueEqual(saw.inventory.insert(ItemVariant.of(AllItems.ANDESITE_ALLOY),1,tx),1L,"One real alloy starts the selected cutting batch");tx.commit();}
        h.assertValueEqual(saw.inventory.selectedRecipe(),Create.asResource("cutting/andesite_alloy"),"Genuine saw output filter selects the six-shaft cutting recipe");h.assertValueEqual(saw.inventory.recipeDuration,200f,"Real cutting duration is retained");
        h.runAfterDelay(2,()->h.assertValueEqual(count(storage(h,POS.east()),AllItems.ANDESITE_ALLOY),0L,"Raw input cannot be pulled from processing saw"));
        h.runAfterDelay(5,()->{h.assertValueEqual(saw.getSpeed(),256f,"Native motor and saw graph supply the actual processing RPM");h.assertTrue(saw.inventory.remainingTime>5&&saw.inventory.remainingTime<200&&!saw.inventory.appliedRecipe&&saw.inventory.input().is(AllItems.ANDESITE_ALLOY),"Real native ticks advance the paid input without early recipe application");});
        h.onEachTick(()->{h.assertValueEqual(count(storage(h,POS.east()),AllItems.ANDESITE_ALLOY),0L,"Overhead funnel never exports raw alloy");if(saw.inventory.remainingTime>0)h.assertValueEqual(count(storage(h,POS.east()),AllBlocks.SHAFT.asItem()),0L,"Overhead funnel waits for completion of the actual cutting cycle");});
        h.succeedWhen(()->{h.assertValueEqual(count(storage(h,POS.east()),AllBlocks.SHAFT.asItem()),6L,"Six crafted shafts exported through overhead funnel");h.assertTrue(saw.inventory.isEmpty(),"Saw releases completed batch once");int delivered=0;for(int slot=0;slot<destination.getContainerSize();slot++)delivered+=destination.getItem(slot).getCount();h.assertValueEqual(delivered,6,"Destination owns exactly the six completed shafts and no alternate recipe output");});
    }
    private static BeltBlockEntity belt(GameTestHelper h){
        var start=new BlockPos(1,2,3);var end=start.east(5);h.setBlock(start,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Z));h.setBlock(end,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Z));h.assertTrue(BeltConnectorItem.createBelts(h.getLevel(),h.absolutePos(start),h.absolutePos(end)),"Belt created");var belt=h.getBlockEntity(start,BeltBlockEntity.class);belt.setNetworkState(-64,-64,false,new com.simibubi.create.content.kinetics.KineticGraph.Stress(10000,0,false),java.util.Map.of());return belt;
    }
    private static TransportedItemStack cargo(BeltBlockEntity belt,int count){try(var tx=Transaction.openOuter()){belt.getInventory().segment(2,Direction.UP).insert(ItemVariant.of(Items.APPLE),count,tx);tx.commit();}return belt.getInventory().getTransportedItems().getFirst();}
    @GameTest public void frontFunnelBlocksRejectedCargoAndExactBatchLeavesRemainder(GameTestHelper h){
        var belt=belt(h);chest(h,POS.east());h.setBlock(POS,AllBlocks.BRASS_BELT_FUNNEL.defaultBlockState().setValue(BeltFunnelBlock.HORIZONTAL_FACING,Direction.WEST).setValue(BeltFunnelBlock.SHAPE,BeltFunnelBlock.Shape.RETRACTED));var funnel=h.getBlockEntity(POS,FunnelBlockEntity.class);funnel.filtering.setAmount(8,false);var item=cargo(belt,12);
        float stopped=BeltFunnelInteractionHandler.checkForFunnels(belt,belt.getInventory(),item,2.8f);h.assertValueEqual(stopped,2.5f,"Front stops at centre");h.assertValueEqual(count(storage(h,POS.east()),Items.APPLE),8L,"Exact batch delivered");h.assertValueEqual(belt.getInventory().byId(item.id).stack.getCount(),4,"Original cargo identity retains four");
        h.assertValueEqual(BeltFunnelInteractionHandler.checkForFunnels(belt,belt.getInventory(),belt.getInventory().byId(item.id),2.8f),2.5f,"Insufficient remainder waits");h.succeed();
    }
    @GameTest public void sideFunnelPassesRejectedCargoAndUpToTakesWholeStack(GameTestHelper h){
        var belt=belt(h);chest(h,POS.south());h.setBlock(POS,AllBlocks.BRASS_BELT_FUNNEL.defaultBlockState().setValue(BeltFunnelBlock.HORIZONTAL_FACING,Direction.NORTH).setValue(BeltFunnelBlock.SHAPE,BeltFunnelBlock.Shape.PULLING));var funnel=h.getBlockEntity(POS,FunnelBlockEntity.class);var item=cargo(belt,12);funnel.filtering.setFilter(new ItemStack(Items.CARROT));h.assertValueEqual(BeltFunnelInteractionHandler.checkForFunnels(belt,belt.getInventory(),item,2.8f),2.8f,"Side rejection passes");funnel.filtering.setFilter(ItemStack.EMPTY);funnel.filtering.setAmount(3,true);BeltFunnelInteractionHandler.checkForFunnels(belt,belt.getInventory(),item,2.8f);h.assertValueEqual(count(storage(h,POS.south()),Items.APPLE),12L,"Original up-to belt intake accepts whole cargo");h.assertTrue(belt.getInventory().byId(item.id)==null,"Delivered cargo removed");h.succeed();
    }
    @GameTest(maxTicks=100) public void movingBeltFeedsFrontFunnelAndWaitsUntilRemainderMatchesNewAmount(GameTestHelper h){
        var belt=belt(h);var motorPos=new BlockPos(1,2,2);h.setBlock(motorPos,AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING,Direction.SOUTH));h.getBlockEntity(motorPos,com.simibubi.create.content.kinetics.base.KineticBlockEntity.class).setGeneratedSpeed(-64);chest(h,POS.east());h.setBlock(POS,AllBlocks.BRASS_BELT_FUNNEL.defaultBlockState().setValue(BeltFunnelBlock.HORIZONTAL_FACING,Direction.WEST).setValue(BeltFunnelBlock.SHAPE,BeltFunnelBlock.Shape.RETRACTED));var funnel=h.getBlockEntity(POS,FunnelBlockEntity.class);funnel.filtering.setAmount(8,false);
        try(var tx=Transaction.openOuter()){belt.getInventory().segment(0,Direction.UP).insert(ItemVariant.of(Items.APPLE),12,tx);tx.commit();}
        h.runAfterDelay(35,()->{h.assertValueEqual(count(storage(h,POS.east()),Items.APPLE),8L,"Moving belt delivered eight");var remaining=belt.getInventory().getTransportedItems();h.assertValueEqual(remaining.size(),1,"Remainder stayed on belt");h.assertValueEqual(remaining.getFirst().stack.getCount(),4,"Four waiting for exact setting");h.assertValueEqual(remaining.getFirst().beltPosition,2.5f,"Cargo held at funnel entry");funnel.filtering.setAmount(4,false);});
        h.succeedWhen(()->{h.assertTrue(h.getTick()>35,"Wait for setting change");h.assertValueEqual(count(storage(h,POS.east()),Items.APPLE),12L,"Both exact batches arrived once");h.assertTrue(belt.getInventory().getTransportedItems().isEmpty(),"Cargo released after matching count");});
    }
    @GameTest public void storageMovementRollsBackWhenOuterTransactionAborts(GameTestHelper h){
        var source=chest(h,POS.south());source.setItem(0,new ItemStack(Items.APPLE,20));chest(h,POS.north());try(var tx=Transaction.openOuter()){h.assertValueEqual(FunnelTransfer.move(storage(h,POS.south()),storage(h,POS.north()),stack->true,8,true,true,tx).getCount(),8,"Eight in nested transfer");}h.assertValueEqual(count(storage(h,POS.south()),Items.APPLE),20L,"Source restored");h.assertValueEqual(count(storage(h,POS.north()),Items.APPLE),0L,"Destination restored");h.succeed();
    }
}
