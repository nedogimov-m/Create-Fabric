package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.deployer.DeployerBlock;
import com.simibubi.create.content.processing.AssemblyOperatorBlockItem;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Genuine server interaction, item registration, native placement and finite survival stock. */
public final class AssemblyOperatorPlacementGameTests {
    private static final BlockPos SUPPORT=new BlockPos(2,2,2);
    private static final List<Block> OPERATORS=List.of(AllBlocks.MECHANICAL_PRESS,AllBlocks.MECHANICAL_MIXER,AllBlocks.SPOUT,AllBlocks.DEPLOYER);
    private static final List<Block> SUPPORTS=List.of(AllBlocks.BASIN,AllBlocks.DEPOT,AllBlocks.BELT,AllBlocks.WEIGHTED_EJECTOR);
    private static void fixture(GameTestHelper h,BlockState state){
        h.setBlock(SUPPORT.above(3),Blocks.AIR);h.setBlock(SUPPORT.above(2),Blocks.AIR);h.setBlock(SUPPORT.above(),Blocks.AIR);h.setBlock(SUPPORT.east(),Blocks.AIR);h.setBlock(SUPPORT,state);
    }
    private static net.minecraft.world.InteractionResult place(GameTestHelper h,ServerPlayer player,ItemStack stack,Direction face){
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);var pos=h.absolutePos(SUPPORT);
        var hit=new BlockHitResult(Vec3.atCenterOf(pos).add(face.getUnitVec3().scale(.5)),face,pos,false);
        return player.gameMode.useItemOn(player,h.getLevel(),stack,InteractionHand.MAIN_HAND,hit);
    }
    @GameTest public void fourActualRegisteredOperatorsPlaceAtSourceHeightOverEveryNativeSupportAndDebitOnce(GameTestHelper h){
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(SUPPORT)).add(0,0,2))){
            var player=joined.player();player.setXRot(0);
            for(var support:SUPPORTS)for(var operator:OPERATORS){
                fixture(h,support.defaultBlockState());player.setShiftKeyDown(support!=AllBlocks.BASIN);var actual=new ItemStack(operator,2);
                h.assertTrue(actual.getItem() instanceof AssemblyOperatorBlockItem,"Actual registered press/mixer/spout/deployer item must own source placement behavior");
                h.assertTrue(place(h,player,actual,Direction.UP).consumesAction(),"Real survival game-mode item interaction must reach original assembly placement");
                var state=h.getLevel().getBlockState(h.absolutePos(SUPPORT.above(2)));
                h.assertTrue(state.is(operator)&&h.getLevel().getBlockState(h.absolutePos(SUPPORT.above())).isAir()&&h.getLevel().getBlockState(h.absolutePos(SUPPORT)).is(support)&&actual.getCount()==1&&player.getMainHandItem()==actual,"Exactly one real operator is placed two cells above the unchanged support with a real empty working gap and one finite debit");
                if(operator==AllBlocks.DEPLOYER)h.assertTrue(state.getValue(DeployerBlock.FACING)==Direction.DOWN,"Source assembly context forces actual deployer downward even with horizontal gaze and sneak placement");
                if(support==AllBlocks.BASIN)h.assertTrue(((BasinBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(SUPPORT))).allItems().stream().allMatch(slot->slot.isResourceBlank()),"Normal basin click never consumes the held operator into basin inventory");
            }
        }h.succeed();
    }
    @GameTest public void blockedOriginalHeightRefusesWithoutAdjacentFallbackOrSurvivalDebit(GameTestHelper h){
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(SUPPORT)).add(0,0,2))){
            var player=joined.player();
            for(var support:SUPPORTS)for(var operator:OPERATORS){
                fixture(h,support.defaultBlockState());h.setBlock(SUPPORT.above(2),Blocks.OBSIDIAN);player.setShiftKeyDown(support!=AllBlocks.BASIN);var actual=new ItemStack(operator,2);
                h.assertTrue(!place(h,player,actual,Direction.UP).consumesAction()&&actual.getCount()==2&&h.getLevel().getBlockState(h.absolutePos(SUPPORT.above())).isAir()&&h.getLevel().getBlockState(h.absolutePos(SUPPORT.above(2))).is(Blocks.OBSIDIAN)&&h.getLevel().getBlockState(h.absolutePos(SUPPORT)).is(support),"Obstructed source height preserves the real item count, support, obstacle and gap; no nearer fallback placement");
            }
        }h.succeed();
    }
    @GameTest public void normalTopSideAndSlopingBeltKeepOrdinaryNativePlacement(GameTestHelper h){
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(SUPPORT)).add(0,0,2))){
            var player=joined.player();player.setShiftKeyDown(true);
            fixture(h,Blocks.STONE.defaultBlockState());var normal=new ItemStack(AllBlocks.MECHANICAL_PRESS,2);
            h.assertTrue(place(h,player,normal,Direction.UP).consumesAction()&&normal.getCount()==1&&h.getLevel().getBlockState(h.absolutePos(SUPPORT.above())).is(AllBlocks.MECHANICAL_PRESS)&&h.getLevel().getBlockState(h.absolutePos(SUPPORT.above(2))).isAir(),"Ordinary stone support keeps adjacent native placement and one real debit");
            fixture(h,AllBlocks.BASIN.defaultBlockState());var side=new ItemStack(AllBlocks.MECHANICAL_PRESS,2);
            h.assertTrue(place(h,player,side,Direction.EAST).consumesAction()&&side.getCount()==1&&h.getLevel().getBlockState(h.absolutePos(SUPPORT.east())).is(AllBlocks.MECHANICAL_PRESS)&&h.getLevel().getBlockState(h.absolutePos(SUPPORT.above(2))).isAir(),"Side interaction with a valid support remains ordinary native adjacent placement");
            fixture(h,AllBlocks.BELT.defaultBlockState().setValue(BeltBlock.SLOPE,BeltSlope.UPWARD));var slope=new ItemStack(AllBlocks.MECHANICAL_PRESS,2);
            h.assertTrue(place(h,player,slope,Direction.UP).consumesAction()&&slope.getCount()==1&&h.getLevel().getBlockState(h.absolutePos(SUPPORT.above())).is(AllBlocks.MECHANICAL_PRESS)&&h.getLevel().getBlockState(h.absolutePos(SUPPORT.above(2))).isAir(),"Only source horizontal belts select assembly height; sloping belts retain ordinary placement");
        }h.succeed();
    }
}
