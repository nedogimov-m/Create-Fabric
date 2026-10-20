package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.bearing.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;

/** Staged, uncompiled tests: actual zero-input assembly and native generator ownership. */
public class WindmillGameTests {
    private static final BlockPos B = new BlockPos(4, 3, 4), P = B.above();
    private static WindmillBearingBlockEntity setup(GameTestHelper h, int count) {
        h.setBlock(B, AllBlocks.WINDMILL_BEARING.defaultBlockState().setValue(BearingBlock.FACING, Direction.UP)); int placed = 0;
        // Root cell first, then a plane entirely inside the ordinary eight-block fixture.
        h.setBlock(P, Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE)); placed++;
        for (int x = -1; x <= 1 && placed < count; x++) for (int z = -1; z <= 1 && placed < count; z++) if (x != 0 || z != 0) { h.setBlock(P.offset(x, 0, z), Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE)); placed++; }
        h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(), SuperGlueEntity.span(h.absolutePos(P.offset(-1,0,-1)), h.absolutePos(P.offset(1,0,1)))));
        return h.getBlockEntity(B, WindmillBearingBlockEntity.class);
    }
    @GameTest public void generatedSpeedHasSourceIntegerThresholdsAndCap(GameTestHelper h) {
        int[][] cases = {{0,1},{7,1},{8,1},{15,1},{16,2},{127,15},{128,16},{2048,16}};
        for (var pair : cases) {
            h.assertValueEqual(WindmillBearingBlockEntity.speedForSails(pair[0],8,WindmillBearingBlockEntity.RotationDirection.CLOCKWISE),pair[1],"Source sail threshold");
            h.assertValueEqual(WindmillBearingBlockEntity.speedForSails(pair[0],8,WindmillBearingBlockEntity.RotationDirection.COUNTER_CLOCKWISE),-pair[1],"Source opposite direction");
        } h.assertValueEqual(WindmillBearingBlockEntity.speedForSails(12,3,WindmillBearingBlockEntity.RotationDirection.CLOCKWISE),4,"Configured divisor"); h.succeed();
    }
    @GameTest public void belowMinimumDoesNotTakeAnyWorldSails(GameTestHelper h) {
        var bearing = setup(h,7); bearing.assemble();
        h.assertTrue(!bearing.isRunning() && bearing.getMovedContraption()==null && bearing.getLastAssemblyException()!=null,"Source sail minimum rejected");
        int count=0;for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(h.getBlockState(P.offset(x,0,z)).is(Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE)))count++;
        h.assertValueEqual(count,7,"Failed assembly leaves all sail blocks intact");h.succeed();
    }
    @GameTest(maxTicks=40) public void zeroInputWindmillStartsAndDrivesActualShaft(GameTestHelper h) {
        var bearing=setup(h,8);h.setBlock(B.below(),AllBlocks.SHAFT.defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.Y));bearing.requestAssembly();
        h.runAfterDelay(12,()->{
            h.assertTrue(bearing.isRunning() && bearing.getMovedContraption()!=null,"Manual windmill request works without initial power");
            h.assertValueEqual(((BearingContraption)bearing.getMovedContraption().getContraption()).getSailBlocks(),8,"All eight original wool sails captured");
            h.assertValueEqual(bearing.getGeneratedSpeed(),1f,"Source generation");h.assertTrue(Math.abs(h.getBlockEntity(B.below(),KineticBlockEntity.class).getSpeed()-1)<.001,"Native solver actually drives the output shaft");
            h.assertTrue(Math.abs(bearing.getStressCapacity()-512)<.001,"Real registered source capacity enters the graph");h.succeed();
        });
    }
    @GameTest(maxTicks=40) public void attachedInputDoesNotAutomaticallyAssembleWindmill(GameTestHelper h) {
        var bearing=setup(h,8);h.setBlock(B.below(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING,Direction.UP));
        h.runAfterDelay(10,()->{h.assertTrue(!bearing.isRunning()&&bearing.getMovedContraption()==null,"Input graph changes do not auto-toggle source windmills");bearing.requestAssembly();});
        h.runAfterDelay(20,()->{h.assertTrue(bearing.isRunning(),"Explicit request still assembles an externally driven windmill");h.succeed();});
    }
    @GameTest public void changingDirectionChangesTheActualSignedSource(GameTestHelper h) {
        var bearing=setup(h,8);bearing.assemble();h.assertTrue(bearing.isRunning(),"Assembly");bearing.setMovementDirection(WindmillBearingBlockEntity.RotationDirection.COUNTER_CLOCKWISE);
        h.assertValueEqual(bearing.getGeneratedSpeed(),-1f,"Signed generation changed");h.assertTrue(bearing.getDirectionalGeneratedSpeed()==-1,"Native graph source uses the same signed RPM");h.succeed();
    }
    @GameTest(maxTicks=30) public void nativeEmptyHandDispatchStartsAndStopsTheRealWindmill(GameTestHelper h) {
        var bearing=setup(h,8);var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);h.useBlock(B,player);
        h.runAfterDelay(4,()->{
            h.assertTrue(bearing.isRunning()&&bearing.getMovedContraption()!=null,"Native item-to-empty-hand dispatch reaches the zero-input controller request");
            h.useBlock(B,player);h.assertTrue(!bearing.isRunning()&&bearing.getMovedContraption()==null,"The second native empty-hand dispatch restores the same owner");
            h.assertBlockPresent(Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE),P);h.succeed();
        });
    }
    @GameTest public void nativeLoadBeforeLevelRetainsDirectionAndLastGeneratedFallback(GameTestHelper h) {
        var bearing=setup(h,8);bearing.assemble();bearing.setMovementDirection(WindmillBearingBlockEntity.RotationDirection.COUNTER_CLOCKWISE);
        var loaded=(WindmillBearingBlockEntity)BlockEntity.loadStatic(h.absolutePos(B),h.getBlockState(B),bearing.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(loaded.getLevel()==null&&loaded.getMovementDirection()==WindmillBearingBlockEntity.RotationDirection.COUNTER_CLOCKWISE,"Native constructor/load ordering preserves direction");
        h.assertValueEqual(loaded.getGeneratedSpeed(),-1f,"Unpublished rotor uses persisted source RPM rather than default creative RPM");h.succeed();
    }
    @GameTest public void disassemblyForMovementRestoresSailsAndPersistsReassemblyQueue(GameTestHelper h) {
        var bearing=setup(h,8);bearing.assemble();h.assertTrue(bearing.disassembleForMovement(),"World restoration accepted");
        h.assertTrue(!bearing.isRunning()&&bearing.hasQueuedReassembly(),"Source move queue set only after restoration");h.assertBlockPresent(Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE),P);
        var saved=bearing.saveWithFullMetadata(h.getLevel().registryAccess());var moved=new WindmillBearingBlockEntity(h.absolutePos(B),h.getBlockState(B));moved.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),saved));
        h.assertTrue(moved.hasQueuedReassembly()&&moved.getGeneratedSpeed()==0,"Native save retains queue without pretending generator is running");h.succeed();
    }
    @GameTest public void manualDisassemblyRestoresAllEightOriginalSails(GameTestHelper h) {
        var bearing=setup(h,8);bearing.assemble();h.assertTrue(bearing.disassemble(),"Manual restoration");int count=0;
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(h.getBlockState(P.offset(x,0,z)).is(Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE)))count++;
        h.assertTrue(count==8&&!bearing.isRunning()&&bearing.getGeneratedSpeed()==0,"All source sails restored and generation stopped");h.succeed();
    }
}
