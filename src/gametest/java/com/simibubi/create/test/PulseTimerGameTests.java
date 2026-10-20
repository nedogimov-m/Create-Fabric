package com.simibubi.create.test;

import com.simibubi.create.*;
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
import net.minecraft.world.phys.Vec3;

/** Actual autonomous native BE clocks, scheduled inhibit input and persisted phase. STAGED, UNRUN. */
public class PulseTimerGameTests {
    private static final BlockPos S=new BlockPos(4,4,4);
    private static PulseTimerBlockEntity timer(GameTestHelper h){return h.getBlockEntity(S,PulseTimerBlockEntity.class);}
    private static BlockState state(GameTestHelper h){return h.getBlockState(S);}
    private static void place(GameTestHelper h){h.setBlock(S.below(),Blocks.STONE);h.setBlock(S,AllBlocks.PULSE_TIMER.defaultBlockState().setValue(DiodeBlock.FACING,Direction.NORTH));}
    private static int signal(GameTestHelper h){return state(h).getSignal(h.getLevel(),h.absolutePos(S),Direction.NORTH);}
    private static void duty(GameTestHelper h,int interval,int expectedHigh){
        place(h);timer(h).setMaxState(interval);var high=new int[1];var wrap=new int[1];var previous=new int[]{-1};
        for(int tick=interval+1;tick<=interval*4;tick++)h.runAfterDelay(tick,()->{int phase=timer(h).getState();h.assertTrue(phase>=0&&phase<interval,"Real clock phase remains in source modulo interval");if(signal(h)==15)high[0]++;if(previous[0]>phase)wrap[0]++;previous[0]=phase;});
        h.runAfterDelay(interval*4+2,()->{h.assertValueEqual(high[0],expectedHigh*3,"Exactly source pulse width in three complete native clock periods");h.assertTrue(wrap[0]>=2,"Autonomous native ticker actually wrapped multiple times");h.succeed();});
    }
    @GameTest public void sourceDefaultTwentyAndMissingLegacyTimingDecodeWithoutALevel(GameTestHelper h){
        place(h);h.assertValueEqual(timer(h).getMaxState(),20,"Actual source timer default20, not shared repeater2");var tag=timer(h).saveWithFullMetadata(h.getLevel().registryAccess());tag.remove("ScrollValue");var restored=(PulseTimerBlockEntity)BlockEntity.loadStatic(h.absolutePos(S),state(h),tag,h.getLevel().registryAccess());h.assertTrue(restored!=null&&restored.getMaxState()==20,"Native missing legacy setting uses timer-specific default before setLevel");h.succeed();
    }
    @GameTest(maxTicks=20)public void minimumIntervalTwoHasOneTickDutyInsteadOfAlwaysOn(GameTestHelper h){duty(h,2,1);}
    @GameTest(maxTicks=25)public void intervalThreeRetainsSourceTwoTickPulse(GameTestHelper h){duty(h,3,2);}
    @GameTest(maxTicks=95)public void defaultTwentyProducesTwoHighTicksPerTwentyNativeTicks(GameTestHelper h){duty(h,20,2);}
    @GameTest(maxTicks=80)public void physicalBackInputInhibitsResetsAndReleaseRestartsClock(GameTestHelper h){
        place(h);timer(h).setMaxState(12);h.runAfterDelay(8,()->h.setBlock(S.north(),Blocks.REDSTONE_BLOCK));
        for(int tick=13;tick<40;tick++)h.runAfterDelay(tick,()->h.assertTrue(state(h).getValue(DiodeBlock.POWERED)&&timer(h).getState()==0&&signal(h)==0,"Held scheduled back input keeps real timer at0 with no output"));
        h.runAfterDelay(41,()->h.setBlock(S.north(),Blocks.AIR));var high=new int[1];for(int tick=45;tick<70;tick++)h.runAfterDelay(tick,()->{if(signal(h)==15)high[0]++;});h.runAfterDelay(72,()->{h.assertTrue(!state(h).getValue(DiodeBlock.POWERED)&&high[0]>=4,"Physical input release resumes periodic native pulses");h.succeed();});
    }
    @GameTest(maxTicks=35)public void invertedClockComplementsNativeOutputWhileInputBitIsFalse(GameTestHelper h){
        place(h);timer(h).setMaxState(5);h.setBlock(S,state(h).setValue(BrassDiodeBlock.INVERTED,true));var on=new int[1];var off=new int[1];
        for(int tick=6;tick<=20;tick++)h.runAfterDelay(tick,()->{int output=signal(h);h.assertTrue(!state(h).getValue(DiodeBlock.POWERED)&&output==(state(h).getValue(BrassDiodeBlock.POWERING)?0:15)&&state(h).getOwnSignal(h.getLevel(),h.absolutePos(S))==output,"Native ownSignal complements timer without input POWERED gating");if(output==15)on[0]++;else off[0]++;});h.runAfterDelay(23,()->{h.assertTrue(on[0]==9&&off[0]==6,"Source inverted duty is three low plus two high internal ticks per period");h.succeed();});
    }
    @GameTest(maxTicks=30)public void midperiodNativeSaveRestoresClockPhaseAndPeriodWithoutReplay(GameTestHelper h){
        place(h);timer(h).setMaxState(37);h.runAfterDelay(11,()->{var data=timer(h).saveWithFullMetadata(h.getLevel().registryAccess());var restored=(PulseTimerBlockEntity)BlockEntity.loadStatic(h.absolutePos(S),state(h),data,h.getLevel().registryAccess());h.assertTrue(restored!=null&&restored.getMaxState()==37&&restored.getState()==timer(h).getState()&&restored.getState()>1,"Native load preserves consumed source phase, not constructor0");h.succeed();});
    }
    @GameTest(maxTicks=30)public void changingLiveIntervalUsesSharedClampThenWrapsIntoTheNewClock(GameTestHelper h){
        place(h);timer(h).setMaxState(50);h.runAfterDelay(11,()->{h.assertTrue(timer(h).getState()>5,"Autonomous fixture consumed native time");timer(h).setMaxState(3);h.assertValueEqual(timer(h).getState(),3,"Source shared interval change clamps active phase");});h.runAfterDelay(15,()->{h.assertTrue(timer(h).getState()<3&&timer(h).getMaxState()==3,"Real subsequent native ticks wrap into new period");h.succeed();});
    }
    @GameTest public void sharedNativeSettingsPacketOwnsActualTimerAndKeepsBoundedAuthority(GameTestHelper h){
        place(h);var player=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(h.absolutePos(S)).add(0,0,1));var packet=new BrassDiodeSettings.SetTiming(h.absolutePos(S),72000);var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());try{BrassDiodeSettings.SetTiming.CODEC.encode(buffer,packet);h.assertTrue(BrassDiodeSettings.SetTiming.CODEC.decode(buffer).equals(packet),"Real shared payload roundtrip for timer");}finally{buffer.release();}h.assertTrue(BrassDiodeSettings.apply(player,packet)&&timer(h).getMaxState()==72000,"Server finds actual PulseTimer through shared diode interface");var far=new BlockPos(2600000,80,2600000);h.assertFalse(BrassDiodeSettings.apply(player,new BrassDiodeSettings.SetTiming(far,20)),"Unloaded timer configuration refused");h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"No timer settings loading ticket");h.succeed();
    }
    private static void direction(GameTestHelper h,Direction facing){h.setBlock(S.below(),Blocks.STONE);h.setBlock(S,AllBlocks.PULSE_TIMER.defaultBlockState().setValue(DiodeBlock.FACING,facing));timer(h).setMaxState(3);h.succeedWhen(()->{h.assertTrue(state(h).getValue(BrassDiodeBlock.POWERING),"Wait for real autonomous output phase");for(var side:Direction.values())h.assertTrue(state(h).getSignal(h.getLevel(),h.absolutePos(S),side)==(side==facing?15:0)&&state(h).shouldRedstoneWireConnectTo(h.getLevel(),h.absolutePos(S),side)==(side.getAxis()==facing.getAxis()),"Actual timer output and native wire direction "+facing);});}
    @GameTest(maxTicks=15)public void nativeTimerFacingNorth(GameTestHelper h){direction(h,Direction.NORTH);}
    @GameTest(maxTicks=15)public void nativeTimerFacingEast(GameTestHelper h){direction(h,Direction.EAST);}
    @GameTest(maxTicks=15)public void nativeTimerFacingSouth(GameTestHelper h){direction(h,Direction.SOUTH);}
    @GameTest(maxTicks=15)public void nativeTimerFacingWest(GameTestHelper h){direction(h,Direction.WEST);}
}
