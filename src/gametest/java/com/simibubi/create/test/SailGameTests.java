package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.bearing.SailBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Staged source canvas behavior; run only after all colored aliases/models/tags are integrated. */
public class SailGameTests {
    private static final BlockPos P=new BlockPos(4,4,4);
    private static BlockState sail(DyeColor color){return AllBlocks.DYED_SAILS.get(color).defaultBlockState().setValue(SailBlock.FACING,Direction.UP);}
    private static void dye(GameTestHelper h,BlockPos pos,DyeColor color){var state=h.getBlockState(pos);((SailBlock)state.getBlock()).applyDye(state,h.getLevel(),h.absolutePos(pos),Vec3.atCenterOf(h.absolutePos(pos)),color);}
    @GameTest public void coloredBlocksShareTheWhiteItemAndNativePickResult(GameTestHelper h){
        for(var color:DyeColor.values()){
            var block=AllBlocks.DYED_SAILS.get(color);h.assertTrue(block.asItem()==AllBlocks.SAIL.asItem(),"Colored canvas shares source white item identity");
            h.setBlock(P,sail(color));h.assertTrue(h.getBlockState(P).getCloneItemStack(h.getLevel(),h.absolutePos(P),false).is(AllBlocks.SAIL.asItem()),"Native pick returns blank sail");
        }h.succeed();
    }
    @GameTest public void firstDyeUseOnlyChangesTheSelectedCanvas(GameTestHelper h){h.setBlock(P,sail(DyeColor.WHITE));h.setBlock(P.east(),sail(DyeColor.RED));dye(h,P,DyeColor.BLUE);h.assertTrue(h.getBlockState(P).is(AllBlocks.DYED_SAILS.get(DyeColor.BLUE))&&h.getBlockState(P.east()).is(AllBlocks.DYED_SAILS.get(DyeColor.RED)),"Source first-use behavior");h.succeed();}
    @GameTest public void repeatedDyeUseChangesOneEligibleNeighbor(GameTestHelper h){h.setBlock(P,sail(DyeColor.BLUE));h.setBlock(P.east(),sail(DyeColor.RED));h.setBlock(P.west(),sail(DyeColor.RED));var state=h.getBlockState(P);((SailBlock)state.getBlock()).applyDye(state,h.getLevel(),h.absolutePos(P),Vec3.atCenterOf(h.absolutePos(P)).add(.49,0,0),DyeColor.BLUE);h.assertTrue(h.getBlockState(P.east()).is(AllBlocks.DYED_SAILS.get(DyeColor.BLUE))&&h.getBlockState(P.west()).is(AllBlocks.DYED_SAILS.get(DyeColor.RED)),"Nearest eligible plane neighbor changes alone");h.succeed();}
    @GameTest public void floodFillReachesFurtherSamePlaneCanvasButSkipsFrames(GameTestHelper h){h.setBlock(P,sail(DyeColor.BLUE));h.setBlock(P.east(),sail(DyeColor.BLUE));h.setBlock(P.east(2),sail(DyeColor.RED));h.setBlock(P.west(),AllBlocks.SAIL_FRAME.defaultBlockState().setValue(SailBlock.FACING,Direction.UP));h.setBlock(P.west(2),sail(DyeColor.RED));dye(h,P,DyeColor.BLUE);h.assertTrue(h.getBlockState(P.east(2)).is(AllBlocks.DYED_SAILS.get(DyeColor.BLUE))&&h.getBlockState(P.west()).is(AllBlocks.SAIL_FRAME)&&h.getBlockState(P.west(2)).is(AllBlocks.DYED_SAILS.get(DyeColor.RED)),"Source bounded connected-plane fill cannot paint empty frames or cross them");h.succeed();}
    @GameTest public void dyePreservesFacingAndCannotCrossAnotherPlane(GameTestHelper h){h.setBlock(P,sail(DyeColor.BLUE));h.setBlock(P.east(),sail(DyeColor.BLUE));h.setBlock(P.east(2),sail(DyeColor.RED).setValue(SailBlock.FACING,Direction.NORTH));dye(h,P,DyeColor.BLUE);h.assertTrue(h.getBlockState(P.east(2)).is(AllBlocks.DYED_SAILS.get(DyeColor.RED))&&h.getBlockState(P.east(2)).getValue(SailBlock.FACING)==Direction.NORTH,"Perpendicular canvas is not part of source plane");h.succeed();}
    @GameTest public void shearsRemoveCanvasWithoutConsumingOrDamagingTheTool(GameTestHelper h){
        h.setBlock(P,sail(DyeColor.RED));var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHEARS));var tool=player.getMainHandItem().copy();var pos=h.absolutePos(P);
        h.getBlockState(P).useItemOn(player.getMainHandItem(),h.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
        h.assertTrue(h.getBlockState(P).is(AllBlocks.SAIL_FRAME)&&ItemStack.isSameItemSameComponents(tool,player.getMainHandItem()),"Source shears action converts to frame without tool consumption");h.succeed();
    }
    @GameTest public void ordinaryDyeInteractionDoesNotConsumeTheNativeDye(GameTestHelper h){
        h.setBlock(P,sail(DyeColor.WHITE));var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DYE.pick(DyeColor.BLUE),9));var pos=h.absolutePos(P);
        h.getBlockState(P).useItemOn(player.getMainHandItem(),h.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
        h.assertTrue(h.getBlockState(P).is(AllBlocks.DYED_SAILS.get(DyeColor.BLUE))&&player.getMainHandItem().getCount()==9,"Modern DYE component retains source no-consumption behavior");h.succeed();
    }
    @GameTest public void frameHoleAndCanvasCollisionDifferInEveryOrientation(GameTestHelper h){
        for(var facing:Direction.values()){
            var frame=AllBlocks.SAIL_FRAME.defaultBlockState().setValue(SailBlock.FACING,facing);var canvas=sail(DyeColor.WHITE).setValue(SailBlock.FACING,facing);var at=h.absolutePos(P);var normal=facing.getUnitVec3();var center=Vec3.atCenterOf(at);
            var start=center.add(normal);var end=center.subtract(normal);
            h.assertTrue(frame.getCollisionShape(h.getLevel(),at).clip(start,end,at)==null,"Source frame has an actual central hole "+facing);
            h.assertTrue(canvas.getCollisionShape(h.getLevel(),at).clip(start,end,at)!=null,"Canvas retains thin collidable face "+facing);
        }h.succeed();
    }
    @GameTest public void canvasUsesNativeRestitutionRatherThanAnOldDoubleBounceCallback(GameTestHelper h){h.assertTrue(Math.abs(AllBlocks.SAIL.getBounceRestitution()-.26f)<.0001&&AllBlocks.SAIL.getFallDistanceReduction()==1&&AllBlocks.SAIL_FRAME.getBounceRestitution()==0,"Native entity collision applies source .26 restitution and automatic .8 nonliving factor once");h.succeed();}
    @GameTest public void unknownDyePositionsNeverCreateAChunk(GameTestHelper h){var far=new BlockPos(2000000,60,2000000);AllBlocks.SAIL.applyDye(sail(DyeColor.WHITE),h.getLevel(),far,Vec3.atCenterOf(far),DyeColor.BLUE);h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Unknown target stays unloaded");h.succeed();}
}
