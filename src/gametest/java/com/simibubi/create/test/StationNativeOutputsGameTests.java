package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.station.StationAssemblyPreflight;
import com.simibubi.create.content.trains.schedule.*;
import com.simibubi.create.content.trains.schedule.condition.ScheduledDelay;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.redstone.displayLink.*;
import com.simibubi.create.test.mixin.GameTestHelperNativeInfoAccessor;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Real station source capture, native comparator ticks and actual timetable/status display transfer. Authored2 UNRUN. */
public final class StationNativeOutputsGameTests {
    private static final BlockPos R=new BlockPos(3,6,4),T=R.east(),S=T.south(2),B=T.east().above(),C=B.east(),I=B.east(2),SEAT=I.above(),COMPARATOR=S.south(),LINK=S.above(),SIGN=S.south(3);
    private static final class Case implements AutoCloseable {
        final GameTestHelper h;final CarriagePlayerOwnerGameTests.Fixture source;final AtomicBoolean closed=new AtomicBoolean();Train train;
        Case(GameTestHelper h){this.h=h;source=CarriagePlayerOwnerGameTests.fixture(h);((GameTestHelperNativeInfoAccessor)(Object)h).create$getActualNativeTestInfo().addListener(new GameTestListener(){
            public void testStructureLoaded(GameTestInfo info){}
            public void testPassed(GameTestInfo info,GameTestRunner runner){close();}
            public void testFailed(GameTestInfo info,GameTestRunner runner){close();}
            public void testAddedForRerun(GameTestInfo old,GameTestInfo next,GameTestRunner runner){close();}
        });}
        void ready(){NativeCarriageSourceReadiness.require(h,List.of(B,C,I,SEAT,B.above()));h.assertTrue(StationAssemblyPreflight.check(source.station(),source.authorizer().player()).readyForCapture(),"Actual original source blocks, native NPC and existing station become ready for real physical capture");}
        void assemble(){h.assertTrue(source.station().tryAssemble(source.authorizer().player()),"Real station physically assembles its original cargo/NPC into one genuine Train");train=source.station().getStation().getPresentTrain();h.assertTrue(train!=null&&TrainManager.get(h.getLevel()).get(train.id)==train&&train.carriages.getFirst().anyAvailableEntity().getCarriage()==train.carriages.getFirst(),"Native output is tested against the actual canonical current Train owner");}
        void stationTick(){StationBlockEntity.tick(h.getLevel(),source.station().getBlockPos(),source.station().getBlockState(),source.station());}
        @Override public void close(){if(!closed.compareAndSet(false,true))return;try{
            source.rider().player().stopRiding();source.authorizer().player().stopRiding();
            if(train!=null&&TrainManager.get(h.getLevel()).get(train.id)==train){var station=source.station().getStation();if(station!=null&&train.getCurrentStation()!=station){station.reserveFor(train);train.arriveAt(station);}h.assertTrue(train.disassemble(Direction.EAST,h.absolutePos(T.above()),source.authorizer().player()),"Real fixture disassembly returns original world cargo/NPC ownership before cleanup");}
        }finally{source.rider().close();source.authorizer().close();}}
    }
    private static int output(GameTestHelper h){return h.getBlockState(S).getAnalogOutputSignal(h.getLevel(),h.absolutePos(S),Direction.NORTH);}
    private static int comparator(GameTestHelper h){return h.getBlockEntity(COMPARATOR,ComparatorBlockEntity.class).getOutputSignal();}
    @GameTest(structure="create_test:reservoir",maxTicks=240)public void actualStationPresenceDrivesNativeComparator15ThenZeroOnRealDeparture(GameTestHelper h){
        var test=new Case(h);h.setBlock(COMPARATOR,Blocks.COMPARATOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH));
        h.assertTrue(h.getBlockState(S).hasAnalogOutputSignal()&&output(h)==0&&comparator(h)==0,"Actual empty source station advertises comparator behavior and has no fabricated train presence");
        h.startSequence().thenWaitUntil(test::ready).thenExecute(test::assemble).thenWaitUntil(()->{
            h.assertTrue(test.source.station().getStation().getPresentTrain()==test.train&&output(h)==15&&comparator(h)==15,"The actual native comparator receives original source strength15 from the same current physically captured Train");
        }).thenExecute(()->test.train.leaveStation()).thenWaitUntil(()->{
            h.assertTrue(test.source.station().getStation().getPresentTrain()==null&&output(h)==0&&comparator(h)==0,"Real Train.leaveStation and normal station presence ticks notify native comparator departure back to0");
        }).thenExecute(test::close).thenSucceed();
    }
    private static DisplayLinkBlockEntity link(GameTestHelper h){return h.getBlockEntity(LINK,DisplayLinkBlockEntity.class);}
    private static String sign(GameTestHelper h){return h.getBlockEntity(SIGN,SignBlockEntity.class).getText(SignTextSlot.FRONT).getMessages(false).getFirst().getString();}
    private static void timetable(GameTestHelper h,Case test){
        var destination=new DestinationInstruction();destination.getData().putString("Text",test.source.station().getStation().name());var wait=new ScheduledDelay();wait.getData().putInt("Value",60);wait.getData().putInt("TimeUnit",1);
        var schedule=new Schedule(new ArrayList<>(List.of(new ScheduleEntry(destination,new ArrayList<>(List.of(new ArrayList<>(List.of(wait))))))),true,0);
        var actual=new ItemStack(AllItems.SCHEDULE);actual.set(AllDataComponents.TRAIN_SCHEDULE,schedule.write(h.getLevel().registryAccess()));var player=test.source.authorizer().player();player.getInventory().setItem(0,actual);
        var variant=ItemVariant.of(actual);var target=ItemStorage.SIDED.find(h.getLevel(),test.source.station().getBlockPos(),Direction.UP);h.assertTrue(target!=null,"Actual station exposes its genuine timetable depot storage");
        try(var tx=Transaction.openOuter()){h.assertTrue(target.insert(variant,1,tx)==1&&PlayerInventoryStorage.of(player).getSlot(0).extract(variant,1,tx)==1,"The real source player transfers exactly one actual physical timetable to the native station owner");tx.commit();}
        h.assertTrue(player.getInventory().getItem(0).isEmpty()&&test.source.station().getAutoSchedule().getCount()==1&&test.train.runtime.isAutoSchedule,"Actual committed station template configures its same real present Train once without another paid schedule item");
        // Enter the actual native arrival boundary for this real runtime/point.
        test.train.runtime.state=ScheduleRuntime.State.IN_TRANSIT;test.train.arriveAt(test.source.station().getStation());
    }
    @GameTest(structure="create_test:reservoir",maxTicks=240)public void actualPresentScheduleRequestRefreshesRealAttachedDisplayAndClearsOnlyAtItsStation(GameTestHelper h){
        var test=new Case(h);h.setBlock(SIGN,Blocks.OAK_SIGN);h.setBlock(LINK,AllBlocks.DISPLAY_LINK.defaultBlockState().setValue(DisplayLinkBlock.FACING,Direction.UP));link(h).target(h.absolutePos(SIGN));
        h.startSequence().thenWaitUntil(test::ready).thenExecute(test::assemble).thenExecute(()->{
            // Real station presence is published before its later physical timetable insertion.
            // Otherwise this very first presence tick reapplies the auto template after arrival,
            // correctly resetting runtime to PRE_TRANSIT before the status source can read it.
            test.stationTick();
            h.assertTrue(test.source.station().getStation().getPresentTrain()==test.train,"The actual native station presence tick resolves the canonical Train before timetable ownership changes");
            timetable(h,test);var config=new CompoundTag();config.putString("Id",AllDisplaySources.TRAIN_STATUS.getId().toString());h.assertTrue(link(h).configure(config,0),"Real attached display selects its native present-train status source and native sign target");
            h.assertTrue(!sign(h).isEmpty(),"Actual native scheduled wait has a real display status before testing its refresh request");
            var actual=h.getBlockEntity(SIGN,SignBlockEntity.class);actual.setText(actual.getText(SignTextSlot.FRONT).asMutable().setLine(0,Component.literal("Stale native status")).asImmutable(),SignTextSlot.FRONT);link(h).refreshTicks=17;test.train.runtime.displayLinkUpdateRequested=true;
            test.stationTick();
            h.assertTrue(!test.train.runtime.displayLinkUpdateRequested,"Actual present station consumes its genuine runtime request once; state="+test.train.runtime.state+", sign="+sign(h));
            h.assertTrue(link(h).refreshTicks==0,"The same request immediately ticks its real attached native display source; refresh="+link(h).refreshTicks+", facing="+link(h).getDirection());
            h.assertTrue(!sign(h).equals("Stale native status")&&!sign(h).isEmpty(),"Actual one-shot request transfers a nonempty real present Train status before passive refresh; state="+test.train.runtime.state+", paused="+test.train.runtime.paused+", sign="+sign(h));
            link(h).refreshTicks=11;test.stationTick();h.assertTrue(link(h).refreshTicks==11,"A consumed native request is not replayed on the next actual station tick");
            test.train.leaveStation();test.train.runtime.displayLinkUpdateRequested=true;test.stationTick();h.assertTrue(test.train.runtime.displayLinkUpdateRequested,"Absent actual train is not a source owner for consuming a pending runtime display request");
        }).thenExecute(test::close).thenSucceed();
    }
}
