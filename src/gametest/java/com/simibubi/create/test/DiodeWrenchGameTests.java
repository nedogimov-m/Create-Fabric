package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.redstone.diodes.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Supplemental regression coverage for actual shared native diode wrench interactions. STAGED, UNRUN. */
public class DiodeWrenchGameTests {
    private static final BlockPos P=new BlockPos(4,4,4);
    private static BlockState state(GameTestHelper h){return h.getBlockState(P);}
    private static void place(GameTestHelper h,Block block){h.setBlock(P.below(),Blocks.STONE);h.setBlock(P,block.defaultBlockState().setValue(DiodeBlock.FACING,Direction.NORTH));}
    private static void wrench(GameTestHelper h,Direction face){
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.WRENCH));
        var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)).relative(face,.5),face,h.absolutePos(P),false);
        h.assertTrue(AllItems.WRENCH.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Real native wrench interaction accepted");
    }
    @GameTest(maxTicks=20)public void topWrenchNotifiesBothTheOldAndNewPhysicalOutputs(GameTestHelper h){
        place(h,AllBlocks.POWERED_TOGGLE_LATCH);h.setBlock(P.south(),Blocks.REDSTONE_LAMP);h.setBlock(P.west(),Blocks.REDSTONE_LAMP);
        var player=h.makeMockPlayer(GameType.SURVIVAL);state(h).useWithoutItem(h.getLevel(),player,new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)),Direction.UP,h.absolutePos(P),false));
        h.assertTrue(h.getBlockState(P.south()).getValue(RedstoneLampBlock.LIT),"Initial south lamp received actual latch output");
        wrench(h,Direction.UP);h.assertValueEqual(state(h).getValue(DiodeBlock.FACING),Direction.EAST,"Top face turns input north to east");
        h.assertTrue(state(h).getValue(ToggleLatchBlock.POWERING)&&h.getBlockState(P.west()).getValue(RedstoneLampBlock.LIT),"Remembered output now powers the physical west lamp");
        h.runAfterDelay(8,()->{h.assertFalse(h.getBlockState(P.south()).getValue(RedstoneLampBlock.LIT),"Old output receives off notification and completes native lamp delay");h.assertTrue(h.getBlockState(P.west()).getValue(RedstoneLampBlock.LIT),"New output remains powered");h.succeed();});
    }
    @GameTest public void sideWrenchKeepsTheSourceHorizontalDiodeFacing(GameTestHelper h){
        place(h,AllBlocks.PULSE_EXTENDER);var original=state(h);for(var face:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}){wrench(h,face);h.assertTrue(state(h)==original,"Source horizontal diode does not turn on side face "+face);}h.succeed();
    }
    @GameTest public void bottomWrenchPreservesTheSameBlockEntityTimingAndInversion(GameTestHelper h){
        place(h,AllBlocks.PULSE_REPEATER);var entity=h.getBlockEntity(P,BrassDiodeBlockEntity.class);entity.setMaxState(37);h.setBlock(P,state(h).setValue(BrassDiodeBlock.INVERTED,true));
        wrench(h,Direction.DOWN);h.assertValueEqual(state(h).getValue(DiodeBlock.FACING),Direction.EAST,"Bottom face follows source clockwise Y rotation");
        h.assertTrue(h.getBlockEntity(P,BrassDiodeBlockEntity.class)==entity&&entity.getMaxState()==37&&state(h).getValue(BrassDiodeBlock.INVERTED),"Actual timing owner and inversion survive rotation");h.succeed();
    }
}
