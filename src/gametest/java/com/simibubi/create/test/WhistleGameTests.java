package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.decoration.steamWhistle.*;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlock.WhistleSize;
import com.simibubi.create.content.decoration.steamWhistle.WhistleExtenderBlock.WhistleExtenderShape;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;

public class WhistleGameTests {
    private static final BlockPos P=new BlockPos(3,3,3);
    private static WhistleBlockEntity whistle(GameTestHelper h){h.setBlock(P.below(),AllBlocks.FLUID_TANK);h.setBlock(P.below(2),Blocks.MAGMA_BLOCK);h.setBlock(P,AllBlocks.STEAM_WHISTLE);return h.getBlockEntity(P,WhistleBlockEntity.class);}
    private static void wrench(GameTestHelper h,BlockPos pos,boolean shift,double hitHeight){
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setShiftKeyDown(shift);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.WRENCH));var absolute=h.absolutePos(pos);
        var result=AllItems.WRENCH.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atLowerCornerOf(absolute).add(0.5,hitHeight,0.5),Direction.UP,absolute,false)));
        h.assertTrue(result.consumesAction(),"Wrench interaction accepted");
    }
    @GameTest(maxTicks=30)public void whistleAloneActivatesBoilerWithHeatAndNoWater(GameTestHelper h){
        var whistle=whistle(h);h.setBlock(P.east(),Blocks.REDSTONE_BLOCK);
        h.succeedWhen(()->{var tank=whistle.getTank();h.assertTrue(tank!=null&&tank.boiler.isActive(),"Whistle activates boiler");h.assertValueEqual(tank.boiler.attachedWhistles,1,"One attached whistle");h.assertValueEqual(tank.boiler.attachedEngines,0,"No engine needed");h.assertTrue(tank.boiler.passiveHeat&&whistle.isSounding(),"Passive heater can sound whistle without water");h.assertValueEqual(tank.boiler.waterSupply,0f,"No water required");});
    }
    @GameTest(maxTicks=35)public void redstoneAndHeatBothRequiredToSound(GameTestHelper h){
        var whistle=whistle(h);h.runAfterDelay(8,()->{h.assertFalse(whistle.isSounding(),"Heat alone is silent");h.setBlock(P.east(),Blocks.REDSTONE_BLOCK);});
        h.runAfterDelay(13,()->{h.assertTrue(whistle.isSounding(),"Redstone and heat sound whistle");h.setBlock(P.below(2),Blocks.STONE);});
        h.runAfterDelay(30,()->{h.assertFalse(whistle.isSounding(),"No heater no sound");h.succeed();});
    }
    @GameTest(maxTicks=35)public void allWallAttachmentDirectionsFindSameTankController(GameTestHelper h){
        h.setBlock(P,AllBlocks.FLUID_TANK);h.setBlock(P.below(),Blocks.MAGMA_BLOCK);
        for(var side:Direction.Plane.HORIZONTAL)h.setBlock(P.relative(side),AllBlocks.STEAM_WHISTLE.defaultBlockState().setValue(WhistleBlock.WALL,true).setValue(WhistleBlock.FACING,side.getOpposite()));
        h.runAfterDelay(10,()->{var tank=h.getBlockEntity(P,FluidTankBlockEntity.class);h.assertValueEqual(tank.boiler.attachedWhistles,4,"Four wall whistles counted");for(var side:Direction.Plane.HORIZONTAL)h.assertTrue(h.getBlockEntity(P.relative(side),WhistleBlockEntity.class).getTank()==tank,"Wall attachment resolves same tank");h.succeed();});
    }
    @GameTest(maxTicks=35,structure="create_test:reservoir")public void growingExtensionChangesPitchInSemitonesAndStopsAfterSixBlocks(GameTestHelper h){
        var whistle=whistle(h);for(int i=0;i<12;i++)WhistleBlock.incrementSize(h.getLevel(),h.absolutePos(P));
        h.runAfterDelay(8,()->{h.assertValueEqual(whistle.getPitch(),12,"Six double extensions span twelve semitones");for(int y=1;y<=6;y++)h.assertBlockPresent(AllBlocks.STEAM_WHISTLE_EXTENSION,P.above(y));WhistleBlock.incrementSize(h.getLevel(),h.absolutePos(P));h.assertBlockPresent(Blocks.AIR,P.above(7));h.succeed();});
    }
    @GameTest(maxTicks=30)public void extensionsConnectAndCopyOctaveFromBase(GameTestHelper h){
        var whistle=whistle(h);for(int i=0;i<3;i++)WhistleBlock.incrementSize(h.getLevel(),h.absolutePos(P));wrench(h,P.above(),false,0.4);
        h.runAfterDelay(8,()->{h.assertValueEqual(whistle.getPitch(),3,"Three half lengths");h.assertValueEqual(h.getBlockState(P).getValue(WhistleBlock.SIZE),WhistleSize.LARGE,"Wrench on extension changes base octave");h.assertValueEqual(h.getBlockState(P.above()).getValue(WhistleExtenderBlock.SHAPE),WhistleExtenderShape.DOUBLE_CONNECTED,"Lower extension hides top rim");for(int y=1;y<=2;y++)h.assertValueEqual(h.getBlockState(P.above(y)).getValue(WhistleBlock.SIZE),WhistleSize.LARGE,"Extension octave follows root");h.succeed();});
    }
    @GameTest(maxTicks=35)public void upperSneakWrenchShortensAndRemovesUnsupportedExtensions(GameTestHelper h){
        var whistle=whistle(h);for(int i=0;i<5;i++)WhistleBlock.incrementSize(h.getLevel(),h.absolutePos(P));wrench(h,P.above(),true,0.75);
        h.runAfterDelay(8,()->{h.assertValueEqual(h.getBlockState(P.above()).getValue(WhistleExtenderBlock.SHAPE),WhistleExtenderShape.SINGLE,"Upper wrench keeps a half length");h.assertBlockPresent(Blocks.AIR,P.above(2));h.assertBlockPresent(Blocks.AIR,P.above(3));h.assertValueEqual(whistle.getPitch(),1,"Pitch reflects shortened extension");h.succeed();});
    }
    @GameTest(maxTicks=30)public void lowerSneakWrenchRemovesExtensionWithNoItemDrop(GameTestHelper h){
        var whistle=whistle(h);for(int i=0;i<4;i++)WhistleBlock.incrementSize(h.getLevel(),h.absolutePos(P));wrench(h,P.above(),true,0.25);
        h.runAfterDelay(8,()->{h.assertBlockPresent(Blocks.AIR,P.above());h.assertBlockPresent(Blocks.AIR,P.above(2));h.assertValueEqual(whistle.getPitch(),0,"Base pitch restored");h.assertTrue(Block.getDrops(h.getBlockState(P),h.getLevel(),h.absolutePos(P),whistle).size()==1,"Base still drops one whistle");h.succeed();});
    }
    @GameTest(maxTicks=30)public void removedTankBreaksWhistleAndItsExtensions(GameTestHelper h){
        whistle(h);for(int i=0;i<3;i++)WhistleBlock.incrementSize(h.getLevel(),h.absolutePos(P));h.setBlock(P.below(),Blocks.AIR);
        h.runAfterDelay(8,()->{h.assertBlockPresent(Blocks.AIR,P);h.assertBlockPresent(Blocks.AIR,P.above());h.assertBlockPresent(Blocks.AIR,P.above(2));h.succeed();});
    }
    @GameTest(maxTicks=30)public void extensionInteractionReachesRootWithoutConsumingWhistleItem(GameTestHelper h){
        var whistle=whistle(h);WhistleBlock.incrementSize(h.getLevel(),h.absolutePos(P));var player=h.makeMockPlayer(GameType.SURVIVAL);var stack=new ItemStack(AllBlocks.STEAM_WHISTLE);player.setItemInHand(InteractionHand.MAIN_HAND,stack);var pos=h.absolutePos(P.above());
        h.getBlockState(P.above()).useItemOn(stack,h.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
        h.runAfterDelay(8,()->{h.assertValueEqual(whistle.getPitch(),2,"Extension item interaction lengthens root");h.assertValueEqual(stack.getCount(),1,"Original free length adjustment preserves held item");h.succeed();});
    }
    @GameTest(maxTicks=30)public void pitchAndOctaveSurviveSave(GameTestHelper h){
        var whistle=whistle(h);WhistleBlock.incrementSize(h.getLevel(),h.absolutePos(P));wrench(h,P,false,0.5);
        h.runAfterDelay(8,()->{var restored=(WhistleBlockEntity)BlockEntity.loadStatic(whistle.getBlockPos(),whistle.getBlockState(),whistle.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertValueEqual(restored.getPitchId(),201,"Semitone and octave persisted");h.succeed();});
    }
}
