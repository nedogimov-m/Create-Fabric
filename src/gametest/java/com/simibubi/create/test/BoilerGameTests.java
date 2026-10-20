package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.tank.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.*;
import com.simibubi.create.content.processing.burner.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.Fluids;

public class BoilerGameTests {
    private static final BlockPos P=new BlockPos(4,3,4),ENGINE=P.west(),SHAFT=ENGINE.west(2);
    private static final FluidVariant WATER=FluidVariant.of(Fluids.WATER);
    private static void structure(GameTestHelper h,boolean heated){
        for(int x=0;x<2;x++)for(int z=0;z<2;z++){h.setBlock(P.offset(x,0,z),AllBlocks.FLUID_TANK);h.setBlock(P.offset(x,-1,z),Blocks.MAGMA_BLOCK);}
        if(heated){h.setBlock(P.below(),AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.SMOULDERING));h.getBlockEntity(P.below(),BlazeBurnerBlockEntity.class).tryFuel(new ItemStack(Items.COAL),false,false);}
        h.setBlock(SHAFT,AllBlocks.SHAFT.defaultBlockState().setValue(PoweredShaftBlock.AXIS,Direction.Axis.Z));
        h.setBlock(ENGINE,AllBlocks.STEAM_ENGINE.defaultBlockState().setValue(SteamEngineBlock.FACE,AttachFace.WALL).setValue(SteamEngineBlock.FACING,Direction.WEST));
    }
    private static FluidTankBlockEntity tank(GameTestHelper h){return h.getBlockEntity(P,FluidTankBlockEntity.class).getControllerBE();}
    private static PoweredShaftBlockEntity shaft(GameTestHelper h){return h.getBlockEntity(SHAFT,PoweredShaftBlockEntity.class);}
    private static void feed(GameTestHelper h,boolean commit){h.onEachTick(()->{var tank=tank(h);if(tank!=null&&tank.boiler.isActive())try(var tx=Transaction.openOuter()){tank.storage().insert(WATER,10*81L,tx);if(commit)tx.commit();}});}
    @GameTest(maxTicks=60)public void passiveBoilerGeneratesOriginalTwoThousandFortyEightStress(GameTestHelper h){
        structure(h,false);feed(h,true);h.succeedWhen(()->{h.assertValueEqual(tank(h).getTotalTankSize(),4,"Four tank blocks form size level one");h.assertTrue(tank(h).boiler.isPassive(4),"Passive heat and water available");h.assertValueEqual(shaft(h).getSpeed(),16f,"Passive engine speed");h.assertValueEqual(shaft(h).getStressCapacity(),2048.0,"Original passive engine total SU");h.assertFalse(tank(h).hasWindows(),"Boiler closes tank windows");});
    }
    @GameTest(maxTicks=60)public void heatedBoilerProducesSixtyFourRpmAndSixteenThousandStress(GameTestHelper h){
        structure(h,true);feed(h,true);h.succeedWhen(()->{h.assertValueEqual(shaft(h).getSpeed(),64f,"Full efficiency speed");h.assertValueEqual(shaft(h).getStressCapacity(),16384.0,"Full efficiency SU");h.assertValueEqual(tank(h).boiler.activeHeat,1,"One burning blaze heat");});
    }
    @GameTest(maxTicks=100)public void abortedWaterTransferDoesNotCreateSteamPower(GameTestHelper h){
        structure(h,true);feed(h,false);h.runAfterDelay(60,()->{h.assertValueEqual(tank(h).boiler.waterSupply,0f,"Aborted water absent from supply samples");h.assertValueEqual(shaft(h).getSpeed(),0f,"No free steam power");h.succeed();});
    }
    @GameTest(maxTicks=130)public void stoppingWaterSupplyEventuallyStopsEngine(GameTestHelper h){
        structure(h,true);h.runAfterDelay(8,()->{try(var tx=Transaction.openOuter()){tank(h).storage().insert(WATER,50*81L,tx);tx.commit();}});
        h.runAfterDelay(20,()->h.assertValueEqual(shaft(h).getSpeed(),64f,"Initial supply powers engine"));
        h.runAfterDelay(115,()->{h.assertValueEqual(shaft(h).getSpeed(),0f,"Peak supply window expires");h.assertValueEqual(tank(h).boiler.waterSupply,0f,"No stale water supply");h.succeed();});
    }
    @GameTest(maxTicks=60)public void cachedStorageBecomesBoilerSinkAndRejectsNonWater(GameTestHelper h){
        h.setBlock(P,AllBlocks.FLUID_TANK);var cached=h.getBlockEntity(P,FluidTankBlockEntity.class).storage();
        structure(h,true);h.runAfterDelay(10,()->{try(var tx=Transaction.openOuter()){
            h.assertValueEqual(cached.insert(FluidVariant.of(AllFluids.HONEY),81000,tx),0L,"Boiler rejects honey");h.assertValueEqual(cached.insert(WATER,81000,tx),81000L,"Cached tank storage follows boiler mode");h.assertValueEqual(cached.extract(WATER,81000,tx),0L,"Boiler water not extractable");tx.commit();}
            h.assertTrue(tank(h).getTankInventory().isEmpty(),"Supply consumed without filling storage");h.succeed();});
    }
    @GameTest(maxTicks=65)public void removingEngineRestoresShaftAndTankStorage(GameTestHelper h){
        structure(h,true);feed(h,true);h.runAfterDelay(20,()->{h.assertValueEqual(shaft(h).getSpeed(),64f,"Engine running before removal");h.setBlock(ENGINE,Blocks.AIR);});
        h.runAfterDelay(35,()->{h.assertBlockPresent(AllBlocks.SHAFT,SHAFT);h.assertFalse(tank(h).boiler.isActive(),"Boiler disabled when engine removed");try(var tx=Transaction.openOuter()){h.assertValueEqual(tank(h).storage().insert(FluidVariant.of(Fluids.LAVA),81000,tx),81000L,"Normal tank accepts lava again");tx.commit();}h.succeed();});
    }
    @GameTest(maxTicks=60)public void multipleEnginesShareHeatAndPassivePower(GameTestHelper h){
        structure(h,false);var other=P.north();var otherShaft=other.north(2);h.setBlock(otherShaft,AllBlocks.SHAFT.defaultBlockState().setValue(PoweredShaftBlock.AXIS,Direction.Axis.X));h.setBlock(other,AllBlocks.STEAM_ENGINE.defaultBlockState().setValue(SteamEngineBlock.FACE,AttachFace.WALL).setValue(SteamEngineBlock.FACING,Direction.NORTH));feed(h,true);
        h.succeedWhen(()->{h.assertValueEqual(tank(h).boiler.attachedEngines,2,"Both engines counted");h.assertValueEqual(shaft(h).getStressCapacity(),1024.0,"Passive power divided across two engines");h.assertValueEqual(h.getBlockEntity(otherShaft,PoweredShaftBlockEntity.class).getStressCapacity(),1024.0,"Equal power share");});
    }
    @GameTest(maxTicks=60)public void sourceAndBoilerDataSurviveSave(GameTestHelper h){
        structure(h,true);feed(h,true);h.runAfterDelay(20,()->{
            var tank=tank(h);var restored=(FluidTankBlockEntity)BlockEntity.loadStatic(tank.getBlockPos(),tank.getBlockState(),tank.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertValueEqual(restored.boiler.waterSupply,tank.boiler.waterSupply,"Supply persisted");h.assertValueEqual(restored.boiler.activeHeat,1,"Heat persisted");
            var shaft=shaft(h);var restoredShaft=(PoweredShaftBlockEntity)BlockEntity.loadStatic(shaft.getBlockPos(),shaft.getBlockState(),shaft.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertValueEqual(restoredShaft.enginePos,shaft.enginePos,"Engine owner persisted");h.assertValueEqual(restoredShaft.getDirectionalGeneratedSpeed(),shaft.getDirectionalGeneratedSpeed(),"Generated speed persisted");h.succeed();});
    }
    @GameTest(maxTicks=70)public void removingHeatStopsEngineAndAddingHeatRestartsIt(GameTestHelper h){
        structure(h,true);feed(h,true);h.runAfterDelay(20,()->{h.assertValueEqual(shaft(h).getSpeed(),64f,"Initially powered");for(int x=0;x<2;x++)for(int z=0;z<2;z++)h.setBlock(P.offset(x,-1,z),Blocks.STONE);});
        h.runAfterDelay(35,()->{h.assertValueEqual(shaft(h).getSpeed(),0f,"No heat no power");h.setBlock(P.below(),Blocks.MAGMA_BLOCK);});
        h.runAfterDelay(55,()->{h.assertValueEqual(shaft(h).getSpeed(),16f,"Passive heat resumes lower power");h.succeed();});
    }
    @GameTest(maxTicks=65)public void reversingEngineChangesRotationWithoutChangingStress(GameTestHelper h){
        structure(h,true);feed(h,true);h.runAfterDelay(20,()->{h.assertValueEqual(shaft(h).getSpeed(),64f,"Default direction");h.getBlockEntity(ENGINE,SteamEngineBlockEntity.class).reverse();});
        h.runAfterDelay(35,()->{h.assertValueEqual(shaft(h).getSpeed(),-64f,"Reversed direction");h.assertValueEqual(shaft(h).getStressCapacity(),16384.0,"Reverse retains capacity");h.succeed();});
    }
    @GameTest(maxTicks=65)public void undersizedBoilerHasNoPowerEvenWithHeatAndWater(GameTestHelper h){
        structure(h,true);h.setBlock(P.east().south(),Blocks.AIR);h.setBlock(P.east(),Blocks.AIR);h.setBlock(P.south(),Blocks.AIR);feed(h,true);
        h.runAfterDelay(40,()->{h.assertValueEqual(tank(h).getTotalTankSize(),1,"Single tank remains");h.assertValueEqual(shaft(h).getSpeed(),0f,"Four blocks needed for first boiler size level");h.succeed();});
    }
    @GameTest(maxTicks=65)public void engineAttachedAfterShaftConvertsAndWrongAxisIsRejected(GameTestHelper h){
        structure(h,true);h.setBlock(SHAFT,AllBlocks.SHAFT.defaultBlockState().setValue(PoweredShaftBlock.AXIS,Direction.Axis.X));feed(h,true);
        h.runAfterDelay(15,()->{h.assertBlockPresent(AllBlocks.SHAFT,SHAFT);h.setBlock(SHAFT,AllBlocks.SHAFT.defaultBlockState().setValue(PoweredShaftBlock.AXIS,Direction.Axis.Y));});
        h.runAfterDelay(35,()->{h.assertBlockPresent(AllBlocks.POWERED_SHAFT,SHAFT);h.assertValueEqual(shaft(h).getSpeed(),16f*4,"Perpendicular shaft powers after replacement");h.succeed();});
    }
    @GameTest(maxTicks=120)public void pumpSuppliesBoilerThroughStandardFabricFluidStorage(GameTestHelper h){
        structure(h,true);var pump=P.east(2);var source=pump.east();h.setBlock(source,AllBlocks.CREATIVE_FLUID_TANK);((CreativeFluidTankBlockEntity.CreativeSmartFluidTank)h.getBlockEntity(source,CreativeFluidTankBlockEntity.class).getTankInventory()).setContainedFluid(new com.simibubi.create.foundation.fluid.FluidStack(WATER,81000));
        h.setBlock(pump,AllBlocks.MECHANICAL_PUMP.defaultBlockState().setValue(com.simibubi.create.content.fluids.pump.PumpBlock.FACING,Direction.WEST));
        h.setBlock(pump.above(),AllBlocks.COGWHEEL.defaultBlockState().setValue(PoweredShaftBlock.AXIS,Direction.Axis.X));h.setBlock(pump.above().east(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING,Direction.WEST));h.getBlockEntity(pump.above().east(),KineticBlockEntity.class).adjustGeneratedSpeed(false);
        h.succeedWhen(()->{h.assertTrue(tank(h).boiler.waterSupply>=10,"Measured real pump water supply");h.assertValueEqual(shaft(h).getSpeed(),64f,"Real pump powers steam engine");h.assertTrue(tank(h).getTankInventory().isEmpty(),"Boiler water never accumulates in normal storage");});
    }
    @GameTest(maxTicks=70)public void engineFollowsExistingSourceDirectionAndPreservesItsOwnCapacity(GameTestHelper h){
        structure(h,true);feed(h,true);var motor=SHAFT.north();h.setBlock(motor,AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING,Direction.SOUTH));var drive=h.getBlockEntity(motor,KineticBlockEntity.class);for(int i=0;i<3;i++)drive.adjustGeneratedSpeed(false);drive.adjustGeneratedSpeed(true);
        h.runAfterDelay(25,()->{h.assertValueEqual(shaft(h).getSpeed(),-128f,"Driven network sets rotation direction");h.assertFalse(shaft(h).isConflicted(),"Steam engine aligns rather than causing opposite-source conflict");h.assertValueEqual(shaft(h).getStressCapacity(),16384.0+128*16384,"Steam contribution uses its generated RPM");h.setBlock(motor,Blocks.AIR);});
        h.runAfterDelay(45,()->{h.assertValueEqual(shaft(h).getSpeed(),-64f,"Engine retains matched direction after other source disappears");h.assertValueEqual(shaft(h).getStressCapacity(),16384.0,"Engine retains its own capacity");h.succeed();});
    }
    @GameTest public void boilerLimitsSizeHeatWaterAndMaximumLevel(GameTestHelper h){
        var boiler=new BoilerData();boiler.attachedEngines=18;boiler.activeHeat=18;boiler.waterSupply=180;
        h.assertValueEqual(boiler.getActualHeat(72),18,"Maximum boiler uses 72 tanks, 18 heat and 180 mB/t");h.assertValueEqual(boiler.getEngineEfficiency(72),1f,"Eighteen engines at full efficiency");
        h.assertValueEqual(boiler.getActualHeat(4),1,"Size limits power");boiler.waterSupply=20;h.assertValueEqual(boiler.getActualHeat(72),2,"Water supply limits power");boiler.activeHeat=1;h.assertValueEqual(boiler.getActualHeat(72),1,"Heat limits power");
        boiler.waterSupply=1000;boiler.activeHeat=100;h.assertValueEqual(boiler.getActualHeat(288),18,"Level capped at eighteen");h.succeed();
    }
    @GameTest(maxTicks=80)public void steamEngineFollowsDirectionThroughSpeedControllerAndRecoversAfterDisconnect(GameTestHelper h){
        structure(h,true);feed(h,true);var controllerPos=SHAFT.north();h.setBlock(controllerPos,AllBlocks.ROTATION_SPEED_CONTROLLER.defaultBlockState().setValue(com.simibubi.create.content.kinetics.speedController.SpeedControllerBlock.HORIZONTAL_AXIS,Direction.Axis.Z));h.setBlock(controllerPos.above(),AllBlocks.LARGE_COGWHEEL.defaultBlockState().setValue(PoweredShaftBlock.AXIS,Direction.Axis.X));h.setBlock(controllerPos.above().west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING,Direction.EAST));h.getBlockEntity(controllerPos,com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity.class).setTargetSpeed(-128);
        h.runAfterDelay(25,()->{h.assertValueEqual(shaft(h).getSpeed(),-128f,"Regulated direction reaches steam shaft");h.assertFalse(shaft(h).isConflicted(),"Steam engine aligns across controller");h.assertValueEqual(shaft(h).getStressCapacity(),16384d+16*16384,"Capacity counts both real generators at own RPM");h.setBlock(controllerPos.above(),Blocks.AIR);});
        h.runAfterDelay(45,()->{h.assertValueEqual(shaft(h).getSpeed(),-64f,"Engine retains matched direction after regulated source removed");h.assertValueEqual(shaft(h).getStressCapacity(),16384d,"No phantom regulator source");h.succeed();});
    }
    @GameTest(maxTicks=60)public void nestedBoilerInputWaitsForOuterCommitAndExistingFluidReturnsAfterEngineRemoval(GameTestHelper h){
        h.setBlock(P,AllBlocks.FLUID_TANK);var cached=h.getBlockEntity(P,FluidTankBlockEntity.class).storage();try(var tx=Transaction.openOuter()){cached.insert(FluidVariant.of(Fluids.LAVA),81000,tx);tx.commit();}
        structure(h,true);h.runAfterDelay(10,()->{
            try(var tx=Transaction.openOuter()){try(var nested=tx.openNested()){h.assertValueEqual(cached.insert(WATER,81000,nested),81000L,"Nested water accepted");nested.commit();}}
            for(int i=0;i<5;i++)tank(h).boiler.tick(tank(h));h.assertValueEqual(tank(h).boiler.waterSupply,0f,"Outer abort cancels sampled water");
            h.setBlock(ENGINE,Blocks.AIR);
            try(var tx=Transaction.openOuter()){h.assertValueEqual(cached.extract(FluidVariant.of(Fluids.LAVA),81000,tx),81000L,"Stored fluid survives boiler mode");tx.commit();}h.succeed();
        });
    }
}
