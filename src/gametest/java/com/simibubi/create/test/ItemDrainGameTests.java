package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.drain.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;

public class ItemDrainGameTests {
    private static final BlockPos BASE=new BlockPos(3,2,3),OUT=BASE.east();
    private static final long B=FluidConstants.BUCKET;
    private static ItemDrainBlockEntity setup(GameTestHelper h){h.setBlock(BASE,AllBlocks.ITEM_DRAIN);h.setBlock(OUT,AllBlocks.DEPOT);return h.getBlockEntity(BASE,ItemDrainBlockEntity.class);}
    private static void insert(ItemDrainBlockEntity drain,ItemStack stack){try(var tx=Transaction.openOuter()){drain.storage(Direction.WEST).insert(ItemVariant.of(stack),stack.getCount(),tx);tx.commit();}}
    private static long count(DepotBlockEntity depot,Item item){return java.util.stream.Stream.concat(depot.inventory.inputs.stream(),depot.inventory.outputs.stream()).filter(slot->slot.stack().is(item)).mapToLong(slot->slot.getAmount()).sum();}
    private static long count(net.minecraft.world.Container container,Item item){long amount=0;for(int slot=0;slot<container.getContainerSize();slot++)if(container.getItem(slot).is(item))amount+=container.getItem(slot).getCount();return amount;}
    @GameTest(maxTicks=65)public void drainEmptiesBucketAndExportsContainerAfterAnimation(GameTestHelper h){
        var drain=setup(h);insert(drain,new ItemStack(Items.WATER_BUCKET));var out=h.getBlockEntity(OUT,DepotBlockEntity.class);
        h.runAfterDelay(12,()->{h.assertTrue(drain.getHeldItemStack().is(Items.WATER_BUCKET),"Full bucket during animation");h.assertValueEqual(drain.tank.getAmount(),0L,"No fluid before filling point");});
        h.succeedWhen(()->{h.assertValueEqual(drain.tank.getAmount(),B,"Full bucket recovered");h.assertValueEqual(count(out,Items.BUCKET),1L,"Empty container exported");h.assertTrue(drain.getHeldItemStack().isEmpty(),"Drain clear");});
    }
    @GameTest(maxTicks=90)public void fullTankWaitsWithoutConsumingUntilFluidExtracted(GameTestHelper h){
        var drain=setup(h);try(var tx=Transaction.openOuter()){drain.tank.insert(FluidVariant.of(Fluids.WATER),B,tx);tx.commit();}insert(drain,new ItemStack(Items.WATER_BUCKET));
        h.runAfterDelay(35,()->{h.assertValueEqual(drain.processingTicks,20,"Wait at start");h.assertTrue(drain.getHeldItemStack().is(Items.WATER_BUCKET),"Input held");h.assertValueEqual(drain.tank.getAmount(),B,"Fluid retained");try(var tx=Transaction.openOuter()){drain.fluidStorage.extract(FluidVariant.of(Fluids.WATER),B/2,tx);tx.commit();}});
        h.succeedWhen(()->{h.assertTrue(h.getTick()>35,"Wait for space");h.assertValueEqual(drain.tank.getAmount(),3*B/2,"Exact 1500 mB capacity");h.assertValueEqual(count(h.getBlockEntity(OUT,DepotBlockEntity.class),Items.BUCKET),1L,"Container released");});
    }
    @GameTest(maxTicks=95)public void incompatibleFluidRetainsContainerThenResumesWhenCleared(GameTestHelper h){
        var drain=setup(h);try(var tx=Transaction.openOuter()){drain.tank.insert(FluidVariant.of(Fluids.WATER),B/2,tx);tx.commit();}insert(drain,new ItemStack(Items.LAVA_BUCKET));
        h.runAfterDelay(30,()->{h.assertTrue(drain.getHeldItemStack().is(Items.LAVA_BUCKET),"Lava bucket held against water");h.assertValueEqual(drain.tank.getAmount(),B/2,"Water unchanged");try(var tx=Transaction.openOuter()){drain.tank.extract(FluidVariant.of(Fluids.WATER),B/2,tx);tx.commit();}});
        h.succeedWhen(()->{h.assertValueEqual(drain.tank.getResource(),FluidVariant.of(Fluids.LAVA),"Now lava");h.assertValueEqual(drain.tank.getAmount(),B,"One lava bucket");h.assertValueEqual(count(h.getBlockEntity(OUT,DepotBlockEntity.class),Items.BUCKET),1L,"Container released");});
    }
    @GameTest public void transactionalItemInsertionExtractionAndDirectionRollback(GameTestHelper h){
        var drain=setup(h);var storage=drain.storage(Direction.WEST);var stack=new ItemStack(Items.WATER_BUCKET);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Named bucket"));
        try(var tx=Transaction.openOuter()){h.assertValueEqual(storage.insert(ItemVariant.of(stack),1,tx),1L,"Inserted during transaction");}
        h.assertTrue(drain.getHeldItemStack().isEmpty(),"Aborted insertion");insert(drain,stack);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(storage.extract(ItemVariant.of(stack),1,tx),1L,"Extraction simulated");}
        h.assertValueEqual(drain.getHeldItem().insertedFrom,Direction.EAST,"Direction restored");h.assertTrue(ItemStack.isSameItemSameComponents(drain.getHeldItemStack(),stack),"Components restored");h.succeed();
    }
    @GameTest public void sidedHandlersEnforceExtractionOnlyFluidAndHorizontalItems(GameTestHelper h){
        var drain=setup(h);var pos=h.absolutePos(BASE);
        for(var side:Direction.values()){
            var fluid=FluidStorage.SIDED.find(h.getLevel(),pos,side);h.assertValueEqual(fluid!=null,side!=Direction.UP,"Fluid port side");
            h.assertValueEqual(ItemStorage.SIDED.find(h.getLevel(),pos,side)!=null,side.getAxis().isHorizontal(),"Item port side");
            if(fluid!=null)try(var tx=Transaction.openOuter()){h.assertValueEqual(fluid.insert(FluidVariant.of(Fluids.WATER),B,tx),0L,"Automation cannot fill drain");}
        }h.succeed();
    }
    @GameTest(maxTicks=65)public void oneDrainableItemAcceptedWhileOrdinaryStackPassesWhole(GameTestHelper h){
        var drain=setup(h);try(var tx=Transaction.openOuter()){h.assertValueEqual(drain.storage(Direction.WEST).insert(ItemVariant.of(Items.POPPY),5,tx),1L,"One emptying recipe item");}
        try(var tx=Transaction.openOuter()){h.assertValueEqual(drain.storage(Direction.WEST).insert(ItemVariant.of(Items.IRON_INGOT),17,tx),17L,"Ordinary stack whole");tx.commit();}
        h.succeedWhen(()->{h.assertValueEqual(count(h.getBlockEntity(OUT,DepotBlockEntity.class),Items.IRON_INGOT),17L,"All ordinary items passed");h.assertTrue(drain.tank.isEmpty(),"No fluid created");});
    }
    @GameTest(maxTicks=65)public void emptyingRecipeRetainsFluidComponents(GameTestHelper h){
        var drain=setup(h);insert(drain,new ItemStack(Items.POPPY));
        h.succeedWhen(()->{h.assertValueEqual(drain.tank.getAmount(),B/4,"Recipe fluid");h.assertValueEqual(drain.tank.getResource().getComponents().get(DataComponents.CUSTOM_NAME),Component.literal("Flower water"),"Fluid components");h.assertValueEqual(count(h.getBlockEntity(OUT,DepotBlockEntity.class),Items.STICK),1L,"Recipe container");});
    }
    @GameTest(maxTicks=110)public void heldBucketAndProcessingResumeAfterReload(GameTestHelper h){
        var drain=setup(h);insert(drain,new ItemStack(Items.WATER_BUCKET));
        h.runAfterDelay(10,()->{var loaded=BlockEntity.loadStatic(drain.getBlockPos(),drain.getBlockState(),drain.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());h.getLevel().removeBlockEntity(drain.getBlockPos());h.getLevel().setBlockEntity(loaded);});
        h.succeedWhen(()->{h.assertTrue(h.getTick()>10,"Wait for reload");h.assertValueEqual(h.getBlockEntity(BASE,ItemDrainBlockEntity.class).tank.getAmount(),B,"Fluid recovered once");h.assertValueEqual(count(h.getBlockEntity(OUT,DepotBlockEntity.class),Items.BUCKET),1L,"Container exported once");});
    }
    @GameTest(maxTicks=95)public void blockedItemOutputRetainsContainerWithoutDrainingTwice(GameTestHelper h){
        var drain=setup(h);h.setBlock(OUT,Blocks.BARREL);h.setBlock(OUT.south(2),Blocks.BARREL);
        var output=h.getBlockEntity(OUT,net.minecraft.world.level.block.entity.BarrelBlockEntity.class);var receipt=h.getBlockEntity(OUT.south(2),net.minecraft.world.level.block.entity.BarrelBlockEntity.class);
        for(int slot=0;slot<output.getContainerSize();slot++)output.setItem(slot,new ItemStack(Items.STONE,64));
        var from=ItemStorage.SIDED.find(h.getLevel(),h.absolutePos(OUT),Direction.WEST);var to=ItemStorage.SIDED.find(h.getLevel(),h.absolutePos(OUT.south(2)),Direction.WEST);insert(drain,new ItemStack(Items.WATER_BUCKET));
        h.runAfterDelay(45,()->{h.assertTrue(drain.getHeldItemStack().is(Items.BUCKET),"Empty bucket held at full output");h.assertValueEqual(count(output,Items.BUCKET),0L,"A full real destination cannot admit an early container");h.assertValueEqual(drain.tank.getAmount(),B,"Drained exactly once");try(var tx=Transaction.openOuter()){h.assertValueEqual(from.extract(ItemVariant.of(Items.STONE),64,tx),64L,"Free one actual destination slot");h.assertValueEqual(to.insert(ItemVariant.of(Items.STONE),64,tx),64L,"Every withdrawn stone moves into a real receipt owner");tx.commit();}});
        h.succeedWhen(()->{h.assertTrue(h.getTick()>45,"Wait for output space");h.assertValueEqual(count(output,Items.BUCKET),1L,"Bucket released once");h.assertTrue(drain.getHeldItemStack().isEmpty(),"The drain retains no duplicate container");h.assertValueEqual(drain.tank.getAmount(),B,"No repeat fluid");h.assertValueEqual(count(output,Items.STONE)+count(receipt,Items.STONE),output.getContainerSize()*64L,"All original blocking stock remains physically owned");});
    }
    @GameTest public void manualSurvivalEmptyingReturnsContainerAndCreativeRetainsInput(GameTestHelper h){
        var drain=setup(h);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET));
        h.assertTrue(drain.emptyPlayerItem(player,InteractionHand.MAIN_HAND),"Hand bucket accepted");h.assertTrue(player.getMainHandItem().is(Items.BUCKET),"Survival bucket returned");h.assertValueEqual(drain.tank.getAmount(),B,"Bucket volume");
        var creative=h.makeMockPlayer(GameType.CREATIVE);creative.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.POTION));creative.getMainHandItem().set(DataComponents.POTION_CONTENTS,new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.WATER));
        h.assertTrue(drain.emptyPlayerItem(creative,InteractionHand.MAIN_HAND),"Creative water bottle accepted");h.assertTrue(creative.getMainHandItem().is(Items.POTION),"Creative input retained");h.assertValueEqual(drain.tank.getAmount(),5*B/4,"Create bottle volume");h.succeed();
    }
    @GameTest(maxTicks=75)public void droppedBucketIsCapturedAndEmptied(GameTestHelper h){
        var drain=setup(h);h.spawnItem(Items.WATER_BUCKET,new net.minecraft.world.phys.Vec3(3.5,3,3.5));
        h.succeedWhen(()->h.assertValueEqual(drain.tank.getAmount(),B,"Falling bucket emptied"));
    }
    private static BeltBlockEntity belt(GameTestHelper h,BlockPos start,BlockPos end){
        h.setBlock(start,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Z));h.setBlock(end,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Z));
        BeltConnectorItem.createBelts(h.getLevel(),h.absolutePos(start),h.absolutePos(end));h.setBlock(start.north(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.SOUTH));var motor=h.getBlockEntity(start.north(),KineticBlockEntity.class);motor.adjustGeneratedSpeed(true);motor.adjustGeneratedSpeed(false);motor.adjustGeneratedSpeed(false);return h.getBlockEntity(start,BeltBlockEntity.class);
    }
    @GameTest(maxTicks=130)public void beltDrainBeltChainRecoversFluidAndDeliversEmptyBucket(GameTestHelper h){
        var drain=setup(h);h.setBlock(OUT,Blocks.AIR);var start=new BlockPos(1,2,3);var first=belt(h,start,start.east());belt(h,BASE.east(),BASE.east(2));h.setBlock(BASE.east(3),AllBlocks.DEPOT);var depot=h.getBlockEntity(BASE.east(3),DepotBlockEntity.class);
        try(var tx=Transaction.openOuter()){first.storage(Direction.UP).insert(ItemVariant.of(Items.WATER_BUCKET),1,tx);tx.commit();}
        h.succeedWhen(()->{h.assertValueEqual(count(depot,Items.BUCKET),1L,"Empty bucket delivered through both belts");h.assertValueEqual(drain.tank.getAmount(),B,"Fluid recovered");});
    }
    @GameTest(maxTicks=75)public void extractedHeldInputCancelsDrainOperation(GameTestHelper h){
        var drain=setup(h);insert(drain,new ItemStack(Items.WATER_BUCKET));
        h.runAfterDelay(10,()->{try(var tx=Transaction.openOuter()){drain.storage(Direction.EAST).extract(ItemVariant.of(Items.WATER_BUCKET),1,tx);tx.commit();}});
        h.runAfterDelay(35,()->{h.assertTrue(drain.tank.isEmpty(),"No fluid after input removal");h.assertTrue(drain.getHeldItemStack().isEmpty(),"Input removed");h.assertValueEqual(count(h.getBlockEntity(OUT,DepotBlockEntity.class),Items.BUCKET),0L,"No phantom output");h.succeed();});
    }
    @GameTest(maxTicks=150)public void drainPumpSpoutCycleConservesOneBucket(GameTestHelper h){
        var source=new BlockPos(1,4,3);var pump=source.east();var spoutPos=source.east(3);
        h.setBlock(source,AllBlocks.ITEM_DRAIN);h.setBlock(source.south(),AllBlocks.DEPOT);h.setBlock(pump,AllBlocks.MECHANICAL_PUMP.defaultBlockState().setValue(com.simibubi.create.content.fluids.pump.PumpBlock.FACING,Direction.EAST));
        h.setBlock(pump.above(),AllBlocks.COGWHEEL.defaultBlockState().setValue(com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock.AXIS,Direction.Axis.X));h.setBlock(source.above(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));var motor=h.getBlockEntity(source.above(),KineticBlockEntity.class);for(int i=0;i<4;i++)motor.adjustGeneratedSpeed(false);
        h.setBlock(source.east(2),AllBlocks.FLUID_PIPE.getAxisState(Direction.Axis.X));h.setBlock(spoutPos,AllBlocks.SPOUT);h.setBlock(spoutPos.below(2),AllBlocks.DEPOT);
        var drain=h.getBlockEntity(source,ItemDrainBlockEntity.class);var spout=h.getBlockEntity(spoutPos,com.simibubi.create.content.fluids.spout.SpoutBlockEntity.class);var depot=h.getBlockEntity(spoutPos.below(2),DepotBlockEntity.class);
        try(var tx=Transaction.openOuter()){drain.storage(Direction.NORTH).insert(ItemVariant.of(Items.WATER_BUCKET),1,tx);depot.inventory.automation.insert(ItemVariant.of(Items.BUCKET),1,tx);tx.commit();}
        h.succeedWhen(()->{h.assertValueEqual(count(depot,Items.WATER_BUCKET),1L,"Recovered water refills other bucket through pump");h.assertValueEqual(count(h.getBlockEntity(source.south(),DepotBlockEntity.class),Items.BUCKET),1L,"Original empty bucket returned");h.assertValueEqual(drain.tank.getAmount()+spout.tank.getAmount(),0L,"No fluid duplicated or left behind");});
    }
}
