package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.copycat.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Native consumers and actual paid custody; all runtime cases remain UNRUN until a separately authorized run. */
public final class CopycatSurfaceGameTests {
    private static final BlockPos P=new BlockPos(2,3,2);
    private static CopycatBlockEntity paid(GameTestHelper h,BlockPos pos,Item material){return paid(h,pos,material,AllBlocks.COPYCAT_PANEL.defaultBlockState());}
    private static CopycatBlockEntity paid(GameTestHelper h,BlockPos pos,Item material,net.minecraft.world.level.block.state.BlockState wrapper){
        h.getLevel().setBlockAndUpdate(pos,wrapper);
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(pos).add(0,2,3))){var player=joined.player();var stock=new ItemStack(material);player.getInventory().setItem(0,stock);h.assertTrue(player.gameMode.useItemOn(player,h.getLevel(),stock,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)).consumesAction()&&stock.isEmpty(),"Actual native material interaction must pay its sole original item");}
        return (CopycatBlockEntity)h.getLevel().getBlockEntity(pos);
    }
    private static ServerExplosion explosion(GameTestHelper h,BlockPos pos,ExplosionDamageCalculator calculator){return new ServerExplosion(h.getLevel(),null,null,calculator,Vec3.atCenterOf(pos),1,false,Explosion.BlockInteraction.KEEP);}
    private static int loose(GameTestHelper h,BlockPos pos,Item item){return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1.5)).stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum();}

    @GameTest public void nativeExplosionCalculatorUsesActualPaidObsidianAndPreservesWaterResistance(GameTestHelper h){
        var pos=h.absolutePos(P);var copycat=paid(h,pos,Items.OBSIDIAN);var calculator=new ExplosionDamageCalculator();var blast=explosion(h,pos,calculator);var state=h.getLevel().getBlockState(pos);
        h.assertValueEqual(calculator.getBlockExplosionResistance(blast,h.getLevel(),pos,state,state.getFluidState()).orElseThrow(),Blocks.OBSIDIAN.getExplosionResistance(),"Actual native explosion consumer sees original paid material resistance");
        var wetPos=pos.east(3);paid(h,wetPos,Items.STONE);h.getLevel().setBlockAndUpdate(wetPos,h.getLevel().getBlockState(wetPos).setValue(WaterloggedCopycatBlock.WATERLOGGED,true));state=h.getLevel().getBlockState(wetPos);
        h.assertValueEqual(calculator.getBlockExplosionResistance(blast,h.getLevel(),wetPos,state,state.getFluidState()).orElseThrow(),state.getFluidState().getExplosionResistance(),"Actual fluid resistance remains part of the native max calculation");
        h.assertTrue(copycat.getConsumedItem().is(Items.OBSIDIAN)&&copycat.getConsumedItem().getCount()==1,"Read-only resistance adaptation cannot transfer or alter original paid receipt");h.succeed();
    }
    @GameTest public void actualSurvivalBreakUsesMaterialHarvestAfterNativeWrapperRemoval(GameTestHelper h){
        var pos=h.absolutePos(P);var copycat=paid(h,pos,Items.OAK_LOG);
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(pos).add(0,2,3))){var player=joined.player();h.assertTrue(player.getMainHandItem().isEmpty(),"Survival original empty hand");h.assertTrue(player.gameMode.destroyBlock(pos),"Native game mode removes actual wrapper");}
        h.assertTrue(h.getLevel().getBlockState(pos).isAir()&&loose(h,pos,AllBlocks.COPYCAT_PANEL.asItem())==1&&loose(h,pos,Items.OAK_LOG)==1&&!copycat.ownsConsumedItem(),"Native material tool rule allows original log by hand, while real removal still returns exactly one wrapper and one paid receipt");h.succeed();
    }
    @GameTest public void realNativePickPacketShiftSelectsExistingWrapperWithoutMintingOrRefunding(GameTestHelper h){
        var pos=h.absolutePos(P);var copycat=paid(h,pos,Items.OAK_LOG);var receipt=copycat.getConsumedItem();
        try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(pos).add(0,2,3))){var player=joined.player();player.getInventory().setItem(1,new ItemStack(AllBlocks.COPYCAT_PANEL));player.getInventory().setItem(2,new ItemStack(Items.OAK_LOG,2));player.setShiftKeyDown(false);player.connection.handlePickItemFromBlock(new ServerboundPickItemFromBlockPacket(pos,false));h.assertTrue(player.getMainHandItem().is(Items.OAK_LOG),"Actual native ordinary pick selects existing material stack");player.setShiftKeyDown(true);try{player.connection.handlePickItemFromBlock(new ServerboundPickItemFromBlockPacket(pos,false));h.assertTrue(player.getMainHandItem().is(AllBlocks.COPYCAT_PANEL.asItem()),"Actual Fabric server pick event preserves source Shift wrapper selection");}finally{player.setShiftKeyDown(false);}h.assertTrue(player.getInventory().countItem(Items.OAK_LOG)==2&&player.getInventory().countItem(AllBlocks.COPYCAT_PANEL.asItem())==1&&ItemStack.matches(copycat.getConsumedItem(),receipt),"Real finite native inventory picking cannot debit, mint, or refund the paid material");}h.succeed();
    }
    @GameTest(maxTicks=60) public void actualItemEntityTicksApplyPaidIceFrictionAtNativeSupportingBlock(GameTestHelper h){
        var icePos=h.absolutePos(P);var stonePos=icePos.east(3);
        // Native item friction samples floor(feetY-.500001f), so the source panel must have a top at a whole block boundary.
        var wrapper=AllBlocks.COPYCAT_PANEL.defaultBlockState().setValue(CopycatPanelBlock.FACING,Direction.DOWN);paid(h,icePos,Items.ICE,wrapper);paid(h,stonePos,Items.STONE,wrapper);
        var ice=new ItemEntity(h.getLevel(),icePos.getX()+.3,icePos.getY()+1,icePos.getZ()+.5,new ItemStack(Items.COBBLESTONE));var stone=new ItemEntity(h.getLevel(),stonePos.getX()+.3,stonePos.getY()+1,stonePos.getZ()+.5,new ItemStack(Items.COBBLESTONE));ice.setDeltaMovement(Vec3.ZERO);stone.setDeltaMovement(Vec3.ZERO);h.getLevel().addFreshEntity(ice);h.getLevel().addFreshEntity(stone);
        h.runAfterDelay(25,()->{h.assertTrue(ice.onGround()&&stone.onGround()&&ice.getBlockPosBelowThatAffectsMyMovement().equals(icePos)&&stone.getBlockPosBelowThatAffectsMyMovement().equals(stonePos),"Real native collision must establish each Copycat as the actual supporting block: iceFeet="+ice.position()+", iceOnGround="+ice.onGround()+", iceMovementSupport="+ice.getBlockPosBelowThatAffectsMyMovement()+", stoneFeet="+stone.position()+", stoneOnGround="+stone.onGround()+", stoneMovementSupport="+stone.getBlockPosBelowThatAffectsMyMovement());ice.setDeltaMovement(.06,0,0);stone.setDeltaMovement(.06,0,0);h.runAfterDelay(2,()->{h.assertTrue(ice.getDeltaMovement().x>stone.getDeltaMovement().x*1.5,"Actual native tick must retain more horizontal speed over original paid ice material");h.assertTrue(ice.getItem().getCount()==1&&stone.getItem().getCount()==1,"Physics cannot change either original item stack");h.succeed();});});
    }
    @GameTest public void actualDangerousWitherSkullPreservesNativeImmunityOfPaidBedrock(GameTestHelper h){
        var pos=h.absolutePos(P);var copycat=paid(h,pos,Items.BEDROCK);var calculator=new ExplosionDamageCalculator();var blast=explosion(h,pos,calculator);var state=h.getLevel().getBlockState(pos);var resistance=calculator.getBlockExplosionResistance(blast,h.getLevel(),pos,state,state.getFluidState()).orElseThrow();var skull=new WitherSkull(EntityTypes.WITHER_SKULL,h.getLevel());skull.setDangerous(true);
        h.assertValueEqual(skull.getBlockExplosionResistance(blast,h.getLevel(),pos,state,state.getFluidState(),resistance),Blocks.BEDROCK.getExplosionResistance(),"Real dangerous-skull native immunity check must not cap an immune original material to0.8");h.assertTrue(copycat.getConsumedItem().is(Items.BEDROCK)&&copycat.ownsConsumedItem(),"Entity resistance inspection retains original paid owner");h.succeed();
    }
}
