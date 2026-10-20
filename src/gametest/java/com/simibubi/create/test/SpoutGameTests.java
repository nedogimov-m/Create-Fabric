package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.spout.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;

public class SpoutGameTests {
    private static final BlockPos BASE=new BlockPos(3,2,3),SPOUT=BASE.above(2);
    private static final long B=FluidConstants.BUCKET;
    private static SpoutBlockEntity setup(GameTestHelper h,ItemStack input,long fluid){
        h.setBlock(BASE,AllBlocks.DEPOT);h.setBlock(SPOUT,AllBlocks.SPOUT);var spout=h.getBlockEntity(SPOUT,SpoutBlockEntity.class);
        try(var tx=Transaction.openOuter()){
            h.getBlockEntity(BASE,DepotBlockEntity.class).inventory.automation.insert(ItemVariant.of(input),input.getCount(),tx);
            if(fluid>0)spout.tank.insert(FluidVariant.of(Fluids.WATER),fluid,tx);tx.commit();
        }return spout;
    }
    private static long count(DepotBlockEntity depot,Item item){return java.util.stream.Stream.concat(depot.inventory.inputs.stream(),depot.inventory.outputs.stream()).filter(slot->slot.stack().is(item)).mapToLong(slot->slot.getAmount()).sum();}
    @GameTest(maxTicks=65)public void spoutFillsOneBucketOnDepotAtEndOfDescent(GameTestHelper h){
        var spout=setup(h,new ItemStack(Items.BUCKET),B);var depot=h.getBlockEntity(BASE,DepotBlockEntity.class);
        h.runAfterDelay(10,()->{h.assertValueEqual(spout.tank.getAmount(),B,"Fluid remains before filling point");h.assertTrue(depot.getHeldItem().is(Items.BUCKET),"Input remains during descent");});
        h.succeedWhen(()->{h.assertValueEqual(count(depot,Items.WATER_BUCKET),1L,"Filled container on depot");h.assertValueEqual(spout.tank.getAmount(),0L,"One bucket consumed");});
    }
    @GameTest(maxTicks=130)public void spoutSplitsFourBottlesWithoutCreatingWater(GameTestHelper h){
        var spout=setup(h,new ItemStack(Items.GLASS_BOTTLE,4),B);var depot=h.getBlockEntity(BASE,DepotBlockEntity.class);
        h.succeedWhen(()->{h.assertValueEqual(count(depot,Items.POTION),4L,"Four water bottles");h.assertValueEqual(spout.tank.getAmount(),0L,"Exactly one bucket used");h.assertValueEqual(count(depot,Items.GLASS_BOTTLE),0L,"All four inputs used");});
    }
    @GameTest(maxTicks=80)public void insufficientFluidWaitsAndResumesAfterRefill(GameTestHelper h){
        var spout=setup(h,new ItemStack(Items.BUCKET),B/2);var depot=h.getBlockEntity(BASE,DepotBlockEntity.class);
        h.runAfterDelay(25,()->{h.assertValueEqual(spout.processingTicks,-1,"No incomplete bucket cycle");h.assertValueEqual(spout.tank.getAmount(),B/2,"Partial water held");try(var tx=Transaction.openOuter()){spout.tank.insert(FluidVariant.of(Fluids.WATER),B/2,tx);tx.commit();}});
        h.succeedWhen(()->h.assertValueEqual(count(depot,Items.WATER_BUCKET),1L,"Resumes once enough fluid arrives"));
    }
    @GameTest(maxTicks=65)public void wrongFluidDoesNotConsumeRecipeInput(GameTestHelper h){
        var spout=setup(h,new ItemStack(Items.DIRT),0);try(var tx=Transaction.openOuter()){spout.tank.insert(FluidVariant.of(Fluids.LAVA),B,tx);tx.commit();}
        h.runAfterDelay(35,()->{h.assertValueEqual(spout.processingTicks,-1,"No recipe for lava");h.assertValueEqual(spout.tank.getAmount(),B,"Lava retained");h.assertValueEqual(count(h.getBlockEntity(BASE,DepotBlockEntity.class),Items.DIRT),1L,"Input retained");h.succeed();});
    }
    @GameTest(maxTicks=100)public void dataPackFillingRecipeConvertsOneDirtPerCycle(GameTestHelper h){
        var spout=setup(h,new ItemStack(Items.DIRT,2),B);var depot=h.getBlockEntity(BASE,DepotBlockEntity.class);
        h.succeedWhen(()->{h.assertValueEqual(count(depot,Items.GRASS_BLOCK),2L,"Both dirt processed");h.assertValueEqual(spout.tank.getAmount(),0L,"500 mB per item");});
    }
    @GameTest(maxTicks=100)public void sourceFullBuffersRetainTheirStockWhileRealFillingStateSurvivesReload(GameTestHelper h){
        setup(h,new ItemStack(Items.BUCKET),B);var depot=h.getBlockEntity(BASE,DepotBlockEntity.class);
        try(var tx=Transaction.openOuter()){for(var slot:depot.inventory.outputs)slot.insert(ItemVariant.of(Items.STONE),64,tx);tx.commit();}
        h.runAfterDelay(8,()->{
            var spout=h.getBlockEntity(SPOUT,SpoutBlockEntity.class);h.assertTrue(spout.processingTicks>5,"Actual original bucket is partway through native filling");
            h.assertValueEqual(spout.tank.getAmount(),B,"Original real fluid is unpaid before filling point");h.assertValueEqual(count(depot,Items.BUCKET),1L,"Original bucket remains owned during actual descent");
            var saved=spout.saveWithFullMetadata(h.getLevel().registryAccess());var restored=BlockEntity.loadStatic(spout.getBlockPos(),spout.getBlockState(),saved,h.getLevel().registryAccess());var after=restored.saveWithFullMetadata(h.getLevel().registryAccess());
            h.assertValueEqual(after.getCompoundOrEmpty("Tank"),saved.getCompoundOrEmpty("Tank"),"All original fluid components/counts survive native reload");h.assertValueEqual(after.getIntOr("ProcessingTicks",-1),saved.getIntOr("ProcessingTicks",-1),"Actual source descent time survives reload");h.assertValueEqual(after.get("ProcessingInput"),saved.get("ProcessingInput"),"Exact original input variant survives reload");
            h.getLevel().removeBlockEntity(spout.getBlockPos());h.getLevel().setBlockEntity(restored);
        });
        h.succeedWhen(()->{
            h.assertTrue(h.getTick()>8,"Wait for genuine native reload");h.assertTrue(depot.getHeldItem().is(Items.WATER_BUCKET)&&depot.getHeldItem().getCount()==1,"First source result replaces the actually consumed main even when all8 outputs are full");
            h.assertValueEqual(count(depot,Items.BUCKET),0L,"Original empty bucket consumed once");h.assertValueEqual(count(depot,Items.STONE),512L,"All original full buffers remain intact");h.assertValueEqual(h.getBlockEntity(SPOUT,SpoutBlockEntity.class).tank.getAmount(),0L,"Exactly original one bucket of real fluid is paid");
        });
    }
    @GameTest(maxTicks=75)public void extractedInputCannotBeFilledAfterCycleStarts(GameTestHelper h){
        var spout=setup(h,new ItemStack(Items.BUCKET),B);var depot=h.getBlockEntity(BASE,DepotBlockEntity.class);
        h.runAfterDelay(8,()->{try(var tx=Transaction.openOuter()){depot.inventory.automation.extract(ItemVariant.of(Items.BUCKET),1,tx);depot.inventory.automation.insert(ItemVariant.of(Items.DIAMOND),1,tx);tx.commit();}});
        h.runAfterDelay(40,()->{h.assertValueEqual(spout.tank.getAmount(),B,"Fluid not consumed for missing input");h.assertValueEqual(count(depot,Items.DIAMOND),1L,"Replacement remains");h.assertValueEqual(count(depot,Items.WATER_BUCKET),0L,"No phantom output");h.succeed();});
    }
    @GameTest public void spoutFluidLookupExcludesBottomAndTransactionsAbort(GameTestHelper h){
        var spout=setup(h,new ItemStack(Items.BUCKET),0);var pos=h.absolutePos(SPOUT);
        for(var side:Direction.values())h.assertValueEqual(FluidStorage.SIDED.find(h.getLevel(),pos,side)!=null,side!=Direction.DOWN,"Correct fluid side: "+side);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(spout.tank.insert(FluidVariant.of(Fluids.WATER),2*B,tx),B,"One bucket capacity");}
        h.assertTrue(spout.tank.isEmpty(),"Aborted fill retained no fluid");h.succeed();
    }
    @GameTest(maxTicks=120)public void beltWaitsForFluidThenFillsAndDeliversContainer(GameTestHelper h){
        var start=new BlockPos(1,2,3);var end=start.east(4);
        h.setBlock(start,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Z));h.setBlock(end,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Z));
        h.assertTrue(BeltConnectorItem.createBelts(h.getLevel(),h.absolutePos(start),h.absolutePos(end)),"Belt formed");var belt=h.getBlockEntity(start,BeltBlockEntity.class);
        h.setBlock(start.north(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.SOUTH));var motor=h.getBlockEntity(start.north(),KineticBlockEntity.class);motor.adjustGeneratedSpeed(true);motor.adjustGeneratedSpeed(false);motor.adjustGeneratedSpeed(false);
        h.setBlock(SPOUT,AllBlocks.SPOUT);h.setBlock(end.east(),AllBlocks.DEPOT);var depot=h.getBlockEntity(end.east(),DepotBlockEntity.class);var spout=h.getBlockEntity(SPOUT,SpoutBlockEntity.class);
        try(var tx=Transaction.openOuter()){belt.storage(Direction.UP).insert(ItemVariant.of(Items.BUCKET),1,tx);tx.commit();}
        h.runAfterDelay(35,()->{h.assertTrue(belt.getInventory().getTransportedItems().stream().anyMatch(item->spout.getBlockPos().equals(item.heldBy)),"Bucket held below empty spout");h.assertValueEqual(count(depot,Items.BUCKET),0L,"Bucket not delivered early");try(var tx=Transaction.openOuter()){spout.tank.insert(FluidVariant.of(Fluids.WATER),B,tx);tx.commit();}});
        h.succeedWhen(()->{h.assertValueEqual(count(depot,Items.WATER_BUCKET),1L,"Filled bucket delivered");h.assertValueEqual(spout.tank.getAmount(),0L,"One bucket used");});
    }
    @GameTest(maxTicks=90)public void removingSpoutReleasesHeldBeltCargo(GameTestHelper h){
        var start=new BlockPos(1,2,3);var end=start.east(4);
        h.setBlock(start,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Z));h.setBlock(end,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Z));
        BeltConnectorItem.createBelts(h.getLevel(),h.absolutePos(start),h.absolutePos(end));var belt=h.getBlockEntity(start,BeltBlockEntity.class);
        h.setBlock(start.north(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.SOUTH));var motor=h.getBlockEntity(start.north(),KineticBlockEntity.class);motor.adjustGeneratedSpeed(true);motor.adjustGeneratedSpeed(false);motor.adjustGeneratedSpeed(false);
        h.setBlock(SPOUT,AllBlocks.SPOUT);h.setBlock(end.east(),AllBlocks.DEPOT);var depot=h.getBlockEntity(end.east(),DepotBlockEntity.class);
        try(var tx=Transaction.openOuter()){belt.storage(Direction.UP).insert(ItemVariant.of(Items.BUCKET),1,tx);tx.commit();}
        h.runAfterDelay(30,()->h.setBlock(SPOUT,Blocks.AIR));h.succeedWhen(()->h.assertValueEqual(count(depot,Items.BUCKET),1L,"Held input released on removal"));
    }
    @GameTest(maxTicks=120)public void spoutFillsWaterCauldronInThreeQuarterBucketSteps(GameTestHelper h){
        h.setBlock(BASE,Blocks.CAULDRON);h.setBlock(SPOUT,AllBlocks.SPOUT);var spout=h.getBlockEntity(SPOUT,SpoutBlockEntity.class);
        try(var tx=Transaction.openOuter()){spout.tank.insert(FluidVariant.of(Fluids.WATER),B,tx);tx.commit();}
        h.succeedWhen(()->{h.assertBlockPresent(Blocks.WATER_CAULDRON,BASE);h.assertValueEqual(h.getBlockState(BASE).getValue(LayeredCauldronBlock.LEVEL),3,"Full cauldron");h.assertValueEqual(spout.tank.getAmount(),B/4,"Three Create bottle volumes consumed");});
    }
    @GameTest(maxTicks=60)public void spoutFillsLavaCauldronWithOneBucket(GameTestHelper h){
        h.setBlock(BASE,Blocks.CAULDRON);h.setBlock(SPOUT,AllBlocks.SPOUT);var spout=h.getBlockEntity(SPOUT,SpoutBlockEntity.class);
        try(var tx=Transaction.openOuter()){spout.tank.insert(FluidVariant.of(Fluids.LAVA),B,tx);tx.commit();}
        h.succeedWhen(()->{h.assertBlockPresent(Blocks.LAVA_CAULDRON,BASE);h.assertValueEqual(spout.tank.getAmount(),0L,"Full lava bucket used");});
    }
    @GameTest(maxTicks=60)public void blockSpoutingConvertsDirtAndDoesNotTouchReplacement(GameTestHelper h){
        h.setBlock(BASE,Blocks.DIRT);h.setBlock(SPOUT,AllBlocks.SPOUT);var spout=h.getBlockEntity(SPOUT,SpoutBlockEntity.class);
        try(var tx=Transaction.openOuter()){spout.tank.insert(FluidVariant.of(Fluids.WATER),B,tx);tx.commit();}
        h.succeedWhen(()->{h.assertBlockPresent(Blocks.MUD,BASE);h.assertValueEqual(spout.tank.getAmount(),3*B/4,"250 mB for mud");});
    }
    @GameTest(maxTicks=60)public void blockRemovedBeforeSpoutFinishesDoesNotConsumeFluid(GameTestHelper h){
        h.setBlock(BASE,Blocks.CAULDRON);h.setBlock(SPOUT,AllBlocks.SPOUT);var spout=h.getBlockEntity(SPOUT,SpoutBlockEntity.class);
        try(var tx=Transaction.openOuter()){spout.tank.insert(FluidVariant.of(Fluids.LAVA),B,tx);tx.commit();}
        h.runAfterDelay(8,()->h.setBlock(BASE,Blocks.STONE));h.runAfterDelay(35,()->{h.assertBlockPresent(Blocks.STONE,BASE);h.assertValueEqual(spout.tank.getAmount(),B,"No fluid lost on changed target");h.succeed();});
    }
    @GameTest(maxTicks=80)public void spoutMoistensFarmlandWithRegisteredBlockBehaviour(GameTestHelper h){
        h.setBlock(BASE.below(),Blocks.STONE);h.setBlock(BASE,Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE,0));h.setBlock(BASE.above(),Blocks.WHEAT);h.setBlock(SPOUT,AllBlocks.SPOUT);var spout=h.getBlockEntity(SPOUT,SpoutBlockEntity.class);
        try(var tx=Transaction.openOuter()){spout.tank.insert(FluidVariant.of(Fluids.WATER),8100,tx);tx.commit();}
        h.succeedWhen(()->{h.assertBlockPresent(Blocks.FARMLAND,BASE);h.assertValueEqual(h.getBlockState(BASE).getValue(FarmlandBlock.MOISTURE),1,"One moisture step");h.assertValueEqual(spout.tank.getAmount(),0L,"100 mB used");});
    }
    @GameTest public void spoutingRegistryCanQueryBlockStates(GameTestHelper h){
        h.assertTrue(com.simibubi.create.api.behaviour.spouting.BlockSpoutingBehaviour.BY_BLOCK.get(Blocks.DIRT.defaultBlockState())!=null,"StateHolder accessor resolves owner");h.succeed();
    }
}
