package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.kinetics.press.PressingBehaviour.Mode;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.ConfigScope;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Actual registered factories, paid storage, native tick and source ABI. UNCOMPILED / UNRUN. */
public final class SourcePressingBehaviourGameTests {
    private static final String STRUCTURE="create_test:reservoir";
    private static final BlockPos P=new BlockPos(6,6,6);
    private static MechanicalPressBlockEntity press(GameTestHelper h){
        h.setBlock(P,AllBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(MechanicalPressBlock.FACING,Direction.EAST));
        h.setBlock(P.west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));
        h.getBlockEntity(P.west(),KineticBlockEntity.class).setGeneratedSpeed(64);
        var press=h.getBlockEntity(P,MechanicalPressBlockEntity.class);press.initialize();KineticNetworks.tick(h.getLevel());return press;
    }
    private static DepotBlockEntity depot(GameTestHelper h,int amount){
        h.setBlock(P.below(2),AllBlocks.DEPOT);var depot=h.getBlockEntity(P.below(2),DepotBlockEntity.class);
        try(var tx=Transaction.openOuter()){depot.inventory.automation.insert(ItemVariant.of(Items.IRON_INGOT),amount,tx);tx.commit();}return depot;
    }
    private static long count(DepotBlockEntity depot,net.minecraft.world.item.Item item){try(var tx=Transaction.openOuter()){return depot.inventory.automation.extract(ItemVariant.of(item),64,tx);}}
    private static void tick(GameTestHelper h,MechanicalPressBlockEntity press){MechanicalPressBlockEntity.tick(h.getLevel(),press.getBlockPos(),press.getBlockState(),press);}

    @GameTest(structure=STRUCTURE) public void actualPressUsesOneInstalledSourceCycleThroughNestedNativeTickAndRenderer(GameTestHelper h){
        var press=press(h);var source=press.getPressingBehaviour();
        h.assertTrue(source==press.pressingBehaviour&&source==press.getBehaviour(BeltProcessingBehaviour.TYPE)&&source==BeltProcessingBehaviour.createNative(press),"Every actual source/native entry point exposes the identical cycle owner");
        source.start(Mode.BELT);int step=source.getRunningTickSpeed();tick(h,press);
        h.assertTrue(source.runningTicks==step&&source.prevRunningTicks==0&&press.isRunning(),"Smart and nested Kinetic/native tick advance the one source clock exactly once");
        h.assertTrue(press.getHeadOffset(1)==source.getRenderedHeadOffset(1)*Mode.BELT.headOffset,"Existing actual native renderer reads the genuine source animation");
        source.runningTicks=source.prevRunningTicks=120;source.mode=Mode.BASIN;
        h.assertValueEqual(press.getHeadOffset(1),22f/16f,"Source basin head extent is used by the actual native head getter");
        h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void actualHeldCallbackAndNativeTickCommitOriginalDepotInputOnlyOnce(GameTestHelper h){
        var setting=AllConfigs.server().recipes.bulkPressing;boolean previous=setting.get();
        AllConfigs.store(ConfigScope.SERVER).importValues(Map.of(setting.key(),false),false);
        try{
            var depot=depot(h,3);var press=press(h);var source=press.getPressingBehaviour();var behaviour=depot.depotBehaviour;
            var held=behaviour.getHeldItem();held.beltPosition=held.prevBeltPosition=.5f;
            source.handleReceivedItem(held,behaviour.transportedHandler);source.runningTicks=source.prevRunningTicks=120;
            source.handleHeldItem(held,behaviour.transportedHandler);tick(h,press);
            h.assertTrue(source.hasAppliedImpact()&&count(depot,AllItems.IRON_SHEET)==1&&count(depot,Items.IRON_INGOT)==2,"Real source held callback followed by native owner tick debits one original ingot and credits one plate");
            var data=press.saveWithFullMetadata(h.getLevel().registryAccess());
            var restored=(MechanicalPressBlockEntity)BlockEntity.loadStatic(press.getBlockPos(),press.getBlockState(),data,h.getLevel().registryAccess());
            h.getLevel().removeBlockEntity(press.getBlockPos());h.getLevel().setBlockEntity(restored);restored.initialize();
            restored.getPressingBehaviour().runningTicks=120;tick(h,restored);
            h.assertTrue(count(depot,AllItems.IRON_SHEET)==1&&count(depot,Items.IRON_INGOT)==2&&restored.getPressingBehaviour().hasAppliedImpact(),"Actual registry reload keeps the original paid impact receipt without replaying output/RNG");
        }finally{AllConfigs.store(ConfigScope.SERVER).importValues(Map.of(setting.key(),previous),false);}h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void sourceNbtTicksAndModeWinOverConflictingLegacyAliases(GameTestHelper h){
        var press=press(h);var source=press.getPressingBehaviour();source.start(Mode.BASIN);source.runningTicks=source.prevRunningTicks=81;
        var data=press.saveWithFullMetadata(h.getLevel().registryAccess());data.putInt("PressTicks",7);data.putBoolean("OnDepot",false);
        var loaded=(MechanicalPressBlockEntity)BlockEntity.loadStatic(press.getBlockPos(),press.getBlockState(),data,h.getLevel().registryAccess());
        h.assertTrue(loaded.getPressingBehaviour().runningTicks==81&&loaded.getPressingBehaviour().mode==Mode.BASIN,"Actual source persistence overrides old native aliases without creating another cycle");
        var again=loaded.saveWithFullMetadata(h.getLevel().registryAccess());
        h.assertTrue(again.getIntOr("Ticks",0)==81&&again.getIntOr("PressTicks",0)==81&&again.getBooleanOr("OnDepot",false),"All compatibility aliases are derived from source state");
        data.remove("Ticks");data.remove("Mode");var legacy=(MechanicalPressBlockEntity)BlockEntity.loadStatic(press.getBlockPos(),press.getBlockState(),data,h.getLevel().registryAccess());
        h.assertTrue(legacy.getPressingBehaviour().runningTicks==7&&legacy.getPressingBehaviour().mode==Mode.WORLD,"Old native saves still migrate through genuine factory behavior read");h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void virtualSourceBasinCycleAnimatesWithoutTouchingActualWorldInputOrPaidOutputs(GameTestHelper h){
        var depot=depot(h,7);var real=press(h);
        var virtual=new MechanicalPressBlockEntity(real.getBlockPos(),real.getBlockState());virtual.markVirtual();virtual.setLevel(h.getLevel());virtual.setSpeed(64);
        var source=virtual.getPressingBehaviour();source.start(Mode.BASIN);
        for(int i=0;i<20;i++)source.tick();
        h.assertTrue(source.runningTicks>0&&virtual.getHeadOffset(1)>0,"Actual source clock/head progress in a virtual owner with no native recipe authority");
        for(int i=0;i<120;i++)source.tick();
        h.assertTrue(source.finished&&!source.running&&count(depot,Items.IRON_INGOT)==7&&count(depot,AllItems.IRON_SHEET)==0&&h.getBlockEntity(P,MechanicalPressBlockEntity.class)==real,"Complete virtual animation cannot consume, publish outputs or replace host actual owners");h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void sourceBasinModeUsesOriginalNativeBasinTransactionAndCannotDoubleCompress(GameTestHelper h){
        h.setBlock(P.below(2),AllBlocks.BASIN);var basin=h.getBlockEntity(P.below(2),BasinBlockEntity.class);var press=press(h);
        try(var tx=Transaction.openOuter()){basin.inventory.automation.insert(ItemVariant.of(Items.SNOW_BLOCK),9,tx);tx.commit();}
        var source=press.getPressingBehaviour();source.start(Mode.BASIN);source.runningTicks=source.prevRunningTicks=120;tick(h,press);tick(h,press);
        long snow=0,ice=0;for(var slot:basin.inventory.inputs){if(slot.getResource().equals(ItemVariant.of(Items.SNOW_BLOCK)))snow+=slot.getAmount();if(slot.getResource().equals(ItemVariant.of(Items.ICE)))ice+=slot.getAmount();}for(var slot:basin.inventory.outputs){if(slot.getResource().equals(ItemVariant.of(Items.SNOW_BLOCK)))snow+=slot.getAmount();if(slot.getResource().equals(ItemVariant.of(Items.ICE)))ice+=slot.getAmount();}
        h.assertTrue(source.hasAppliedImpact()&&source.mode==Mode.BASIN&&snow==0&&ice==1,"One genuine source impact commits exactly the original native nine-snow-block compacting recipe");h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void removedSourcePressCannotRestartOrConsumeReplacementBarrelInventory(GameTestHelper h){
        var press=press(h);var source=press.getPressingBehaviour();source.start(Mode.BELT);
        h.setBlock(P,Blocks.BARREL);var barrel=h.getBlockEntity(P,BarrelBlockEntity.class);barrel.setItem(0,new ItemStack(Items.DIAMOND,13));
        int ticks=source.runningTicks;source.start(Mode.BASIN);source.tick();
        h.assertTrue(source.runningTicks==ticks&&barrel.getItem(0).getCount()==13&&h.getBlockEntity(P,BarrelBlockEntity.class)==barrel,"Late source owner references preserve original replacement inventory and cannot resurrect a processor");h.succeed();
    }
}
