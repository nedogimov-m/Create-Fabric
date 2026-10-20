package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import com.simibubi.create.content.kinetics.motor.*;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.test.GeneratingKineticTestFixtures.Generator;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native generator/solver/factory/save integration cases, UNCOMPILED and UNRUN. */
public final class GeneratingKineticGameTests {
    private static final String STRUCTURE="create_test:reservoir";
    private static final BlockPos P=new BlockPos(6,5,6),S=P.above();
    private static Generator setup(GameTestHelper h){
        h.setBlock(P,GeneratingKineticTestFixtures.BLOCK);
        h.setBlock(S,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Y));
        return h.getBlockEntity(P,Generator.class);
    }
    @GameTest(structure=STRUCTURE,maxTicks=30) public void sourceAddonFractionalGeneratorDrivesActualNativeShaftAndProvenance(GameTestHelper h){
        var source=setup(h);
        h.runAfterDelay(8,()->{
            var shaft=h.getBlockEntity(S,KineticBlockEntity.class);
            h.assertValueEqual(source.getGeneratedSpeed(),2.5f,"Source float output remains fractional");
            h.assertValueEqual(shaft.getSpeed(),2.5f,"Canonical native solver consumes float without narrowing");
            h.assertTrue(shaft.getGeneratingSource().equals(source.getBlockPos())&&shaft.getSource().equals(source.getBlockPos()),"Real generator and upstream provenance belong to actual registered source");
            h.assertTrue(Math.abs(shaft.getStressCapacity()-100)<.001&&source.initialized==1,"Actual source capacity and once-only Smart initialize reach real network");
            source.generate(-1.25f);
        });
        h.runAfterDelay(16,()->{h.assertValueEqual(h.getBlockEntity(S,KineticBlockEntity.class).getSpeed(),-1.25f,"Actual signed fractional update propagates");h.succeed();});
    }
    @GameTest(structure=STRUCTURE,maxTicks=35) public void protectedSourceCapacityNotificationChangesTheActualCanonicalStressGraph(GameTestHelper h){
        var source=setup(h);
        h.runAfterDelay(8,()->source.notifyCapacity(0));
        h.runAfterDelay(14,()->{var shaft=h.getBlockEntity(S,KineticBlockEntity.class);h.assertTrue(shaft.isOverstressed()&&shaft.getSpeed()==0,"Actual supplied zero capacity stops the powered native component");source.notifyCapacity(80);});
        h.runAfterDelay(22,()->{var shaft=h.getBlockEntity(S,KineticBlockEntity.class);h.assertValueEqual(shaft.getSpeed(),2.5f,"Same actual generator resumes after supplied capacity update");h.assertTrue(Math.abs(shaft.getStressCapacity()-200)<.001,"Supplied source capacity argument reaches canonical graph");h.succeed();});
    }
    @GameTest(structure=STRUCTURE) public void sourceFractionalProviderAndActualTypeSurviveNativeFactorySaveReload(GameTestHelper h){
        var source=setup(h);source.generate(-3.75f);
        var data=source.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored=BlockEntity.loadStatic(source.getBlockPos(),source.getBlockState(),data,h.getLevel().registryAccess());
        h.assertTrue(restored instanceof Generator&&restored.getType()==GeneratingKineticTestFixtures.TYPE,"Native registry factory creates same genuine generating subclass");
        h.assertValueEqual(((Generator)restored).getGeneratedSpeed(),-3.75f,"Actual float provider state persists before level publication");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void nativeMotorAndCrankFactoriesCreateRealGeneratingOwners(GameTestHelper h){
        h.setBlock(P,AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.WEST));
        var motor=h.getBlockEntity(P,CreativeMotorBlockEntity.class);motor.setGeneratedSpeed(-37);
        h.assertTrue(motor instanceof GeneratingKineticBlockEntity&&motor.getType()==AllBlockEntityTypes.MOTOR,"Actual placed motor has genuine generating superclass");
        h.assertValueEqual(motor.getGeneratedSpeedSetting(),-37,"Original integer editing value remains canonical");
        h.assertValueEqual(motor.getGeneratedSpeed(),37f,"Source generator output includes real facing direction");
        var state=AllBlocks.HAND_CRANK.defaultBlockState();
        var crank=AllBlockEntityTypes.HAND_CRANK.create(P,state);
        h.assertTrue(crank instanceof HandCrankBlockEntity&&crank instanceof GeneratingKineticBlockEntity,"Actual native hand-crank registry factory preserves source owner chain");h.succeed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=30) public void removedActualAddonSourceCannotBeReinsertedByALateGenerationNotification(GameTestHelper h){
        var source=setup(h);
        h.runAfterDelay(8,()->{h.setBlock(P,net.minecraft.world.level.block.Blocks.AIR);source.generate(7.5f);});
        h.runAfterDelay(16,()->{var shaft=h.getBlockEntity(S,KineticBlockEntity.class);h.assertTrue(shaft.getSpeed()==0&&shaft.getGeneratingSource()==null,"Late source API callback cannot resurrect removed native generator/provenance");h.succeed();});
    }
}
