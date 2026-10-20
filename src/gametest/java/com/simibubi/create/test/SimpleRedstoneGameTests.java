package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.redstone.analogLever.*;
import com.simibubi.create.content.redstone.diodes.*;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.*;

/** Registered native circuits and their actual tick scheduling/notifications. STAGED, UNRUN. */
public class SimpleRedstoneGameTests {
    private static final BlockPos S=new BlockPos(4,4,4);
    private static BrassDiodeBlockEntity diode(GameTestHelper h){return h.getBlockEntity(S,BrassDiodeBlockEntity.class);}
    private static BlockState state(GameTestHelper h){return h.getBlockState(S);}
    private static void place(GameTestHelper h,Block block){h.setBlock(S.below(),Blocks.STONE);h.setBlock(S,block.defaultBlockState().setValue(DiodeBlock.FACING,Direction.NORTH));}
    private static void input(GameTestHelper h,boolean on){h.setBlock(S.north(),on?Blocks.REDSTONE_BLOCK:Blocks.AIR);}
    private static int output(GameTestHelper h){return state(h).getSignal(h.getLevel(),h.absolutePos(S),Direction.NORTH);}
    private static ServerPlayer player(GameTestHelper h){var player=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(h.absolutePos(S)).add(0,0,1));return player;}
    private static void click(GameTestHelper h){state(h).useWithoutItem(h.getLevel(),player(h),new BlockHitResult(Vec3.atCenterOf(h.absolutePos(S)),Direction.UP,h.absolutePos(S),false));}
    private static void direction(GameTestHelper h,Direction facing){
        h.setBlock(S.below(),Blocks.STONE);h.setBlock(S,AllBlocks.PULSE_EXTENDER.defaultBlockState().setValue(DiodeBlock.FACING,facing));h.setBlock(S.relative(facing),Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(5,()->{for(var side:Direction.values())h.assertValueEqual(state(h).getSignal(h.getLevel(),h.absolutePos(S),side),side==facing?15:0,"Only native output query side emits");h.assertTrue(state(h).getValue(DiodeBlock.POWERED)&&state(h).getValue(BrassDiodeBlock.POWERING),"Real input sampled through delayed diode tick");h.succeed();});
    }
    @GameTest(maxTicks=15)public void extenderNorth(GameTestHelper h){direction(h,Direction.NORTH);}
    @GameTest(maxTicks=15)public void extenderEast(GameTestHelper h){direction(h,Direction.EAST);}
    @GameTest(maxTicks=15)public void extenderSouth(GameTestHelper h){direction(h,Direction.SOUTH);}
    @GameTest(maxTicks=15)public void extenderWest(GameTestHelper h){direction(h,Direction.WEST);}
    @GameTest(maxTicks=60)public void extenderRefreshesWhileHeldThenNotifiesARealLampWhenItsTailEnds(GameTestHelper h){
        place(h,AllBlocks.PULSE_EXTENDER);diode(h).setMaxState(8);h.setBlock(S.south(),Blocks.REDSTONE_LAMP);input(h,true);
        h.runAfterDelay(20,()->{h.assertTrue(output(h)==15&&diode(h).getState()==8&&h.getBlockState(S.south()).getValue(RedstoneLampBlock.LIT),"Held input refreshes full interval and powers actual output lamp");input(h,false);});
        h.runAfterDelay(26,()->h.assertValueEqual(output(h),15,"Physical falling input retains a real extension tail"));
        h.runAfterDelay(40,()->{h.assertTrue(output(h)==0&&diode(h).isIdle()&&!h.getBlockState(S.south()).getValue(RedstoneLampBlock.LIT),"Tail ends once, source state idle and real lamp receives off notification");h.succeed();});
    }
    @GameTest(maxTicks=70)public void repeaterEmitsOneTwoTickPulseForHeldInputAndRearmsOnlyAfterLow(GameTestHelper h){
        place(h,AllBlocks.PULSE_REPEATER);diode(h).setMaxState(8);input(h,true);var samples=new int[2];
        for(int tick=1;tick<20;tick++)h.runAfterDelay(tick,()->{if(output(h)==15)samples[0]++;});
        h.runAfterDelay(20,()->h.assertTrue(samples[0]==2&&output(h)==0&&diode(h).getState()>diode(h).getMaxState()+1,"Held physical input produces exactly two output ticks, then waits without retriggering"));
        h.runAfterDelay(25,()->input(h,false));h.runAfterDelay(32,()->{h.assertTrue(diode(h).isIdle(),"Real low input rearms repeater");input(h,true);});
        for(int tick=33;tick<50;tick++)h.runAfterDelay(tick,()->{if(output(h)==15)samples[1]++;});
        h.runAfterDelay(51,()->{h.assertValueEqual(samples[1],2,"A new rising edge emits exactly the second two-tick pulse");h.succeed();});
    }
    @GameTest(maxTicks=35)public void repeaterKeepsAShortCapturedPulseAfterInputFalls(GameTestHelper h){
        place(h,AllBlocks.PULSE_REPEATER);diode(h).setMaxState(12);input(h,true);h.runAfterDelay(5,()->input(h,false));var observed=new int[1];var afterLow=new boolean[1];
        for(int tick=1;tick<22;tick++)h.runAfterDelay(tick,()->{if(output(h)==15){observed[0]++;afterLow[0]|=!state(h).getValue(DiodeBlock.POWERED);}});
        h.runAfterDelay(24,()->{h.assertTrue(observed[0]==2&&afterLow[0]&&diode(h).isIdle()&&output(h)==0,"Captured short input emits its full two-tick output after input POWERED clears, then rearms");h.succeed();});
    }
    @GameTest public void invertedIdleDiodeHasNativeOwnWeakAndDirectOutput(GameTestHelper h){
        place(h,AllBlocks.PULSE_EXTENDER);click(h);var block=state(h);h.assertTrue(!block.getValue(DiodeBlock.POWERED)&&block.getValue(BrassDiodeBlock.INVERTED),"Ordinary use inverts idle diode");
        h.assertTrue(output(h)==15&&block.getOwnSignal(h.getLevel(),h.absolutePos(S))==15&&block.getDirectSignal(h.getLevel(),h.absolutePos(S),Direction.NORTH)==15,"Modern ownSignal must not gate inverted output on input bit");h.succeed();
    }
    @GameTest(maxTicks=30)public void midphaseNativeSaveRestoresTimerSettingAndInversionWithoutLevel(GameTestHelper h){
        place(h,AllBlocks.PULSE_EXTENDER);diode(h).setMaxState(37);input(h,true);
        h.runAfterDelay(6,()->{click(h);input(h,false);});h.runAfterDelay(12,()->{var saved=diode(h).saveWithFullMetadata(h.getLevel().registryAccess());var restored=(BrassDiodeBlockEntity)BlockEntity.loadStatic(h.absolutePos(S),state(h),saved,h.getLevel().registryAccess());h.assertTrue(restored!=null&&restored.getState()==diode(h).getState()&&restored.getMaxState()==37&&restored.getProgress()==diode(h).getProgress(),"Source State/ScrollValue survive native pre-setLevel decode");h.succeed();});
    }
    @GameTest(maxTicks=15)public void shorteningLiveIntervalClampsConsumedStateAndPersists(GameTestHelper h){
        place(h,AllBlocks.PULSE_EXTENDER);diode(h).setMaxState(40);input(h,true);h.runAfterDelay(6,()->{h.assertValueEqual(diode(h).getState(),40,"Real held input filled interval");diode(h).setMaxState(3);h.assertTrue(diode(h).getState()==3&&diode(h).getMaxState()==3,"Source timing callback clamps active state");h.succeed();});
    }
    @GameTest public void nativeTimingPacketBoundsAndAuthoritativeReachRejectUnloadedTargets(GameTestHelper h){
        place(h,AllBlocks.PULSE_REPEATER);var player=player(h);var packet=new BrassDiodeSettings.SetTiming(h.absolutePos(S),72000);var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{BrassDiodeSettings.SetTiming.CODEC.encode(buffer,packet);h.assertTrue(BrassDiodeSettings.SetTiming.CODEC.decode(buffer).equals(packet)&&buffer.readableBytes()==0,"Actual native packet roundtrip");buffer.clear();buffer.writeBlockPos(h.absolutePos(S));buffer.writeVarInt(Integer.MAX_VALUE);boolean rejected=false;try{BrassDiodeSettings.SetTiming.CODEC.decode(buffer);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected,"Unbounded timing rejected during native decode");}finally{buffer.release();}
        h.assertTrue(BrassDiodeSettings.apply(player,packet)&&diode(h).getMaxState()==72000,"Near server owner accepts full source timing range");var far=new BlockPos(2600000,80,2600000);h.assertFalse(BrassDiodeSettings.apply(player,new BrassDiodeSettings.SetTiming(far,20)),"Far/unloaded target refused");h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"No config chunk ticket");h.succeed();
    }
    @GameTest public void timingBoardRetainsSourceRowsAndBoundaryConversions(GameTestHelper h){
        h.assertTrue(BrassDiodeSettings.ValueSetting.fromTicks(60).equals(new BrassDiodeSettings.ValueSetting(0,60))&&BrassDiodeSettings.ValueSetting.fromTicks(61).equals(new BrassDiodeSettings.ValueSetting(1,3)),"Source switches units strictly above60ticks");
        h.assertTrue(BrassDiodeSettings.ValueSetting.fromTicks(1200).equals(new BrassDiodeSettings.ValueSetting(1,60))&&BrassDiodeSettings.ValueSetting.fromTicks(1201).equals(new BrassDiodeSettings.ValueSetting(2,1)),"Source switches to minutes strictly above1200ticks");h.assertTrue(new BrassDiodeSettings.ValueSetting(0,0).ticks()==2&&new BrassDiodeSettings.ValueSetting(2,60).ticks()==72000,"Board exact source range");h.succeed();
    }
    @GameTest public void actualNativeWireConnectionsUseAxisForDiodesAndAllHorizontalLatchSides(GameTestHelper h){
        for(var block:new Block[]{AllBlocks.PULSE_REPEATER,AllBlocks.PULSE_EXTENDER,AllBlocks.POWERED_TOGGLE_LATCH,AllBlocks.POWERED_LATCH}){var state=block.defaultBlockState().setValue(DiodeBlock.FACING,Direction.NORTH);for(var face:Direction.values())h.assertTrue(state.shouldRedstoneWireConnectTo(h.getLevel(),h.absolutePos(S),face)==(face.getAxis().isHorizontal()&&(block==AllBlocks.POWERED_LATCH||face.getAxis()==Direction.Axis.Z)),"Native wire port semantics for "+block+" "+face);}h.succeed();
    }
    @GameTest(maxTicks=50)public void toggleLatchHeldAndFallingInputCannotToggleButNextRiseDoes(GameTestHelper h){
        place(h,AllBlocks.POWERED_TOGGLE_LATCH);input(h,true);h.runAfterDelay(5,()->h.assertValueEqual(output(h),15,"First rising input toggles on"));h.runAfterDelay(15,()->{h.assertValueEqual(output(h),15,"Held input cannot toggle repeatedly");input(h,false);});
        h.runAfterDelay(21,()->{h.assertValueEqual(output(h),15,"Falling edge preserves remembered output");input(h,true);});h.runAfterDelay(27,()->{h.assertValueEqual(output(h),0,"Second native rising edge toggles off");h.succeed();});
    }
    @GameTest public void manualToggleUsesActualNativeBlockInteraction(GameTestHelper h){place(h,AllBlocks.POWERED_TOGGLE_LATCH);click(h);h.assertValueEqual(output(h),15,"Native use toggles on without back input");click(h);h.assertValueEqual(output(h),0,"Next native use toggles off");h.succeed();}
    @GameTest(maxTicks=35)public void poweredLatchBackSetsAndEitherSideResetsWithSourceRecoveryOnSideFall(GameTestHelper h){
        place(h,AllBlocks.POWERED_LATCH);input(h,true);h.runAfterDelay(5,()->{h.assertValueEqual(output(h),15,"Back sets native latch");h.setBlock(S.east(),Blocks.REDSTONE_BLOCK);});h.runAfterDelay(10,()->{h.assertValueEqual(output(h),0,"Side resets despite held back");h.setBlock(S.east(),Blocks.AIR);});h.runAfterDelay(15,()->{h.assertValueEqual(output(h),15,"Side falling restores held back");h.setBlock(S.west(),Blocks.REDSTONE_BLOCK);});h.runAfterDelay(21,()->{h.assertValueEqual(output(h),0,"Other physical side resets too");h.succeed();});
    }
    @GameTest(maxTicks=25)public void simultaneousBackAndSideRiseUsesSourceSideLastOrdering(GameTestHelper h){place(h,AllBlocks.POWERED_LATCH);input(h,true);h.setBlock(S.east(),Blocks.REDSTONE_BLOCK);h.runAfterDelay(6,()->{h.assertTrue(state(h).getValue(DiodeBlock.POWERED)&&state(h).getValue(PoweredLatchBlock.POWERED_SIDE)&&output(h)==0,"Both inputs latch, simultaneous side-last reset wins");h.succeed();});}
    @GameTest(maxTicks=20)public void poweredLatchManualToggleIsBlockedWhenOnlyOneInputIsHeld(GameTestHelper h){place(h,AllBlocks.POWERED_LATCH);input(h,true);h.runAfterDelay(5,()->{click(h);h.assertValueEqual(output(h),15,"Manual toggle cannot override exclusive set input");input(h,false);});h.runAfterDelay(11,()->{click(h);h.assertValueEqual(output(h),0,"Equal low inputs permit manual latch toggle");h.succeed();});}
    private static AnalogLeverBlockEntity lever(GameTestHelper h){return h.getBlockEntity(S,AnalogLeverBlockEntity.class);}
    private static void placeLever(GameTestHelper h){h.setBlock(S.below(),Blocks.STONE);h.setBlock(S,AllBlocks.ANALOG_LEVER.defaultBlockState().setValue(AnalogLeverBlock.FACE,AttachFace.FLOOR));}
    @GameTest public void analogLeverClampsAtBothStrengthBoundsAndPreservesSignalFaces(GameTestHelper h){
        placeLever(h);for(int i=0;i<20;i++)lever(h).changeState(false);h.assertValueEqual(lever(h).getState(),15,"Strength clamps15");for(var side:Direction.values())h.assertTrue(state(h).getSignal(h.getLevel(),h.absolutePos(S),side)==15&&state(h).getDirectSignal(h.getLevel(),h.absolutePos(S),side)==(side==Direction.UP?15:0),"Weak all sides, direct only support direction");for(int i=0;i<20;i++)lever(h).changeState(true);h.assertValueEqual(lever(h).getState(),0,"Reverse clamps0");h.succeed();
    }
    @GameTest(maxTicks=35)public void analogLeverDebouncesRealRedstoneNotificationForFifteenTicks(GameTestHelper h){
        placeLever(h);h.setBlock(S.south(),Blocks.REDSTONE_LAMP);click(h);h.assertTrue(lever(h).getState()==1&&lever(h).getChangeTimer()==15,"Native click changes displayed signal immediately");
        h.runAfterDelay(8,()->{h.assertFalse(h.getBlockState(S.south()).getValue(RedstoneLampBlock.LIT),"No premature real output notification");click(h);});h.runAfterDelay(17,()->h.assertFalse(h.getBlockState(S.south()).getValue(RedstoneLampBlock.LIT),"Second click restarts debounce"));h.runAfterDelay(27,()->{h.assertTrue(lever(h).getChangeTimer()==0&&h.getBlockState(S.south()).getValue(RedstoneLampBlock.LIT),"Real lamp is notified15ticks after last native click");h.succeed();});
    }
    @GameTest(maxTicks=20)public void analogNativePersistenceKeepsPendingDebounceWithoutAWorld(GameTestHelper h){placeLever(h);for(int i=0;i<7;i++)lever(h).changeState(false);h.runAfterDelay(5,()->{var data=lever(h).saveWithFullMetadata(h.getLevel().registryAccess());var restored=(AnalogLeverBlockEntity)BlockEntity.loadStatic(h.absolutePos(S),state(h),data,h.getLevel().registryAccess());h.assertTrue(restored!=null&&restored.getState()==7&&restored.getChangeTimer()==lever(h).getChangeTimer()&&restored.getChangeTimer()>0,"State/ChangeTimer survive native load before setLevel");h.succeed();});}
    @GameTest(maxTicks=20)public void unsupportedNativeDiodeDropsAndStopsPoweringTheActualOutput(GameTestHelper h){place(h,AllBlocks.POWERED_TOGGLE_LATCH);h.setBlock(S.south(),Blocks.REDSTONE_LAMP);click(h);h.runAfterDelay(3,()->{h.assertTrue(h.getBlockState(S.south()).getValue(RedstoneLampBlock.LIT),"Fixture output is really powered");h.setBlock(S.below(),Blocks.AIR);});h.runAfterDelay(12,()->{h.assertTrue(state(h).isAir()&&!h.getBlockState(S.south()).getValue(RedstoneLampBlock.LIT),"Native support removal destroys diode and notifies output off");h.succeed();});}
}
