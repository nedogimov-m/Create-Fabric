package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.actors.seat.*;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.trains.*;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.schedule.*;
import com.simibubi.create.content.trains.schedule.destination.ChangeTitleInstruction;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.content.trains.track.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.*;

/** Actual station-created Train owners, real paid conductor item and durable native restoration. */
public final class StationModeEntryGameTests {
  private static final String STRUCTURE="create_test:reservoir";
  private static final BlockPos R=new BlockPos(3,6,4),T=R.east(),S=T.south(2),B=T.east().above(),BARREL=B.above().south(),SEAT=BARREL.south(),BURNER=B.above().east(),SECOND=B.east(8);
  private record Fixture(StationBlockEntity station,ServerPlayer player,Train train,Entity rider,List<UUID> originalBodies,ItemStack schedule,boolean second){}
  private static ItemStack cargo(boolean second){var stack=new ItemStack(Items.DIAMOND,second?3:7);stack.set(DataComponents.CUSTOM_NAME,Component.literal(second?"Original second entry cargo":"Original first entry cargo"));return stack;}
  private static void glue(GameTestHelper h,BlockPos from,BlockPos to){h.assertTrue(h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(from),h.absolutePos(to)))),"Original source cells are joined by real native glue");}
  private static List<ItemEntity> schedules(GameTestHelper h,Fixture f){return h.getLevel().getEntities(EntityTypes.ITEM,new AABB(f.station.getBlockPos()).inflate(8),e->ItemStack.matches(e.getItem(),f.schedule));}
  private static long cargoCount(Train train,ItemStack stack){long count=0;for(var row:train.carriages)for(var view:row.storage.getAllItems())if(view.getResource().equals(ItemVariant.of(stack)))count+=view.getAmount();return count;}
  private static void paidSchedule(GameTestHelper h,Fixture f){
    f.player.setItemInHand(InteractionHand.MAIN_HAND,f.schedule.copy());
    var owner=f.train.carriages.getFirst().anyAvailableEntity();var local=BURNER.subtract(B);var info=owner.getContraption().getBlocks().get(local);
    var behavior=MovingInteractionBehaviour.REGISTRY.get(info.state());
    h.assertTrue(behavior!=null&&behavior.handlePlayerInteraction(f.player,InteractionHand.MAIN_HAND,local,owner),"The actual captured burner conductor consumes the real paid schedule");
    h.assertTrue(f.player.getMainHandItem().isEmpty()&&f.train.runtime.getSchedule()!=null,"One original schedule becomes private Train runtime custody");
  }
  private static void run(GameTestHelper h,boolean second,Consumer<Fixture> action){
    var rail=AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.XO);
    for(int i=0;i<25;i++){h.setBlock(R.east(i).below(),Blocks.STONE);h.setBlock(R.east(i),rail);}
    for(int i=0;i<25;i++)h.assertTrue(TrackPropagator.reconcile(h.getLevel(),h.absolutePos(R.east(i)),rail,false).complete(),"Actual native rail graph is reconciled");
    h.setBlock(S,AllBlocks.TRACK_STATION);var station=h.getBlockEntity(S,StationBlockEntity.class);
    h.assertTrue(station.setTarget(new TrackTargetingSelection(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(T)),true,new Vec3(1,0,0),Optional.empty())),"The genuine native station owns its original graph point");
    var player=h.makeMockServerPlayerInLevel();player.setPos(Vec3.atCenterOf(station.getBlockPos()).add(0,0,1));
    h.assertTrue(station.configure(player,"Original entry station",true,DoorControl.ALL),"Actual player enters the empty original assembly strip");
    h.setBlock(B,AllBlocks.SMALL_BOGEY.defaultBlockState().setValue(AbstractBogeyBlock.AXIS,Direction.Axis.X));
    h.setBlock(B.above(),AllBlocks.TRAIN_CONTROLS.defaultBlockState().setValue(ControlsBlock.FACING,Direction.EAST));
    h.setBlock(B.above().east(2),AllBlocks.TRAIN_CONTROLS.defaultBlockState().setValue(ControlsBlock.FACING,Direction.WEST));
    h.setBlock(BURNER,AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.SMOULDERING));
    h.setBlock(BARREL,Blocks.BARREL);h.getBlockEntity(BARREL,BarrelBlockEntity.class).setItem(0,cargo(false));
    h.setBlock(SEAT,AllBlocks.SEATS.get(DyeColor.RED));glue(h,B,SEAT.east(2));
    var rider=h.spawnWithNoFreeWill(EntityTypes.PIG,SEAT.above());rider.setNoGravity(true);h.assertTrue(SeatBlock.trySitDown(h.getLevel(),h.absolutePos(SEAT),rider),"The original rider enters the true source seat before capture");
    if(second){h.setBlock(SECOND,AllBlocks.SMALL_BOGEY.defaultBlockState().setValue(AbstractBogeyBlock.AXIS,Direction.Axis.X));h.setBlock(SECOND.east(),Blocks.BARREL);h.getBlockEntity(SECOND.east(),BarrelBlockEntity.class).setItem(0,cargo(true));h.setBlock(SECOND.east(2),StationModeEntryTestFixtures.ACTOR);glue(h,SECOND,SECOND.east(2));}
    var actual=new AtomicReference<Fixture>();
    h.startSequence().thenWaitUntil(()->{
      var source=new ArrayList<>(List.of(B,B.above(),B.above().east(2),BURNER,BARREL,SEAT));
      if(second)source.addAll(List.of(SECOND,SECOND.east(),SECOND.east(2)));
      NativeCarriageSourceReadiness.require(h,source);
      h.assertTrue(station.preflight(player).readyForCapture(),"Real entity and wheel-point readiness must arise through native ticks");
    })
      .thenExecute(()->{
        h.assertTrue(station.tryAssemble(player),"Actual full source capture, native acceptance and Train adoption completes");
        var train=station.getStation().getPresentTrain();h.assertTrue(train!=null&&TrainManager.get(h.getLevel()).get(train.id)==train&&!station.isAssembling(),"Only the actual adopted real Train is used");
        var instruction=new ChangeTitleInstruction();instruction.getData().putString("Text","Entry "+UUID.randomUUID());var schedule=new Schedule(new ArrayList<>(List.of(new ScheduleEntry(instruction,new ArrayList<>()))),true,0);
        var item=new ItemStack(AllItems.SCHEDULE);item.set(AllDataComponents.TRAIN_SCHEDULE,schedule.write(h.getLevel().registryAccess()));
        var fixture=new Fixture(station,player,train,rider,train.carriages.stream().map(row->row.anyAvailableEntity().getUUID()).toList(),item,second);actual.set(fixture);paidSchedule(h,fixture);action.accept(fixture);
      }).thenWaitUntil(()->{
        var f=actual.get();h.assertTrue(f!=null,"Actual native source capture must complete before checking station-mode restoration");h.assertTrue(f.station.isAssembling()&&!f.station.stationModeEntryPending(),"The native station enters mode only after its real durable intent completes");
        h.assertTrue(TrainManager.get(h.getLevel()).get(f.train.id)==null&&f.train.hasCompletedDisassembly(),"Every original real carriage receipt is consumed and the native schedule owner is empty before registry retirement");
        h.assertTrue(ItemStack.matches(h.getBlockEntity(BARREL,BarrelBlockEntity.class).getItem(0),cargo(false)),"The original first named cargo is world-owned exactly once");
        if(f.second)h.assertTrue(ItemStack.matches(h.getBlockEntity(SECOND.east(),BarrelBlockEntity.class).getItem(0),cargo(true)),"The second actual root restores its own exact original cargo once");
        h.assertTrue(h.getLevel().getEntity(f.rider.getUUID())==f.rider&&f.rider.getVehicle() instanceof SeatEntity,"The original rider object and UUID return to an actual source-world seat");
        h.assertTrue(f.originalBodies.stream().allMatch(id->h.getLevel().getEntity(id)==null)&&f.train.carriages.stream().allMatch(row->row.isWorldRestored()&&!row.storage.ownsContents()),"Original native carriage bodies are retired; only consumed row receipts remain");
        long returned=schedules(h,f).size()+(ItemStack.matches(f.player.getMainHandItem(),f.schedule)?1:0);h.assertValueEqual(returned,1L,"Exactly one real paid schedule reaches native hand or accepted world item");
      }).thenSucceed();
  }
  @GameTest(structure=STRUCTURE,maxTicks=300) public void enteringAssemblyModeRestoresActualTrainAndReturnsOnePaidSchedule(GameTestHelper h){
    run(h,false,f->{h.assertTrue(f.station.configure(f.player,"Restored entry station",true,DoorControl.EAST),"Source mode-entry starts genuine disassembly of its present Train");h.assertTrue(f.station.stationModeEntryPending()||f.station.isAssembling(),"The user request is durably pending or physically completed");});
  }
  @GameTest(structure=STRUCTURE,maxTicks=300) public void realDestinationObstructionRefusesBeforeSourceCustodyChangesAndRetrySucceeds(GameTestHelper h){
    run(h,false,f->{
      h.setBlock(BARREL,Blocks.OBSIDIAN);
      h.assertFalse(f.station.configure(f.player,"Blocked entry",true,DoorControl.ALL),"Actual occupied destination refuses native restoration before mode changes");
      h.assertTrue(!f.station.isAssembling()&&!f.station.stationModeEntryPending()&&!f.train.isRestoring()&&TrainManager.get(h.getLevel()).get(f.train.id)==f.train,"The exact original Train remains its only active native owner");
      h.assertTrue(cargoCount(f.train,cargo(false))==7&&f.train.runtime.getSchedule()!=null&&schedules(h,f).isEmpty()&&f.train.carriages.getFirst().anyAvailableEntity().getUUID().equals(f.originalBodies.getFirst()),"Refusal retains original body UUID, named cargo and the actual private paid schedule");
      h.setBlock(BARREL,Blocks.AIR);h.assertTrue(f.station.configure(f.player,"Retry entry",true,DoorControl.ALL),"Removing the real obstruction permits the original native request to succeed");
    });
  }
  @GameTest(structure=STRUCTURE,maxTicks=300) public void partialRealRootRestorationKeepsDurableIntentAndResumesWithoutReplayingCargo(GameTestHelper h){
    run(h,true,f->{
      var second=f.train.carriages.get(1);StationModeEntryTestFixtures.failOnce(second.anyAvailableEntity().getUUID());boolean failed=false;
      try{f.station.configure(f.player,"Partial entry",true,DoorControl.ALL);}catch(StationModeEntryTestFixtures.PlannedRestoreFailure actual){failed=true;}
      h.assertTrue(failed&&f.train.carriages.getFirst().isWorldRestored()&&!second.isWorldRestored()&&second.storage.ownsContents(),"A real second-root actor failure leaves the first consumed native receipt and the still-owned original pending row");
      h.assertTrue(!f.station.isAssembling()&&f.station.stationModeEntryPending()&&f.train.isRestoring()&&!f.train.hasCompletedDisassembly()&&f.train.runtime.getSchedule()!=null,"Partial physical placement cannot complete mode-entry or return the still-owned paid schedule");
      h.assertFalse(f.station.configure(f.player,"Partial entry",false,DoorControl.ALL),"A mode cancellation cannot abandon an already transferred native formation");
      var other=h.makeMockPlayer(GameType.SURVIVAL);other.setPos(f.player.position());h.assertFalse(f.station.configure(other,"Partial entry",true,DoorControl.ALL),"Another actual player cannot replace the recorded restoration requester");
      h.assertTrue(f.station.configure(f.player,"Partial entry",true,DoorControl.ALL),"The original exact request can be repeated without replaying consumed roots");
      var saved=f.station.saveWithoutMetadata(h.getLevel().registryAccess());h.assertTrue(saved.contains("AssemblyEntryIntent")&&!f.station.getUpdateTag(h.getLevel().registryAccess()).contains("AssemblyEntryIntent"),"Native station persistence keeps the actual private intent while its real client snapshot excludes it");
      f.station.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),saved));
      h.assertTrue(f.station.getStation()==null,"Actual native BE load clears its transient graph binding before the ordinary Train tick resumes the real completion receipt");
      h.assertTrue(f.station.stationModeEntryPending()&&ItemStack.matches(h.getBlockEntity(BARREL,BarrelBlockEntity.class).getItem(0),cargo(false))&&cargoCount(f.train,cargo(true))==3,"Explicit real Station native save/load preserves pending intent without duplicating either actual cargo owner; this is not a chunk-unload claim");
      TrainManager.get(h.getLevel()).validateCustody();
    });
  }
  @GameTest(structure=STRUCTURE,maxTicks=300) public void nativeScheduleItemRefusalKeepsCompletedBodiesPendingUntilRealReturnIsAccepted(GameTestHelper h){
    run(h,false,f->{
      var armed=new AtomicBoolean(true);var refusals=new AtomicInteger();
      var originalHand=new ItemStack(Items.STICK);originalHand.set(DataComponents.CUSTOM_NAME,Component.literal("Original occupied schedule-return hand"));
      f.player.setItemInHand(InteractionHand.MAIN_HAND,originalHand.copy());
      h.assertTrue(h.getLevel().getServer().getPlayerList().getPlayer(f.player.getUUID())==f.player&&ItemStack.matches(f.player.getMainHandItem(),originalHand),"The actual registered player's occupied original hand requires a genuine native world-item schedule return");
      ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{if(world==h.getLevel()&&entity instanceof ItemEntity item&&ItemStack.matches(item.getItem(),f.schedule)&&armed.compareAndSet(true,false)){refusals.incrementAndGet();entity.discard();}});
      h.assertTrue(f.station.configure(f.player,"Return-pending entry",true,DoorControl.ALL),"The real mode request retains accepted durable custody when its native item return is refused");armed.set(false);
      h.assertTrue(refusals.get()==1&&f.train.carriages.stream().allMatch(Carriage::isWorldRestored)&&!f.train.hasCompletedDisassembly()&&!f.station.isAssembling()&&f.station.stationModeEntryPending(),"Real consumed body receipts alone cannot authorize mode-entry while one actual paid item is privately pending");
      h.assertTrue(ItemStack.matches(f.player.getMainHandItem(),originalHand),"The refused native schedule drop cannot overwrite the player's original occupied hand");
      var nativeTrain=f.train.write(new com.simibubi.create.content.trains.graph.DimensionPalette(),h.getLevel().registryAccess());h.assertTrue(nativeTrain.contains("DisassemblySchedule")&&schedules(h,f).isEmpty(),"Native durable Train data owns the original schedule until a real hand/world return is accepted");
    });
  }
}
