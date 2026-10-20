package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.deployer.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import java.util.*;

public class ManualApplicationGameTests {
    private static final BlockPos P=new BlockPos(3,3,3);
    private static DeployerFakePlayer player(GameTestHelper h,ItemStack held){var player=new DeployerFakePlayer(h.getLevel(),UUID.randomUUID());player.setPos(Vec3.atCenterOf(h.absolutePos(P)).add(0,0,2));player.setItemInHand(InteractionHand.MAIN_HAND,held);return player;}
    private static InteractionResult use(GameTestHelper h,DeployerFakePlayer player,InteractionHand hand){var pos=h.absolutePos(P);return player.gameMode.useItemOn(player,h.getLevel(),player.getItemInHand(hand),hand,new BlockHitResult(Vec3.atCenterOf(pos).add(0,0,0.5),Direction.SOUTH,pos,false));}
    @GameTest public void originalCasingRecipesUseConventionTagsAndConsumeOneMaterial(GameTestHelper h){
        Item[] tools={AllItems.ANDESITE_ALLOY,AllItems.BRASS_INGOT,Items.COPPER_INGOT};Block[] outputs={AllBlocks.ANDESITE_CASING,AllBlocks.BRASS_CASING,AllBlocks.COPPER_CASING};for(int i=0;i<tools.length;i++){h.setBlock(P,Blocks.STRIPPED_MANGROVE_LOG);var player=player(h,new ItemStack(tools[i],2));h.assertTrue(use(h,player,InteractionHand.MAIN_HAND).consumesAction(),"Casing recipe consumes use");h.assertBlockPresent(outputs[i],P);h.assertValueEqual(player.getMainHandItem().getCount(),1,"One material consumed");}h.succeed();
    }
    @GameTest public void datapackRecipePreservesMatchingBlockPropertiesAndReusableTool(GameTestHelper h){
        h.setBlock(P,Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));var player=player(h,new ItemStack(Items.GOLD_NUGGET,2));use(h,player,InteractionHand.MAIN_HAND);h.assertBlockPresent(Blocks.MANGROVE_LOG,P);h.assertValueEqual(h.getBlockState(P).getValue(BlockStateProperties.AXIS),Direction.Axis.X,"Log axis copied to replacement");h.assertValueEqual(player.getMainHandItem().getCount(),2,"Datapack keep_held_item honored");h.succeed();
    }
    @GameTest public void recipeWithItemResultRemovesBlockAndDropsEachOutputOnce(GameTestHelper h){
        h.setBlock(P,Blocks.GRAVEL);var player=player(h,new ItemStack(Items.STICK));use(h,player,InteractionHand.MAIN_HAND);h.assertBlockPresent(Blocks.AIR,P);var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(P)).inflate(1));for(var item:List.of(Items.DIAMOND,Items.EMERALD))h.assertValueEqual(drops.stream().filter(entity->entity.getItem().is(item)).mapToInt(entity->entity.getItem().getCount()).sum(),1,"One recipe result dropped");h.assertTrue(player.getMainHandItem().isEmpty(),"Application item consumed");h.succeed();
    }
    @GameTest public void damageableToolUsesActualOffhandAndCopiesFacing(GameTestHelper h){
        h.setBlock(P,Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.WEST));var player=player(h,ItemStack.EMPTY);player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.WOODEN_AXE));use(h,player,InteractionHand.OFF_HAND);h.assertBlockPresent(Blocks.CARVED_PUMPKIN,P);h.assertValueEqual(h.getBlockState(P).getValue(BlockStateProperties.HORIZONTAL_FACING),Direction.WEST,"Facing transferred to new block");h.assertValueEqual(player.getOffhandItem().getDamageValue(),1,"Correct hand damaged once");h.assertTrue(player.getMainHandItem().isEmpty(),"Main hand remains empty");h.succeed();
    }
    @GameTest public void containerRemainderAndCreativeExemptionFollowNativeRules(GameTestHelper h){
        h.setBlock(P,Blocks.NETHERRACK);var player=player(h,new ItemStack(Items.MILK_BUCKET));use(h,player,InteractionHand.MAIN_HAND);h.assertBlockPresent(Blocks.STONE,P);h.assertTrue(player.getMainHandItem().is(Items.BUCKET),"Container returned");h.setBlock(P,Blocks.STRIPPED_CHERRY_WOOD);player.setGameMode(GameType.CREATIVE);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.ANDESITE_ALLOY,2));use(h,player,InteractionHand.MAIN_HAND);h.assertBlockPresent(AllBlocks.ANDESITE_CASING,P);h.assertValueEqual(player.getMainHandItem().getCount(),2,"Creative material preserved");h.succeed();
    }
    @GameTest public void defaultPhaseProtectionRunsBeforeRecipeConversion(GameTestHelper h){
        h.setBlock(P,Blocks.STRIPPED_OAK_LOG);var player=player(h,new ItemStack(AllItems.ANDESITE_ALLOY));DeployerTestFixtures.DENY_USE.add(h.absolutePos(P));use(h,player,InteractionHand.MAIN_HAND);h.assertBlockPresent(Blocks.STRIPPED_OAK_LOG,P);h.assertTrue(player.getMainHandItem().is(AllItems.ANDESITE_ALLOY),"Protection preserved material");DeployerTestFixtures.DENY_USE.remove(h.absolutePos(P));use(h,player,InteractionHand.MAIN_HAND);h.assertBlockPresent(AllBlocks.ANDESITE_CASING,P);h.succeed();
    }
    @GameTest public void wrongToolAndAdventureModeCannotApplyRecipe(GameTestHelper h){
        h.setBlock(P,Blocks.STRIPPED_OAK_LOG);var player=player(h,new ItemStack(Items.IRON_NUGGET));use(h,player,InteractionHand.MAIN_HAND);h.assertBlockPresent(Blocks.STRIPPED_OAK_LOG,P);player.setGameMode(GameType.ADVENTURE);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.ANDESITE_ALLOY));use(h,player,InteractionHand.MAIN_HAND);h.assertBlockPresent(Blocks.STRIPPED_OAK_LOG,P);h.assertTrue(player.getMainHandItem().is(AllItems.ANDESITE_ALLOY),"Adventure mode preserves material");h.succeed();
    }
    @GameTest(maxTicks=70)public void realDeployerAppliesWorldRecipeThroughNativePlayerInteraction(GameTestHelper h){
        h.setBlock(P,Blocks.STRIPPED_OAK_LOG);var hand=P.above(2);h.setBlock(hand,AllBlocks.DEPLOYER.defaultBlockState().setValue(DeployerBlock.FACING,Direction.DOWN).setValue(DeployerBlock.AXIS_ALONG_FIRST_COORDINATE,true));h.setBlock(hand.west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING,Direction.EAST));h.getBlockEntity(hand.west(),com.simibubi.create.content.kinetics.base.KineticBlockEntity.class).setGeneratedSpeed(256);var deployer=h.getBlockEntity(hand,DeployerBlockEntity.class);deployer.setHeldItem(new ItemStack(AllItems.BRASS_INGOT));h.succeedWhen(()->{h.assertBlockPresent(AllBlocks.BRASS_CASING,P);h.assertTrue(deployer.getHeldItem().isEmpty(),"World application consumes held material once");});
    }
}
