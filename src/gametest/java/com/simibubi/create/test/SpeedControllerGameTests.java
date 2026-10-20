package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.*;
import com.simibubi.create.content.kinetics.speedController.*;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlock;
import com.simibubi.create.content.kinetics.crank.HandCrankBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import java.util.List;

public class SpeedControllerGameTests {
    private static final BlockPos P=new BlockPos(3,3,3);
    private static SpeedControllerBlockEntity controller(GameTestHelper h){h.setBlock(P,AllBlocks.ROTATION_SPEED_CONTROLLER.defaultBlockState().setValue(SpeedControllerBlock.HORIZONTAL_AXIS,Direction.Axis.X));h.setBlock(P.above(),AllBlocks.LARGE_COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Z));return h.getBlockEntity(P,SpeedControllerBlockEntity.class);}
    private static KineticBlockEntity kinetic(GameTestHelper h,BlockPos pos){return h.getBlockEntity(pos,KineticBlockEntity.class);}
    @GameTest(maxTicks=45)public void shaftDriveRegulatesCogAtAnyInputSpeedAndStopsWithoutPower(GameTestHelper h){
        var controller=controller(h);controller.setTargetSpeed(96);h.setBlock(P.west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));h.setBlock(P.above().south(),AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Z));
        h.runAfterDelay(4,()->{h.assertValueEqual(controller.getSpeed(),16f,"Shaft input retained");h.assertValueEqual(kinetic(h,P.above().south()).getSpeed(),96f,"Cog receives selected RPM");kinetic(h,P.west()).setGeneratedSpeed(-64);});
        h.runAfterDelay(8,()->{h.assertValueEqual(controller.getSpeed(),-64f,"Input can reverse independently");h.assertValueEqual(kinetic(h,P.above()).getSpeed(),96f,"Positive output retained");controller.setTargetSpeed(-48);});
        h.runAfterDelay(12,()->{h.assertValueEqual(kinetic(h,P.above()).getSpeed(),-48f,"Target reverses output");controller.setTargetSpeed(0);});
        h.runAfterDelay(16,()->{h.assertValueEqual(controller.getSpeed(),-64f,"Zero target does not stop input shaft");h.assertValueEqual(kinetic(h,P.above()).getSpeed(),0f,"Zero disconnects output");controller.setTargetSpeed(128);kinetic(h,P.west()).setGeneratedSpeed(0);});
        h.runAfterDelay(20,()->{h.assertValueEqual(controller.getSpeed(),0f,"Source stopped");h.assertValueEqual(kinetic(h,P.above()).getSpeed(),0f,"Controller cannot power itself");h.succeed();});
    }
    @GameTest(maxTicks=30)public void cogDriveRegulatesShaftAndRemembersSpeedAfterLoad(GameTestHelper h){
        var controller=controller(h);controller.setTargetSpeed(-72);h.setBlock(P.above().north(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.SOUTH));h.setBlock(P.east(),AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X));
        h.runAfterDelay(5,()->{h.assertValueEqual(kinetic(h,P.above()).getSpeed(),16f,"Driven cog retains input RPM");h.assertValueEqual(kinetic(h,P.east()).getSpeed(),-72f,"Controller shaft gets requested speed");var saved=controller.saveWithFullMetadata(h.getLevel().registryAccess());h.assertValueEqual(saved.getStringOr("id",""),"create:rotation_speed_controller","Original BE id");h.assertValueEqual(saved.getIntOr("ScrollValue",0),-72,"Original settings key");var restored=(SpeedControllerBlockEntity)BlockEntity.loadStatic(h.absolutePos(P),controller.getBlockState(),saved,h.getLevel().registryAccess());h.assertValueEqual(restored.getTargetSpeed(),-72,"Setting restored");h.succeed();});
    }
    @GameTest(maxTicks=35)public void onlyPerpendicularLargeHorizontalCogConnectsAndRemovalRestoresNormalShaft(GameTestHelper h){
        var controller=controller(h);controller.setTargetSpeed(64);h.setBlock(P.west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));h.setBlock(P.east(),AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X));
        h.runAfterDelay(4,()->{h.assertValueEqual(kinetic(h,P.above()).getSpeed(),64f,"Correct cog connected");h.setBlock(P.above(),AllBlocks.COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Z));});
        h.runAfterDelay(8,()->{h.assertValueEqual(kinetic(h,P.above()).getSpeed(),0f,"Small cog excluded");h.setBlock(P.above(),AllBlocks.LARGE_COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X));});
        h.runAfterDelay(12,()->{h.assertValueEqual(kinetic(h,P.above()).getSpeed(),0f,"Parallel cog excluded");h.setBlock(P.above(),AllBlocks.LARGE_COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Y));});
        h.runAfterDelay(16,()->{h.assertValueEqual(kinetic(h,P.above()).getSpeed(),0f,"Vertical cog excluded");h.setBlock(P.above(),Blocks.AIR);});
        h.runAfterDelay(20,()->{h.assertValueEqual(kinetic(h,P.east()).getSpeed(),16f,"Through-shaft remains connected without cog");h.succeed();});
    }
    @GameTest(maxTicks=30)public void regulatedMachineLoadCanOverstressCrankAndRecoverAtLowerTarget(GameTestHelper h){
        var controller=controller(h);controller.setTargetSpeed(64);h.setBlock(P.west(),AllBlocks.HAND_CRANK.defaultBlockState().setValue(HandCrankBlock.FACING,Direction.WEST));h.setBlock(P.above().south(),AllBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(MechanicalPressBlock.FACING,Direction.NORTH));
        h.runAfterDelay(2,()->kinetic(h,P.west()).turnCrank(false));
        h.runAfterDelay(4,()->{h.assertTrue(controller.isOverstressed(),"512 SU demand exceeds crank 256 SU");h.assertValueEqual(kinetic(h,P.above()).getTheoreticalSpeed(),64f,"Overload keeps target theoretical RPM");h.assertValueEqual(controller.getSpeed(),0f,"Input stalls with regulated load");controller.setTargetSpeed(16);kinetic(h,P.west()).turnCrank(false);});
        h.runAfterDelay(7,()->{h.assertFalse(controller.isOverstressed(),"Lower demand recovers");h.assertValueEqual(controller.getStressImpact(),128d,"Press load uses selected RPM");h.assertValueEqual(controller.getStressCapacity(),256d,"No synthetic controller capacity");h.assertValueEqual(kinetic(h,P.above()).getSpeed(),16f,"Machine runs at lower target");h.succeed();});
    }
    @GameTest(maxTicks=30)public void chainsPropagateAbsoluteTargetsAndStopAfterRemovingMiddleCog(GameTestHelper h){
        var first=controller(h);first.setTargetSpeed(48);var secondPos=P.above().south(2);h.setBlock(P.west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));h.setBlock(P.above().south(),AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Z));h.setBlock(secondPos,AllBlocks.ROTATION_SPEED_CONTROLLER.defaultBlockState().setValue(SpeedControllerBlock.HORIZONTAL_AXIS,Direction.Axis.Z));h.setBlock(secondPos.above(),AllBlocks.LARGE_COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X));h.getBlockEntity(secondPos,SpeedControllerBlockEntity.class).setTargetSpeed(-80);
        h.runAfterDelay(5,()->{h.assertValueEqual(kinetic(h,secondPos).getSpeed(),48f,"Second regulator receives first output");h.assertValueEqual(kinetic(h,secondPos.above()).getSpeed(),-80f,"Second absolute target independent of first");h.setBlock(P.above(),Blocks.AIR);});
        h.runAfterDelay(9,()->{h.assertValueEqual(kinetic(h,secondPos.above()).getSpeed(),0f,"Detached chain stops");h.succeed();});
    }
    @GameTest public void nativeCogPlacementHelperUsesPerpendicularAxisAndConsumesExactlyOne(GameTestHelper h){
        h.setBlock(P,AllBlocks.ROTATION_SPEED_CONTROLLER.defaultBlockState().setValue(SpeedControllerBlock.HORIZONTAL_AXIS,Direction.Axis.X));var player=h.makeMockPlayer(GameType.SURVIVAL);var stack=new ItemStack(AllBlocks.LARGE_COGWHEEL,2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);var pos=h.absolutePos(P);var result=h.getBlockState(P).useItemOn(stack,h.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,0.5,0),Direction.UP,pos,false));
        h.assertTrue(result.consumesAction(),"Helper uses native BlockItem placement");h.assertBlockPresent(AllBlocks.LARGE_COGWHEEL,P.above());h.assertValueEqual(h.getBlockState(P.above()).getValue(ShaftBlock.AXIS),Direction.Axis.Z,"Helper aligns cog perpendicular to controller");h.assertValueEqual(stack.getCount(),1,"One gear consumed");h.succeed();
    }
    @GameTest public void controllerPlacementUnderExistingCogChoosesPerpendicularAxis(GameTestHelper h){
        var player=h.makeMockPlayer(GameType.SURVIVAL);h.setBlock(P.below(),Blocks.STONE);var pos=h.absolutePos(P.below());
        for(var axis:new Direction.Axis[]{Direction.Axis.X,Direction.Axis.Z}){h.setBlock(P.above(),AllBlocks.LARGE_COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,axis));var stack=new ItemStack(AllBlocks.ROTATION_SPEED_CONTROLLER);var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(pos).add(0,0.5,0),Direction.UP,pos,false));var state=AllBlocks.ROTATION_SPEED_CONTROLLER.getStateForPlacement(context);h.assertTrue(state.getValue(SpeedControllerBlock.HORIZONTAL_AXIS)!=axis,"Placement honors existing cog axis");}h.succeed();
    }
    @GameTest public void settingsPacketsValidateReachPermissionsRangeAndSupportedBlock(GameTestHelper h){
        var controller=controller(h);var player=h.makeMockPlayer(GameType.SURVIVAL);var pos=h.absolutePos(P);player.setPos(Vec3.atCenterOf(pos).add(0,0,2));
        h.assertTrue(KineticSpeedSettings.apply(h.getLevel(),player,new KineticSpeedSettings.SetSpeed(pos,-127)),"Near survival player can set fine RPM");h.assertValueEqual(controller.getTargetSpeed(),-127,"Signed exact setting");h.assertFalse(KineticSpeedSettings.apply(h.getLevel(),player,new KineticSpeedSettings.SetSpeed(pos,257)),"Invalid range rejected");
        h.setBlock(P.east(),Blocks.CHEST);h.assertFalse(KineticSpeedSettings.apply(h.getLevel(),player,new KineticSpeedSettings.SetSpeed(pos.east(),64)),"Arbitrary block mutation rejected");player.setPos(Vec3.atCenterOf(pos).add(0,0,100));h.assertFalse(KineticSpeedSettings.apply(h.getLevel(),player,new KineticSpeedSettings.SetSpeed(pos,64)),"Remote mutation rejected");
        var spectator=h.makeMockPlayer(GameType.SPECTATOR);spectator.setPos(Vec3.atCenterOf(pos));h.assertFalse(KineticSpeedSettings.apply(h.getLevel(),spectator,new KineticSpeedSettings.SetSpeed(pos,64)),"Spectator rejected");h.succeed();
    }
    @GameTest public void controllerRecipeUsesOriginalPrecisionMechanismAndBrassCasing(GameTestHelper h){
        var input=net.minecraft.world.item.crafting.CraftingInput.of(1,2,List.of(new ItemStack(AllItems.PRECISION_MECHANISM),new ItemStack(AllBlocks.BRASS_CASING)));var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,h.getLevel());h.assertTrue(recipe.isPresent()&&recipe.get().value().assemble(input).is(AllBlocks.ROTATION_SPEED_CONTROLLER.asItem()),"Original crafting recipe");h.succeed();
    }
}
