package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.hosePulley.*;
import com.simibubi.create.content.fluids.transfer.FluidManipulationBehaviour.BottomlessFluidMode;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

public class HosePulleyGameTests {
    private static final BlockPos P=new BlockPos(3,5,3),ROOT=P.below(3);
    private static final long B=FluidConstants.BUCKET;
    private static final FluidVariant WATER=FluidVariant.of(Fluids.WATER),LAVA=FluidVariant.of(Fluids.LAVA);
    private static HosePulleyBlockEntity pulley(GameTestHelper h,int depth){
        return pulley(h,P,depth);
    }
    private static HosePulleyBlockEntity pulley(GameTestHelper h,BlockPos position,int depth){
        h.setBlock(position,AllBlocks.HOSE_PULLEY);var be=h.getBlockEntity(position,HosePulleyBlockEntity.class);
        var tag=be.saveWithFullMetadata(h.getLevel().registryAccess());var offset=new net.minecraft.nbt.CompoundTag();offset.putFloat("Value",depth);tag.put("Offset",offset);
        var restored=(HosePulleyBlockEntity)BlockEntity.loadStatic(be.getBlockPos(),be.getBlockState(),tag,h.getLevel().registryAccess());h.getLevel().removeBlockEntity(be.getBlockPos());h.getLevel().setBlockEntity(restored);return restored;
    }
    private static void pool(GameTestHelper h,BlockPos min,BlockPos max){
        for(var pos:BlockPos.betweenClosed(min.offset(-1,-1,-1),max.offset(1,0,1)))h.setBlock(pos,Blocks.STONE);
        for(var pos:BlockPos.betweenClosed(min,max))h.setBlock(pos,Blocks.WATER.defaultBlockState());
    }
    @GameTest(maxTicks=50)public void finitePoolDrainRollbackThenCommitRemovesOneSource(GameTestHelper h){
        pool(h,ROOT,ROOT);var pulley=pulley(h,3);
        h.runAfterDelay(8,()->{
            try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.extract(WATER,B,tx),B,"Bucket available");}h.assertBlockPresent(Blocks.WATER,ROOT);h.assertTrue(pulley.tank.isEmpty(),"Aborted buffer change");
            try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.extract(WATER,B,tx),B,"Same source available after rollback");tx.commit();}
            h.assertBlockPresent(Blocks.AIR,ROOT);h.assertTrue(pulley.tank.isEmpty(),"Whole bucket transferred");h.succeed();
        });
    }
    @GameTest(maxTicks=50)public void partialDrainStoresRemainderAndNeverPullsSourceTwice(GameTestHelper h){
        pool(h,ROOT,ROOT);var pulley=pulley(h,3);
        h.runAfterDelay(8,()->{try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.extract(WATER,B/4,tx),B/4,"Partial transfer");tx.commit();}h.assertValueEqual(pulley.tank.getAmount(),3*B/4,"Remaining source held internally");h.assertBlockPresent(Blocks.AIR,ROOT);
            try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.extract(WATER,2*B,tx),3*B/4,"Only remainder available");tx.commit();}h.assertTrue(pulley.tank.isEmpty(),"No duplicated water");h.succeed();});
    }
    @GameTest(maxTicks=50)public void drainUsesHighestThenFarthestSource(GameTestHelper h){
        pool(h,ROOT,ROOT.east().above());var pulley=pulley(h,3);
        h.runAfterDelay(8,()->{try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.extract(WATER,B,tx),B,"Bucket drained");tx.commit();}h.assertBlockPresent(Blocks.AIR,ROOT.east().above());h.assertBlockPresent(Blocks.WATER,ROOT.above());h.assertBlockPresent(Blocks.WATER,ROOT);h.succeed();});
    }
    @GameTest(maxTicks=50)public void drainDoesNotSearchBelowHoseRoot(GameTestHelper h){
        pool(h,ROOT.below(),ROOT);var pulley=pulley(h,3);
        h.runAfterDelay(8,()->{try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.extract(WATER,2*B,tx),B,"Only upper source drained");tx.commit();}h.assertBlockPresent(Blocks.WATER,ROOT.below());h.succeed();});
    }
    @GameTest(maxTicks=65)public void fillingRollbackRetainsBufferAndLeavesWorldUntouched(GameTestHelper h){
        pool(h,ROOT,ROOT);h.setBlock(ROOT,Blocks.AIR);var pulley=pulley(h,3);
        h.runAfterDelay(8,()->{
            try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.insert(WATER,B,tx),B,"One bucket staged");}h.assertBlockPresent(Blocks.AIR,ROOT);h.assertTrue(pulley.tank.isEmpty(),"Abort removes buffer too");
            try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.insert(WATER,B,tx),B,"Same fill possible");tx.commit();}h.assertBlockPresent(Blocks.WATER,ROOT);h.succeed();
        });
    }
    @GameTest(maxTicks=65)public void partialFillingCombinesBufferIntoOneSource(GameTestHelper h){
        pool(h,ROOT,ROOT);h.setBlock(ROOT,Blocks.AIR);var pulley=pulley(h,3);
        h.runAfterDelay(8,()->{try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.insert(WATER,B/4,tx),B/4,"Quarter bucket buffered");tx.commit();}h.assertBlockPresent(Blocks.AIR,ROOT);
            try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.insert(WATER,3*B/4,tx),3*B/4,"Remaining volume accepted");tx.commit();}h.assertBlockPresent(Blocks.WATER,ROOT);h.assertTrue(pulley.tank.isEmpty(),"Buffer spent on source");h.succeed();});
    }
    @GameTest(maxTicks=65)public void fillingPrioritizesLowerSpaceAfterRoot(GameTestHelper h){
        pool(h,ROOT.below(),ROOT);h.setBlock(ROOT,Blocks.AIR);h.setBlock(ROOT.below(),Blocks.AIR);var pulley=pulley(h,3);
        h.runAfterDelay(8,()->{for(int i=0;i<2;i++)try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.insert(WATER,B,tx),B,"World bucket "+i);tx.commit();}h.assertBlockPresent(Blocks.WATER,ROOT);h.assertBlockPresent(Blocks.WATER,ROOT.below());h.assertTrue(pulley.tank.isEmpty(),"Two sources filled");h.succeed();});
    }
    @GameTest(maxTicks=65)public void waterloggedBlocksDrainAndFillWithoutDestroyingBlock(GameTestHelper h){
        pool(h,ROOT,ROOT);h.setBlock(ROOT,Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,true));var pulley=pulley(h,3);
        h.runAfterDelay(8,()->{try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.extract(WATER,B,tx),B,"Waterlogged source extracted");tx.commit();}h.assertBlockPresent(Blocks.OAK_SLAB,ROOT);h.assertFalse(h.getBlockState(ROOT).getValue(BlockStateProperties.WATERLOGGED),"Slab dry");});
        h.runAfterDelay(15,()->{try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.insert(WATER,B,tx),B,"Water returned");tx.commit();}h.assertTrue(h.getBlockState(ROOT).getValue(BlockStateProperties.WATERLOGGED),"Slab waterlogged again");h.succeed();});
    }
    @GameTest(maxTicks=65)public void infiniteSourceRequiresAllowedFluidAndThreshold(GameTestHelper h){
        pool(h,ROOT,ROOT.east(2));var pulley=pulley(h,3);pulley.configure(new HosePulleySettings(128,2,false,BottomlessFluidMode.ALLOW_ALL,true));
        h.runAfterDelay(8,()->{h.assertTrue(pulley.isInfinite(),"Pool exceeds configured threshold");for(int i=0;i<5;i++)try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.extract(WATER,B,tx),B,"Infinite bucket");tx.commit();}for(int i=0;i<3;i++)h.assertBlockPresent(Blocks.WATER,ROOT.east(i));h.assertTrue(pulley.tank.isEmpty(),"No artificial buffer residue");h.succeed();});
    }
    @GameTest(maxTicks=65)public void deniedBottomlessFluidStaysFinite(GameTestHelper h){
        pool(h,ROOT,ROOT.east(2));var pulley=pulley(h,3);pulley.configure(new HosePulleySettings(128,2,false,BottomlessFluidMode.DENY_ALL,true));
        h.runAfterDelay(8,()->{h.assertFalse(pulley.isInfinite(),"Bottomless denied");try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.extract(WATER,B,tx),B,"Finite bucket");tx.commit();}h.assertBlockPresent(Blocks.AIR,ROOT.east(2));h.succeed();});
    }
    @GameTest(maxTicks=65)public void nestedAbortRestoresWorldQueueAndBuffer(GameTestHelper h){
        pool(h,ROOT,ROOT.east());var pulley=pulley(h,3);
        h.runAfterDelay(8,()->{try(var tx=Transaction.openOuter()){
            try(var nested=tx.openNested()){h.assertValueEqual(pulley.storage.extract(WATER,B/2,nested),B/2,"Nested extraction");}
            h.assertValueEqual(pulley.storage.extract(WATER,B,tx),B,"First source restored by nested abort");tx.commit();
        }h.assertBlockPresent(Blocks.AIR,ROOT.east());h.assertBlockPresent(Blocks.WATER,ROOT);h.assertTrue(pulley.tank.isEmpty(),"Buffer rollback");h.succeed();});
    }
    @GameTest(maxTicks=80)public void loweringHoseStopsAtSolidFloorAndReverseRaisesIt(GameTestHelper h){
        h.setBlock(ROOT,Blocks.STONE);h.setBlock(P,AllBlocks.HOSE_PULLEY);var pulley=h.getBlockEntity(P,HosePulleyBlockEntity.class);
        h.setBlock(P.east(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.WEST));var motor=h.getBlockEntity(P.east(),KineticBlockEntity.class);motor.adjustGeneratedSpeed(true);for(int i=0;i<4;i++)motor.adjustGeneratedSpeed(false);
        h.runAfterDelay(15,()->{h.assertValueEqual(pulley.getOffset(),2f,"Stops one block above solid floor");h.assertFalse(pulley.isMoving(),"Hose stationary");motor.adjustGeneratedSpeed(true);});
        h.runAfterDelay(30,()->{h.assertValueEqual(pulley.getOffset(),0f,"Reverse fully retracts hose");h.succeed();});
    }
    @GameTest public void pulleyExposesOnlyOriginalShaftAndPipeFaces(GameTestHelper h){
        for(var facing:Direction.Plane.HORIZONTAL){h.setBlock(P,AllBlocks.HOSE_PULLEY.defaultBlockState().setValue(HosePulleyBlock.HORIZONTAL_FACING,facing));var state=h.getBlockState(P);
            for(var side:Direction.values()){h.assertValueEqual(AllBlocks.HOSE_PULLEY.hasShaftTowards(h.getLevel(),h.absolutePos(P),state,side),side==facing.getClockWise(),"Single shaft face");h.assertValueEqual(FluidStorage.SIDED.find(h.getLevel(),h.absolutePos(P),side)!=null,side==facing.getCounterClockWise(),"Opposite fluid port");}}
        h.succeed();
    }
    @GameTest(maxTicks=180,structure="create_test:reservoir")public void defaultTenThousandBlockThresholdUsesRealWaterPool(GameTestHelper h){
        var min=new BlockPos(1,2,1);var max=new BlockPos(22,22,22);pool(h,min,max);var pulley=pulley(h,new BlockPos(1,25,1),23);
        h.runAfterDelay(5,()->h.assertFalse(pulley.isInfinite(),"Search is incremental, not an immediate small-radius shortcut"));
        h.succeedWhen(()->{
            h.assertTrue(pulley.isInfinite(),"Default threshold reached in actual 10164-block pool");
            for(int i=0;i<10;i++)try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.extract(WATER,B,tx),B,"Bottomless bucket "+i);tx.commit();}
            int sources=0;for(var pos:BlockPos.betweenClosed(min,max))if(h.getBlockState(pos).getFluidState().isSource())sources++;
            h.assertValueEqual(sources,10164,"No world source consumed");
        });
    }
    @GameTest(maxTicks=110,structure="create_test:reservoir")public void fillingSearchMakesProgressPastOneThousandOccupiedBlocks(GameTestHelper h){
        var min=new BlockPos(1,2,1);var max=new BlockPos(10,14,10);pool(h,min,max);h.setBlock(max,Blocks.AIR);var pulley=pulley(h,new BlockPos(1,17,1),3);
        pulley.configure(new HosePulleySettings(128,-1,false,BottomlessFluidMode.ALLOW_ALL,true));
        h.succeedWhen(()->{try(var tx=Transaction.openOuter()){long accepted=pulley.storage.insert(WATER,B,tx);if(accepted>0)tx.commit();}h.assertBlockPresent(Blocks.WATER,max);});
    }
    @GameTest(maxTicks=240)public void infiniteSourceBecomesFiniteAfterPoolShrinks(GameTestHelper h){
        pool(h,ROOT,ROOT.east(2));var pulley=pulley(h,3);pulley.configure(new HosePulleySettings(128,2,false,BottomlessFluidMode.ALLOW_ALL,true));
        h.runAfterDelay(8,()->{h.assertTrue(pulley.isInfinite(),"Initially infinite");h.setBlock(ROOT.east(),Blocks.STONE);h.setBlock(ROOT.east(2),Blocks.STONE);});
        h.succeedWhen(()->{h.assertTrue(h.getTick()>170,"Periodic validation must occur");h.assertFalse(pulley.isInfinite(),"Small source reclassified");try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.extract(WATER,B,tx),B,"Last finite bucket");tx.commit();}h.assertBlockPresent(Blocks.AIR,ROOT);});
    }
    @GameTest(maxTicks=60)public void twoHosesCannotReserveSameSourceInOneTransaction(GameTestHelper h){
        pool(h,ROOT,ROOT.east());h.setBlock(ROOT.east(),Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,7));var first=pulley(h,3);var second=pulley(h,P.east(),3);
        h.runAfterDelay(8,()->{try(var tx=Transaction.openOuter()){h.assertValueEqual(first.storage.extract(WATER,B,tx),B,"First hose reserves source");h.assertValueEqual(second.storage.extract(WATER,B,tx),0L,"Second hose sees staged removal");tx.commit();}h.assertTrue(first.tank.isEmpty()&&second.tank.isEmpty(),"No duplicate buffer");h.assertBlockPresent(Blocks.AIR,ROOT);h.succeed();});
    }
    @GameTest(maxTicks=60,dimension="minecraft:the_nether")public void hoseEvaporatesWaterInNetherOnlyAfterCommit(GameTestHelper h){
        h.setBlock(ROOT,Blocks.AIR);var pulley=pulley(h,3);
        h.runAfterDelay(8,()->{try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.insert(WATER,B,tx),B,"Evaporating insert");}h.assertTrue(pulley.tank.isEmpty(),"Rollback retains no buffer");
            try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.insert(WATER,B,tx),B,"Committed evaporation");tx.commit();}h.assertBlockPresent(Blocks.AIR,ROOT);h.assertTrue(pulley.tank.isEmpty(),"Water evaporated");h.succeed();});
    }
    @GameTest(maxTicks=60)public void wrongVariantDoesNotConsumeWorldWater(GameTestHelper h){
        pool(h,ROOT,ROOT);var pulley=pulley(h,3);
        h.runAfterDelay(8,()->{try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.extract(LAVA,B,tx),0L,"Wrong fluid rejected");tx.commit();}h.assertBlockPresent(Blocks.WATER,ROOT);h.assertTrue(pulley.tank.isEmpty(),"No buffer created");h.succeed();});
    }
    @GameTest(maxTicks=65)public void bufferedFluidAndOffsetSurviveReload(GameTestHelper h){
        pool(h,ROOT,ROOT);var pulley=pulley(h,3);
        h.runAfterDelay(8,()->{
            try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.extract(WATER,B/4,tx),B/4,"Initial partial extraction");tx.commit();}
            var loaded=(HosePulleyBlockEntity)BlockEntity.loadStatic(pulley.getBlockPos(),pulley.getBlockState(),pulley.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.getLevel().removeBlockEntity(pulley.getBlockPos());h.getLevel().setBlockEntity(loaded);
            h.assertValueEqual(loaded.getOffset(),3f,"Hose depth retained");h.assertValueEqual(loaded.tank.getAmount(),3*B/4,"Buffer retained");
            try(var tx=Transaction.openOuter()){h.assertValueEqual(loaded.storage.extract(WATER,B,tx),3*B/4,"Only actual remainder available");tx.commit();}h.assertTrue(loaded.tank.isEmpty(),"No source duplicated by reload");h.succeed();
        });
    }
    @GameTest(maxTicks=70)public void fillingStateRebuildsAfterReload(GameTestHelper h){
        pool(h,ROOT,ROOT);h.setBlock(ROOT,Blocks.AIR);var pulley=pulley(h,3);
        h.runAfterDelay(8,()->{try(var tx=Transaction.openOuter()){pulley.storage.insert(WATER,B/4,tx);tx.commit();}
            var loaded=(HosePulleyBlockEntity)BlockEntity.loadStatic(pulley.getBlockPos(),pulley.getBlockState(),pulley.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());h.getLevel().removeBlockEntity(pulley.getBlockPos());h.getLevel().setBlockEntity(loaded);
        });
        h.runAfterDelay(15,()->{var loaded=h.getBlockEntity(P,HosePulleyBlockEntity.class);try(var tx=Transaction.openOuter()){h.assertValueEqual(loaded.storage.insert(WATER,3*B/4,tx),3*B/4,"Complete saved buffer");tx.commit();}h.assertBlockPresent(Blocks.WATER,ROOT);h.assertTrue(loaded.tank.isEmpty(),"Exactly one source placed");h.succeed();});
    }
    @GameTest(maxTicks=65)public void stoppedDriveRoundsOffsetAndHoldsPosition(GameTestHelper h){
        h.setBlock(ROOT,Blocks.STONE);h.setBlock(P,AllBlocks.HOSE_PULLEY);var pulley=h.getBlockEntity(P,HosePulleyBlockEntity.class);
        h.setBlock(P.east(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.WEST));var motor=h.getBlockEntity(P.east(),KineticBlockEntity.class);motor.adjustGeneratedSpeed(true);for(int i=0;i<2;i++)motor.adjustGeneratedSpeed(false);
        var stopped=new float[1];h.runAfterDelay(9,()->h.setBlock(P.east(),Blocks.AIR));h.runAfterDelay(14,()->{stopped[0]=pulley.getOffset();h.assertValueEqual(stopped[0],(float)Math.round(stopped[0]),"Rounded to block");h.assertFalse(pulley.isMoving(),"Stopped");});
        h.runAfterDelay(28,()->{h.assertValueEqual(pulley.getOffset(),stopped[0],"Unpowered position holds");h.succeed();});
    }
    @GameTest(maxTicks=65)public void disabledSourcePlacementConsumesFluidWithoutChangingWorld(GameTestHelper h){
        h.setBlock(ROOT,Blocks.AIR);var pulley=pulley(h,3);pulley.configure(new HosePulleySettings(128,10000,false,BottomlessFluidMode.ALLOW_BY_TAG,false));
        h.runAfterDelay(8,()->{try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.insert(WATER,B,tx),B,"Fluid accepted by disabled placement mode");tx.commit();}h.assertBlockPresent(Blocks.AIR,ROOT);h.assertTrue(pulley.tank.isEmpty(),"Fluid discarded as upstream setting specifies");h.succeed();});
    }
    @GameTest(maxTicks=65)public void componentVariantsDoNotMixInPulleyBuffer(GameTestHelper h){
        pool(h,ROOT,ROOT);var pulley=pulley(h,3);var named=FluidVariant.of(Fluids.WATER,net.minecraft.core.component.DataComponentPatch.builder().set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Treated")).build());
        h.runAfterDelay(8,()->{try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.insert(named,B/4,tx),B/4,"Component fluid buffered");tx.commit();}
            try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.insert(WATER,B/4,tx),0L,"Different components rejected");h.assertValueEqual(pulley.storage.extract(WATER,B,tx),0L,"World water cannot mix with buffer");}
            h.assertValueEqual(pulley.tank.getResource(),named,"Components retained");h.assertBlockPresent(Blocks.WATER,ROOT);h.succeed();
        });
    }
    @GameTest(maxTicks=180,structure="create_test:reservoir")public void defaultLargePoolFillingDiscardsIncomingWithoutAddingSources(GameTestHelper h){
        var min=new BlockPos(1,2,1);var max=new BlockPos(22,22,22);pool(h,min,max);var pulley=pulley(h,new BlockPos(1,25,1),3);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.insert(WATER,3*B/2,tx),3*B/2,"Initial full buffer");tx.commit();}
        h.succeedWhen(()->{h.assertTrue(pulley.isInfinite(),"Filling search recognizes actual 10164-block pool");try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.insert(WATER,B,tx),B,"Above-threshold pool consumes incoming fluid");tx.commit();}h.assertValueEqual(pulley.tank.getAmount(),3*B/2,"Existing buffer not duplicated or deleted");h.assertBlockPresent(Blocks.WATER,max);});
    }
    @GameTest(maxTicks=65)public void fillingDropsReplacedBlockOnlyWhenTransactionCommits(GameTestHelper h){
        pool(h,ROOT,ROOT);h.setBlock(ROOT,Blocks.TORCH);var pulley=pulley(h,3);var area=new net.minecraft.world.phys.AABB(h.absolutePos(ROOT)).inflate(1);
        h.runAfterDelay(8,()->{
            try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.insert(WATER,B,tx),B,"Torch replacement staged");}
            h.assertBlockPresent(Blocks.TORCH,ROOT);h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).isEmpty(),"No dropped item from simulation");
            try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.insert(WATER,B,tx),B,"Torch replacement committed");tx.commit();}
            h.assertBlockPresent(Blocks.WATER,ROOT);h.assertValueEqual(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).stream().filter(item->item.getItem().is(net.minecraft.world.item.Items.TORCH)).mapToLong(item->item.getItem().getCount()).sum(),1L,"Replaced torch dropped once");h.succeed();
        });
    }
    @GameTest public void wrenchRotatesPulleyPortsAndPreservesContents(GameTestHelper h){
        var pulley=pulley(h,3);try(var tx=Transaction.openOuter()){pulley.tank.insert(WATER,B/4,tx);tx.commit();}
        var state=h.getBlockState(P);var next=com.simibubi.create.content.equipment.wrench.WrenchItem.rotate(state,Direction.UP);h.setBlock(P,next);
        h.assertValueEqual(next.getValue(HosePulleyBlock.HORIZONTAL_FACING),Direction.EAST,"Horizontal rotation");h.assertTrue(h.getBlockEntity(P,HosePulleyBlockEntity.class)==pulley,"Same block entity retained");h.assertValueEqual(pulley.getOffset(),3f,"Hose depth retained");h.assertValueEqual(pulley.tank.getAmount(),B/4,"Fluid retained");h.assertTrue(HosePulleyBlock.hasPipeTowards(next,Direction.NORTH),"Pipe port rotated with body");h.succeed();
    }
    @GameTest(maxTicks=65)public void storageViewReportsBufferedAndOneAvailableWorldBucket(GameTestHelper h){
        pool(h,ROOT,ROOT.east());var pulley=pulley(h,3);
        h.runAfterDelay(8,()->{
            try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.extract(WATER,B/4,tx),B/4,"Partial extraction");tx.commit();}
            var view=pulley.storage.iterator().next();h.assertValueEqual(view.getAmount(),7*B/4,"View includes buffer and next world bucket");h.assertTrue(view.getCapacity()>=view.getAmount(),"Virtual view respects Fabric capacity invariant");
            try(var tx=Transaction.openOuter()){h.assertValueEqual(view.extract(WATER,view.getAmount(),tx),7*B/4,"Advertised quantity is extractable");tx.commit();}
            h.assertValueEqual(view.getAmount(),0L,"Same view updates after drain");h.assertTrue(pulley.tank.isEmpty(),"Buffer empty");h.succeed();
        });
    }
    @GameTest(maxTicks=70)public void replacedSourceInvalidatesOldFluidCandidateWithoutSuccessfulTransfer(GameTestHelper h){
        pool(h,ROOT,ROOT);var pulley=pulley(h,3);
        h.runAfterDelay(8,()->h.setBlock(ROOT,Blocks.LAVA));
        h.runAfterDelay(18,()->{try(var tx=Transaction.openOuter()){h.assertValueEqual(pulley.storage.extract(LAVA,B,tx),B,"Changed world fluid discovered without draining old cached fluid");tx.commit();}h.assertBlockPresent(Blocks.AIR,ROOT);h.succeed();});
    }
    @GameTest(maxTicks=150)public void pumpContinuouslyDrainsFinitePoolThroughCapacityLimitedBuffer(GameTestHelper h){
        pool(h,ROOT,ROOT.east(2));var pulley=pulley(h,3);var pump=P.west();var destination=P.west(2);
        h.setBlock(destination,AllBlocks.FLUID_TANK);h.setBlock(pump,AllBlocks.MECHANICAL_PUMP.defaultBlockState().setValue(com.simibubi.create.content.fluids.pump.PumpBlock.FACING,Direction.WEST));
        h.setBlock(pump.below(),AllBlocks.COGWHEEL.defaultBlockState().setValue(com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock.AXIS,Direction.Axis.X));h.setBlock(destination.below(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));var motor=h.getBlockEntity(destination.below(),KineticBlockEntity.class);for(int i=0;i<4;i++)motor.adjustGeneratedSpeed(false);
        var tank=h.getBlockEntity(destination,com.simibubi.create.content.fluids.tank.FluidTankBlockEntity.class);
        h.succeedWhen(()->{h.assertValueEqual(tank.getTankInventory().getAmount(),3*B,"All three buckets transferred in small pump requests");h.assertTrue(pulley.tank.isEmpty(),"No stranded remainder");for(int i=0;i<3;i++)h.assertBlockPresent(Blocks.AIR,ROOT.east(i));});
    }
}
