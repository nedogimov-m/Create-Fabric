package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.crafter.*;
import com.simibubi.create.content.kinetics.deployer.*;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;

public final class ReportedInteractionGameTests {
    private static final BlockPos P = new BlockPos(4,4,4);
    @GameTest public void nativeEmptyHandExtractsCrafterIngredientAndCover(GameTestHelper h) {
        h.setBlock(P, AllBlocks.MECHANICAL_CRAFTER.defaultBlockState().setValue(MechanicalCrafterBlock.HORIZONTAL_FACING, Direction.SOUTH));
        var crafter=h.getBlockEntity(P,MechanicalCrafterBlockEntity.class);
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(P)).add(0,0,2))) {
            var player=joined.player();var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)).add(0,0,.5),Direction.SOUTH,h.absolutePos(P),false);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND,2));
            h.assertTrue(player.gameMode.useItemOn(player,h.getLevel(),player.getMainHandItem(),InteractionHand.MAIN_HAND,hit).consumesAction(),"Native insertion succeeds");
            h.assertTrue(crafter.inventory.stack().is(Items.DIAMOND)&&player.getMainHandItem().getCount()==1,"One real ingredient inserted");
            player.getInventory().setSelectedSlot(1);
            h.assertTrue(player.gameMode.useItemOn(player,h.getLevel(),ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit).consumesAction(),"Empty hand reaches extraction");
            h.assertTrue(crafter.inventory.isResourceBlank(),"Ingredient removed");
            h.assertValueEqual(player.getInventory().countItem(Items.DIAMOND),2,"Both real diamonds retained");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.CRAFTER_SLOT_COVER));
            player.gameMode.useItemOn(player,h.getLevel(),player.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
            h.assertTrue(crafter.covered,"Cover installed");
            player.gameMode.useItemOn(player,h.getLevel(),ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit);
            h.assertFalse(crafter.covered,"Empty hand removes cover");
            h.assertValueEqual(player.getInventory().countItem(AllItems.CRAFTER_SLOT_COVER),1,"Cover returned once");
        }h.succeed();
    }
    @GameTest public void nativeEmptyHandRetrievesBasinContents(GameTestHelper h) {
        h.setBlock(P,AllBlocks.BASIN);var basin=h.getBlockEntity(P,BasinBlockEntity.class);
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(P)).add(0,0,2))) {
            var player=joined.player();var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)).add(0,0,.5),Direction.SOUTH,h.absolutePos(P),false);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND));
            player.gameMode.useItemOn(player,h.getLevel(),player.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
            h.assertFalse(basin.isEmpty(),"Basin received item");
            player.gameMode.useItemOn(player,h.getLevel(),ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit);
            h.assertTrue(basin.isEmpty(),"Basin empty hand retrieves contents");
            h.assertValueEqual(player.getInventory().countItem(Items.DIAMOND),1,"One real diamond recovered");
        }h.succeed();
    }
    @GameTest public void processingFilterSlotsAcceptPersistAndRefundRealFilters(GameTestHelper h) {
        for(var block:new Block[]{AllBlocks.DEPLOYER,AllBlocks.MECHANICAL_SAW,AllBlocks.BASIN,AllBlocks.SMART_FLUID_PIPE}) {
            h.setBlock(P,block);var state=h.getBlockState(P);
            if(block==AllBlocks.MECHANICAL_SAW){state=state.setValue(com.simibubi.create.content.kinetics.saw.SawBlock.FACING,Direction.UP);h.setBlock(P,state);}
            var actual=state;var side=java.util.Arrays.stream(Direction.values()).filter(d->FilteringSettings.activeSide(actual,d)).findFirst().orElseThrow();
            var pos=h.absolutePos(P);var center=Vec3.atLowerCornerOf(pos).add(FilteringSettings.processingFilterPosition(state,side));
            var hit=new BlockHitResult(center,side,pos,false);
            try(var joined=NativeSurvivalTestPlayer.join(h,center.add(side.getUnitVec3().scale(2)))) {
                var player=joined.player();player.setItemInHand(InteractionHand.MAIN_HAND,FilterGameTests.filter(false,false,new ItemStack(Items.DIAMOND)));
                h.assertTrue(player.gameMode.useItemOn(player,h.getLevel(),player.getMainHandItem(),InteractionHand.MAIN_HAND,hit).consumesAction(),"Native filter click accepted: "+block);
                var filtering=FilteringSettings.resolve(h.getLevel(),pos);
                h.assertTrue(filtering!=null&&filtering.getFilter().is(AllItems.FILTER)&&player.getMainHandItem().isEmpty(),"Filter transferred to machine: "+block);
                h.assertTrue(filtering.test(new ItemStack(Items.DIAMOND))&&!filtering.test(new ItemStack(Items.DIRT)),"Configured predicate active");
                var entity=h.getLevel().getBlockEntity(pos);var restored=BlockEntity.loadStatic(pos,state,entity.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
                var restoredFilter=com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(restored,FilteringBehaviour.TYPE);
                h.assertTrue(restoredFilter!=null&&restoredFilter.getFilter().is(AllItems.FILTER),"Filter survives native save/load");
                if(restored instanceof DeployerBlockEntity deployer)h.assertTrue(deployer.filter.is(AllItems.FILTER),"Legacy deployer filter view agrees after load");
                player.gameMode.useItemOn(player,h.getLevel(),ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit);
                h.assertTrue(filtering.getFilter().isEmpty(),"Empty hand clears filter");
                h.assertValueEqual(player.getInventory().countItem(AllItems.FILTER),1,"Real filter refunded once");
            }h.setBlock(P,Blocks.AIR);
        }h.succeed();
    }
    @GameTest public void deployerPlacementLeavesOneCellGapInEveryDirection(GameTestHelper h) {
        for(var facing:Direction.values()) {
            h.setBlock(P,AllBlocks.DEPLOYER.defaultBlockState().setValue(DeployerBlock.FACING,facing));
            var deployer=h.getBlockEntity(P,DeployerBlockEntity.class);var target=P.relative(facing,2);var gap=P.relative(facing);
            h.setBlock(gap,Blocks.AIR);h.setBlock(target,Blocks.AIR);deployer.setHeldItem(new ItemStack(Items.STONE,2));
            DeployerHandler.activate(deployer,deployer.getPlayer());
            h.assertBlockPresent(Blocks.STONE,target);h.assertBlockPresent(Blocks.AIR,gap);
            h.assertValueEqual(deployer.getPlayer().getMainHandItem().getCount(),1,"One item paid at distance two: "+facing);
            h.setBlock(target,Blocks.AIR);
            // A solid work cell must never cause placement backwards into the gap.
            h.setBlock(target,Blocks.DIRT);deployer.setHeldItem(new ItemStack(Items.STONE,2));
            DeployerHandler.activate(deployer,deployer.getPlayer());
            h.assertBlockPresent(Blocks.AIR,gap);h.assertBlockPresent(Blocks.DIRT,target);
            h.assertValueEqual(deployer.getPlayer().getMainHandItem().getCount(),2,"Solid work cell preserves item: "+facing);
            h.setBlock(target,Blocks.AIR);
        }h.succeed();
    }
}
