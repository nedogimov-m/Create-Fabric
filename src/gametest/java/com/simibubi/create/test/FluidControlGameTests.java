package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.*;
import com.simibubi.create.content.fluids.pipes.*;
import com.simibubi.create.content.fluids.pipes.valve.*;
import com.simibubi.create.content.fluids.pump.*;
import com.simibubi.create.content.fluids.tank.*;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

public class FluidControlGameTests {
    @GameTest(maxTicks=120)
    public void configuredFluidListsBlockAndReopenActualPumpRoute(GameTestHelper h){
        var out=pipeline(h,FluidVariant.of(Fluids.WATER),3);var pipe=smart(h);pipe.setFilter(FilterGameTests.filter(true,false,new ItemStack(Items.WATER_BUCKET)));h.runAfterDelay(30,()->{h.assertValueEqual(out.getTankInventory().getFluidAmount(),0L,"Deny list blocked actual route");pipe.setFilter(FilterGameTests.filter(false,false,new ItemStack(Items.WATER_BUCKET),new ItemStack(Items.LAVA_BUCKET)));});h.succeedWhen(()->h.assertValueEqual(out.getTankInventory().getFluidAmount(),3*B,"Changing configured filter invalidates blocked route"));
    }
    private static final BlockPos P=new BlockPos(3,3,3),CONTROL=P.east();
    private static final long B=FluidConstants.BUCKET;
    private static KineticBlockEntity motor(GameTestHelper h,BlockPos pos,Direction facing,int rpm){
        h.setBlock(pos,AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,facing));var motor=h.getBlockEntity(pos,KineticBlockEntity.class);
        for(int speed=16;speed<Math.abs(rpm);speed*=2)motor.adjustGeneratedSpeed(false);if(rpm<0)motor.adjustGeneratedSpeed(true);return motor;
    }
    private static FluidTankBlockEntity tank(GameTestHelper h,BlockPos pos,FluidVariant fluid,int buckets){
        h.setBlock(pos,AllBlocks.FLUID_TANK);var tank=h.getBlockEntity(pos,FluidTankBlockEntity.class);
        if(buckets>0)try(var tx=Transaction.openOuter()){tank.storage().insert(fluid,buckets*B,tx);tx.commit();}return tank;
    }
    private static FluidTankBlockEntity pipeline(GameTestHelper h,FluidVariant fluid,int buckets){
        tank(h,P.west(),fluid,buckets);var out=tank(h,P.east(2),fluid,0);
        h.setBlock(P,AllBlocks.MECHANICAL_PUMP.defaultBlockState().setValue(PumpBlock.FACING,Direction.EAST));h.setBlock(P.above(),AllBlocks.COGWHEEL.defaultBlockState().setValue(CogWheelBlock.AXIS,Direction.Axis.X));motor(h,P.above().west(),Direction.EAST,256);return out;
    }
    private static SmartFluidPipeBlockEntity smart(GameTestHelper h){h.setBlock(CONTROL,AllBlocks.SMART_FLUID_PIPE.defaultBlockState().setValue(SmartFluidPipeBlock.FACING,Direction.EAST));return h.getBlockEntity(CONTROL,SmartFluidPipeBlockEntity.class);}
    private static FluidValveBlockEntity valve(GameTestHelper h,boolean open,int rpm){
        h.setBlock(CONTROL,AllBlocks.FLUID_VALVE.defaultBlockState().setValue(FluidValveBlock.FACING,Direction.UP).setValue(FluidValveBlock.AXIS_ALONG_FIRST_COORDINATE,false).setValue(FluidValveBlock.ENABLED,open));
        if(rpm!=0)motor(h,CONTROL.north(),Direction.SOUTH,rpm);return h.getBlockEntity(CONTROL,FluidValveBlockEntity.class);
    }
    @GameTest(maxTicks=70)
    public void closedValveBlocksEvenWhilePipePressureExists(GameTestHelper h){var out=pipeline(h,FluidVariant.of(Fluids.WATER),4);valve(h,false,0);h.runAfterDelay(40,()->{h.assertValueEqual(out.getTankInventory().getFluidAmount(),0L,"Closed valve blocks flow");h.assertValueEqual(h.getBlockEntity(P.west(),FluidTankBlockEntity.class).getTankInventory().getFluidAmount(),4*B,"Input retained");h.succeed();});}
    @GameTest(maxTicks=100)
    public void positiveRotationOpensValveAndWaterFlows(GameTestHelper h){var out=pipeline(h,FluidVariant.of(Fluids.WATER),3);var valve=valve(h,false,16);h.runAfterDelay(15,()->{h.assertTrue(valve.getPointer(1)>0&&valve.getPointer(1)<1,"Pointer travels gradually");h.assertFalse(h.getBlockState(CONTROL).getValue(FluidValveBlock.ENABLED),"No flow before full opening");});h.succeedWhen(()->{h.assertValueEqual(out.getTankInventory().getFluidAmount(),3*B,"Open valve transfers entire input");h.assertValueEqual(valve.getPointer(1),1f,"Pointer fully open");});}
    @GameTest(maxTicks=100)
    public void negativeRotationClosesValveAndStopsExistingRoute(GameTestHelper h){var out=pipeline(h,FluidVariant.of(Fluids.WATER),8);var valve=valve(h,false,32);final long[] stopped={0};h.runAfterDelay(30,()->h.getBlockEntity(CONTROL.north(),KineticBlockEntity.class).adjustGeneratedSpeed(true));h.runAfterDelay(50,()->{h.assertFalse(h.getBlockState(CONTROL).getValue(FluidValveBlock.ENABLED),"Valve closed");h.assertValueEqual(valve.getPointer(1),0f,"Pointer fully closed");stopped[0]=out.getTankInventory().getFluidAmount();h.assertTrue(stopped[0]>0,"Water flowed before closing");});h.runAfterDelay(65,()->{h.assertValueEqual(out.getTankInventory().getFluidAmount(),stopped[0],"Cached route cannot bypass closed valve");h.succeed();});}
    @GameTest(maxTicks=60)
    public void stoppingDrivePausesPartialValveTravel(GameTestHelper h){var valve=valve(h,false,16);final float[] pointer={0};h.runAfterDelay(8,()->h.setBlock(CONTROL.north(),Blocks.AIR));h.runAfterDelay(12,()->{pointer[0]=valve.getPointer(1);h.assertTrue(pointer[0]>0&&pointer[0]<1,"Partial angle");});h.runAfterDelay(28,()->{h.assertValueEqual(valve.getPointer(1),pointer[0],"Unpowered pointer holds position");h.assertFalse(h.getBlockState(CONTROL).getValue(FluidValveBlock.ENABLED),"Still closed until full travel");h.succeed();});}
    @GameTest(maxTicks=65)
    public void pointerAndOpeningResumeAfterBlockEntityReload(GameTestHelper h){var valve=valve(h,false,16);h.runAfterDelay(8,()->{var loaded=(FluidValveBlockEntity)BlockEntity.loadStatic(valve.getBlockPos(),valve.getBlockState(),valve.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertValueEqual(loaded.getPointer(1),valve.getPointer(1),"Pointer persisted");h.getLevel().removeBlockEntity(valve.getBlockPos());h.getLevel().setBlockEntity(loaded);});h.succeedWhen(()->{h.assertTrue(h.getTick()>8,"Wait for reload");h.assertTrue(h.getBlockState(CONTROL).getValue(FluidValveBlock.ENABLED),"Resumed to full opening");});}
    @GameTest(maxTicks=75)
    public void openValveHoldsPositionWhenPowerRemoved(GameTestHelper h){valve(h,false,32);h.runAfterDelay(20,()->h.setBlock(CONTROL.north(),Blocks.AIR));h.runAfterDelay(40,()->{h.assertTrue(h.getBlockState(CONTROL).getValue(FluidValveBlock.ENABLED),"Open state holds without rotation");h.assertValueEqual(h.getBlockEntity(CONTROL,FluidValveBlockEntity.class).getPointer(1),1f,"Pointer stays fully open");h.succeed();});}
    @GameTest
    public void allValveOrientationsSeparatePipeShaftAndFacingAxes(GameTestHelper h){
        for(var facing:Direction.values())for(boolean first:new boolean[]{true,false}){
            var state=AllBlocks.FLUID_VALVE.defaultBlockState().setValue(FluidValveBlock.FACING,facing).setValue(FluidValveBlock.AXIS_ALONG_FIRST_COORDINATE,first);var shaft=AllBlocks.FLUID_VALVE.getRotationAxis(state);var pipe=FluidValveBlock.getPipeAxis(state);
            h.assertTrue(shaft!=pipe&&shaft!=facing.getAxis()&&pipe!=facing.getAxis(),"Three distinct valve axes: "+state);
            for(var side:Direction.values())h.assertValueEqual(AllBlocks.FLUID_VALVE.hasShaftTowards(h.getLevel(),h.absolutePos(CONTROL),state,side),side.getAxis()==shaft,"Only shaft faces transmit rotation");
        }h.succeed();
    }
    @GameTest(maxTicks=90)
    public void emptySmartFilterPassesAndMatchingBucketDoesNotConsumeSample(GameTestHelper h){var out=pipeline(h,FluidVariant.of(Fluids.WATER),3);var smart=smart(h);var sample=new ItemStack(Items.WATER_BUCKET);h.assertTrue(smart.setFilter(sample),"Fluid sample applied");h.assertTrue(sample.is(Items.WATER_BUCKET),"Sample unchanged");h.succeedWhen(()->h.assertValueEqual(out.getTankInventory().getFluidAmount(),3*B,"Matching liquid passes"));}
    @GameTest(maxTicks=80)
    public void differentLiquidAndNonfluidSamplesBlockWater(GameTestHelper h){var out=pipeline(h,FluidVariant.of(Fluids.WATER),4);var smart=smart(h);smart.setFilter(new ItemStack(Items.LAVA_BUCKET));h.runAfterDelay(30,()->{h.assertValueEqual(out.getTankInventory().getFluidAmount(),0L,"Different liquid rejected");smart.setFilter(new ItemStack(Items.DIAMOND));});h.runAfterDelay(50,()->{h.assertValueEqual(out.getTankInventory().getFluidAmount(),0L,"Non-fluid sample rejects liquids");h.succeed();});}
    @GameTest(maxTicks=100)
    public void removingFilterRestoresPreviouslyBlockedFlow(GameTestHelper h){var out=pipeline(h,FluidVariant.of(Fluids.WATER),3);var smart=smart(h);smart.setFilter(new ItemStack(Items.LAVA_BUCKET));h.runAfterDelay(25,()->{h.assertValueEqual(out.getTankInventory().getFluidAmount(),0L,"Initially blocked");smart.clearContent();});h.succeedWhen(()->{h.assertTrue(h.getTick()>25,"Wait for clearing");h.assertValueEqual(out.getTankInventory().getFluidAmount(),3*B,"Clearing filter opens route");});}
    @GameTest(maxTicks=75)
    public void changingFilterImmediatelyInvalidatesCachedTransferRoute(GameTestHelper h){var out=pipeline(h,FluidVariant.of(Fluids.WATER),8);var smart=smart(h);final long[] before={0};h.runAfterDelay(30,()->{h.assertTrue(out.getTankInventory().getFluidAmount()>0,"Route initially flowing");smart.setFilter(new ItemStack(Items.LAVA_BUCKET));before[0]=out.getTankInventory().getFluidAmount();});h.runAfterDelay(45,()->{h.assertValueEqual(out.getTankInventory().getFluidAmount(),before[0],"Existing network cannot bypass changed filter");h.succeed();});}
    @GameTest
    public void directSampleMatchesFluidComponentsExactly(GameTestHelper h){var smart=smart(h);smart.setFilter(new ItemStack(Items.WATER_BUCKET));var named=FluidVariant.of(Fluids.WATER,net.minecraft.core.component.DataComponentPatch.builder().set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Treated")).build());h.assertFalse(smart.test(new FluidStack(named,B)),"Components differ from plain bucket");h.assertTrue(smart.test(new FluidStack(FluidVariant.of(Fluids.WATER),B)),"Plain water matches");h.succeed();}
    @GameTest(maxTicks=80)
    public void smartFilterAndPipeFlowPersistAfterReload(GameTestHelper h){var out=pipeline(h,FluidVariant.of(Fluids.WATER),2);var smart=smart(h);smart.setFilter(new ItemStack(Items.WATER_BUCKET));h.runAfterDelay(12,()->{var loaded=(SmartFluidPipeBlockEntity)BlockEntity.loadStatic(smart.getBlockPos(),smart.getBlockState(),smart.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(loaded.getFilter().is(Items.WATER_BUCKET),"Filter persisted");h.getLevel().removeBlockEntity(smart.getBlockPos());h.getLevel().setBlockEntity(loaded);});h.succeedWhen(()->{h.assertTrue(h.getTick()>12,"Wait for reload");h.assertValueEqual(out.getTankInventory().getFluidAmount(),2*B,"Flow restored");});}
    @GameTest
    public void clickOnFilterSlotCopiesAndEmptyHandClearsSample(GameTestHelper h){
        var smart=smart(h);var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);var bucket=new ItemStack(Items.WATER_BUCKET);var pos=h.absolutePos(CONTROL);var state=h.getBlockState(CONTROL);
        var hit=new net.minecraft.world.phys.BlockHitResult(SmartFluidPipeBlock.filterPosition(state).add(net.minecraft.world.phys.Vec3.atLowerCornerOf(pos)),Direction.UP,pos,false);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,bucket);
        state.useItemOn(bucket,h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);h.assertTrue(smart.getFilter().is(Items.WATER_BUCKET),"Clicked sample set");h.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET),"Bucket retained in survival");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);state.useWithoutItem(h.getLevel(),player,hit);h.assertTrue(smart.getFilter().isEmpty(),"Empty hand clears sample");h.succeed();
    }
    @GameTest
    public void filterHitAndPipeAxisFollowEveryMountOrientation(GameTestHelper h){for(var face:AttachFace.values())for(var side:Direction.Plane.HORIZONTAL){var state=AllBlocks.SMART_FLUID_PIPE.defaultBlockState().setValue(SmartFluidPipeBlock.FACE,face).setValue(SmartFluidPipeBlock.FACING,side);h.assertValueEqual(SmartFluidPipeBlock.getPipeAxis(state),face==AttachFace.WALL?Direction.Axis.Y:side.getAxis(),"Mount pipe axis");var pos=h.absolutePos(CONTROL);h.assertTrue(SmartFluidPipeBlock.filterHit(state,pos,new net.minecraft.world.phys.BlockHitResult(SmartFluidPipeBlock.filterPosition(state).add(net.minecraft.world.phys.Vec3.atLowerCornerOf(pos)),Direction.UP,pos,false)),"Filter slot centre hittable");}h.succeed();}
    @GameTest
    public void smartPipeResolvesFluidFromEmptyingRecipeAndAfterReload(GameTestHelper h){
        var pipe=smart(h);pipe.setFilter(new ItemStack(Items.POPPY));
        var variant=FluidVariant.of(Fluids.WATER,net.minecraft.core.component.DataComponentPatch.builder().set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Flower water")).build());
        h.assertTrue(pipe.test(new FluidStack(variant,B)),"Recipe sample resolves fluid components");h.assertFalse(pipe.test(new FluidStack(FluidVariant.of(Fluids.WATER),B)),"Plain water differs");
        var loaded=(SmartFluidPipeBlockEntity)BlockEntity.loadStatic(pipe.getBlockPos(),pipe.getBlockState(),pipe.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());loaded.setLevel(h.getLevel());
        h.assertTrue(loaded.test(new FluidStack(variant,B)),"Recipe sample resolves lazily after reload");h.succeed();
    }
}
