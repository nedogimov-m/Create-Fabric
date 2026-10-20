package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.equipment.clipboard.ClipboardValueSettingsHandler;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.motor.KineticScrollValueBehaviour;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.logistics.tunnel.BrassTunnelBlockEntity;
import com.simibubi.create.content.logistics.tunnel.BrassTunnelBlockEntity.SelectionMode;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsPacket;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.BulkScrollValueBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.ConfigScope;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Actual registered owners, Fabric protocol and native persistence. UNCOMPILED / UNRUN. */
public final class SourceMotorValueSettingsGameTests {
    private static final String STRUCTURE="create_test:reservoir";
    private static final BlockPos P=new BlockPos(6,5,6);
    private static CreativeMotorBlockEntity motor(GameTestHelper h,BlockPos pos,Direction facing){
        h.setBlock(pos,AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,facing));
        var motor=h.getBlockEntity(pos,CreativeMotorBlockEntity.class);motor.initialize();return motor;
    }
    private static KineticBlockEntity shaft(GameTestHelper h,BlockPos pos){
        h.setBlock(pos,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.X));
        var shaft=h.getBlockEntity(pos,KineticBlockEntity.class);KineticNetworks.track(h.getLevel(),shaft);KineticNetworks.tick(h.getLevel());return shaft;
    }
    private static NativeSurvivalTestPlayer player(GameTestHelper h){return NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(P)).add(0,0,2));}
    private static ValueSettingsPacket packet(CreativeMotorBlockEntity motor,int row,int value,Direction side,int id,boolean ctrl){return new ValueSettingsPacket(motor.getBlockPos(),row,value,null,null,side,ctrl,id);}
    private static void limit(int value){var config=AllConfigs.server().kinetics.maxRotationSpeed;AllConfigs.store(ConfigScope.SERVER).importValues(Map.of(config.key(),value),false);}

    @GameTest(structure=STRUCTURE) public void actualScrollOwnerIsTheNativeSettingAndDrivesRealShaftWithOneCallback(GameTestHelper h){
        var motor=motor(h,P,Direction.EAST);var shaft=shaft(h,P.east());int[] calls={0};
        motor.generatedSpeed.withCallback(value->{calls[0]++;motor.updateGeneratedRotation();});
        h.assertTrue(motor.getBehaviour(ScrollValueBehaviour.TYPE)==motor.generatedSpeed,"Actual Smart registry exposes the source setting itself");
        motor.setGeneratedSpeed(48);KineticNetworks.tick(h.getLevel());
        h.assertTrue(motor.generatedSpeed.getValue()==48&&motor.getGeneratedSpeedSetting()==48&&shaft.getSpeed()==48&&calls[0]==1,"Native setter changes one source owner and invokes its real capacity/rotation callback once");
        motor.generatedSpeed.setValue(-48);motor.setGeneratedSpeed(-48);KineticNetworks.tick(h.getLevel());
        h.assertTrue(shaft.getSpeed()==-48&&calls[0]==2,"Public source mutation reverses the actual shaft; identical native mutation does not replay callback");
        var west=motor(h,P.south(3),Direction.WEST);west.generatedSpeed.setValue(32);
        h.assertTrue(west.getGeneratedSpeed()==-32&&west.getGeneratedSpeedSetting()==32,"Source setting remains signed independently of actual facing conversion");h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void sourcePersistenceWinsOverLegacyAliasOnActualFactoryReload(GameTestHelper h){
        var motor=motor(h,P,Direction.EAST);motor.generatedSpeed.setValue(-64);
        var data=motor.saveWithFullMetadata(h.getLevel().registryAccess());
        h.assertTrue(data.getIntOr("ScrollValue",0)==-64&&data.getIntOr("GeneratedSpeed",0)==-64,"Both saved keys derive from the same installed setting");
        data.putInt("GeneratedSpeed",128);
        var restored=(CreativeMotorBlockEntity)BlockEntity.loadStatic(motor.getBlockPos(),motor.getBlockState(),data,h.getLevel().registryAccess());
        h.assertTrue(restored.generatedSpeed.getValue()==-64&&restored.getGeneratedSpeedSetting()==-64&&restored.getBehaviour(ScrollValueBehaviour.TYPE)==restored.generatedSpeed,"Genuine source key wins during actual registry factory read");
        h.getLevel().removeBlockEntity(motor.getBlockPos());h.getLevel().setBlockEntity(restored);restored.initialize();
        var shaft=shaft(h,P.east());h.assertTrue(shaft.getSpeed()==-64,"Loaded actual source resumes the real network");
        data.remove("ScrollValue");data.putInt("GeneratedSpeed",96);
        var legacy=(CreativeMotorBlockEntity)BlockEntity.loadStatic(motor.getBlockPos(),motor.getBlockState(),data,h.getLevel().registryAccess());
        h.assertValueEqual(legacy.generatedSpeed.getValue(),96,"Old native save migrates only when the source key is absent");h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void existingSourceOwnerUsesFullConfiguredIntRangeThroughActualPacketAndGraph(GameTestHelper h){
        int previous=AllConfigs.server().kinetics.maxRotationSpeed.get();
        try(var joined=player(h)){
            var motor=motor(h,P,Direction.EAST);var shaft=shaft(h,P.east());
            try{
                limit(1024);h.assertTrue(packet(motor,1,513,Direction.UP,0,false).apply(h.getLevel(),joined.player()),"Actual source board packet accepts reconfigured range on the existing owner");
                h.assertTrue(motor.generatedSpeed.getValue()==513&&shaft.getSpeed()==513,"No old hardcoded 256 clamp or shadow native value");
                limit(Integer.MAX_VALUE);h.assertTrue(packet(motor,1,Integer.MAX_VALUE,Direction.UP,0,false).apply(h.getLevel(),joined.player()),"Actual receiver preserves exact full-range int setting");
                KineticNetworks.tick(h.getLevel());
                h.assertTrue(motor.generatedSpeed.getValue()==Integer.MAX_VALUE&&shaft.getSpeed()==(float)Integer.MAX_VALUE&&!shaft.isConflicted(),"Source float rounding of configured maximum remains legal in the native world solver");
                var saved=motor.saveWithFullMetadata(h.getLevel().registryAccess());h.assertValueEqual(saved.getIntOr("ScrollValue",0),Integer.MAX_VALUE,"Exact int setting survives native value serialization");
            }finally{motor.generatedSpeed.setValue(16);limit(previous);}
        }h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void actualPacketRejectsInvalidBoardSideOwnerDistanceAndPermissions(GameTestHelper h){
        var motor=motor(h,P,Direction.EAST);
        try(var joined=player(h)){
            var player=joined.player();
            h.assertFalse(packet(motor,2,32,Direction.UP,0,false).apply(h.getLevel(),player),"No nonexistent source row");
            h.assertFalse(packet(motor,1,-1,Direction.UP,0,false).apply(h.getLevel(),player),"No negative board magnitude");
            h.assertFalse(packet(motor,1,32,Direction.UP,99,false).apply(h.getLevel(),player),"No nonexistent installed source id");
            h.assertFalse(packet(motor,1,32,Direction.EAST,0,false).apply(h.getLevel(),player),"No source output-face value box");
            h.assertFalse(packet(motor,1,32,Direction.DOWN,0,false).apply(h.getLevel(),player),"No inactive lower side on horizontal source");
            player.setGameMode(GameType.ADVENTURE);h.assertFalse(packet(motor,1,32,Direction.UP,0,false).apply(h.getLevel(),player),"Native adventure permissions refuse mutation");
            player.setGameMode(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(motor.getBlockPos()).add(30,0,0));
            h.assertFalse(packet(motor,1,32,Direction.UP,0,false).apply(h.getLevel(),player),"Real native block interaction range refuses remote mutation");
            h.assertValueEqual(motor.generatedSpeed.getValue(),16,"All rejected paths preserve the actual one source setting");
        }h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void reentrantBoardReplacementAndLateSourceCallbackPreserveActualBarrelCargo(GameTestHelper h){
        var motor=motor(h,P,Direction.EAST);var old=motor.generatedSpeed;
        var replacement=new KineticScrollValueBehaviour(old.label,motor,old.getSlotPositioning()){
            @Override public ValueSettingsBoard createBoard(Player player,BlockHitResult hit){
                var board=super.createBoard(player,hit);h.setBlock(P,Blocks.BARREL);h.getBlockEntity(P,BarrelBlockEntity.class).setItem(0,new ItemStack(Items.DIAMOND,13));return board;
            }
        };replacement.between(-256,256);replacement.value=16;replacement.withCallback(value->motor.updateGeneratedRotation());
        motor.generatedSpeed=replacement;motor.attachBehaviourLate(replacement);
        old.setValue(96);h.assertTrue(old.getValue()==16&&motor.getGeneratedSpeedSetting()==16,"Replaced source behaviour cannot change a still-live actual motor");
        try(var joined=player(h)){
            h.assertFalse(packet(motor,1,64,Direction.UP,0,false).apply(h.getLevel(),joined.player()),"Actual packet revalidates native owner after real source createBoard callback");
            old.setValue(96);replacement.setValue(128);
            h.assertTrue(replacement.getValue()==16&&h.getBlockEntity(P,BarrelBlockEntity.class).getItem(0).getCount()==13,"Removed settings cannot publish or modify replacement's original paid cargo");
            h.assertTrue(h.getLevel().getBlockEntity(motor.getBlockPos()) instanceof BarrelBlockEntity,"Late source references cannot resurrect an old native motor owner");
        }h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void actualClipboardUsesInstalledSourceOwnerAndSimulationLeavesItUntouched(GameTestHelper h){
        int previous=AllConfigs.server().kinetics.maxRotationSpeed.get();var motor=motor(h,P,Direction.EAST);
        try(var joined=player(h)){
            try{
                limit(1024);var providers=ClipboardValueSettingsHandler.cloneables(motor);
                h.assertTrue(providers.stream().filter(provider->provider.getClipboardKey().equals("Speed")).count()==1&&providers.contains(motor.generatedSpeed),"Exactly the installed actual behaviour owns the source Speed clipboard contract");
                var tag=new CompoundTag();tag.putInt("Row",0);tag.putInt("Value",513);
                h.assertTrue(motor.generatedSpeed.readFromClipboard(h.getLevel().registryAccess(),tag,joined.player(),Direction.UP,true)&&motor.getGeneratedSpeedSetting()==16,"Source simulation does not mutate the real motor");
                motor.generatedSpeed.readFromClipboard(h.getLevel().registryAccess(),tag,joined.player(),Direction.UP,false);
                h.assertValueEqual(motor.getGeneratedSpeedSetting(),-513,"Actual clipboard changes the same full-range signed setting");
            }finally{motor.generatedSpeed.setValue(16);limit(previous);}
        }h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void actualTunnelOptionBehaviourPreservesTransferredInventoryThroughNativeReload(GameTestHelper h){
        h.setBlock(P,AllBlocks.BRASS_TUNNEL);h.setBlock(P.east(2),Blocks.BARREL);
        var tunnel=h.getBlockEntity(P,BrassTunnelBlockEntity.class);var barrel=h.getBlockEntity(P.east(2),BarrelBlockEntity.class);
        barrel.setItem(0,new ItemStack(Items.DIAMOND,9));var inventory=ContainerStorage.of(barrel,null);
        try(var tx=Transaction.openOuter()){
            h.assertValueEqual(inventory.extract(ItemVariant.of(Items.DIAMOND),9,tx),9L,"Actual original inventory debited");
            h.assertValueEqual(tunnel.accept(new ItemStack(Items.DIAMOND,9),null,tx),9L,"Actual tunnel queue accepts the same transaction amount");tx.commit();
        }
        var option=tunnel.getBehaviour(ScrollOptionBehaviour.TYPE);h.assertTrue(option instanceof ScrollOptionBehaviour<?>,"Actual tunnel owns source option behaviour");
        option.setValue(SelectionMode.FORCED_ROUND_ROBIN.ordinal());
        h.assertTrue(tunnel.getSelectionMode()==SelectionMode.FORCED_ROUND_ROBIN&&barrel.isEmpty(),"Ponder-style source callback changes real mode without recreating inventory");
        var data=tunnel.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored=(BrassTunnelBlockEntity)BlockEntity.loadStatic(tunnel.getBlockPos(),tunnel.getBlockState(),data,h.getLevel().registryAccess());
        h.getLevel().removeBlockEntity(tunnel.getBlockPos());h.getLevel().setBlockEntity(restored);
        h.assertTrue(restored.getSelectionMode()==SelectionMode.FORCED_ROUND_ROBIN&&restored.getStackToDistribute().is(Items.DIAMOND)&&restored.getStackToDistribute().getCount()==9&&barrel.isEmpty(),"Actual registry reload preserves source setting and exactly the original paid queue");h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void actualCtrlPacketUpdatesBulkOwnersWhileOrdinaryPacketChangesOnlyTarget(GameTestHelper h){
        var first=motor(h,P,Direction.EAST);var second=motor(h,P.south(2),Direction.EAST);
        var bulk=new BulkScrollValueBehaviour(Component.literal("Bulk source setting"),first,first.generatedSpeed.getSlotPositioning(),owner->List.of(first,second));
        bulk.between(-256,256);bulk.value=16;bulk.withCallback(value->first.updateGeneratedRotation());first.generatedSpeed=bulk;first.attachBehaviourLate(bulk);
        try(var joined=player(h)){
            h.assertTrue(packet(first,0,48,Direction.UP,0,false).apply(h.getLevel(),joined.player()),"Real ordinary source packet accepted");
            h.assertTrue(first.getGeneratedSpeedSetting()==48&&second.getGeneratedSpeedSetting()==16,"Ordinary packet changes only target actual owner");
            h.assertTrue(packet(first,0,64,Direction.UP,0,true).apply(h.getLevel(),joined.player()),"Real control bit reaches genuine BulkScrollValueBehaviour");
            h.assertTrue(first.getGeneratedSpeedSetting()==64&&second.getGeneratedSpeedSetting()==64&&first.generatedSpeed==bulk&&second.getBehaviour(ScrollValueBehaviour.TYPE)==second.generatedSpeed,"Bulk packet updates actual installed owners without parallel setting copies");
        }h.succeed();
    }
}
