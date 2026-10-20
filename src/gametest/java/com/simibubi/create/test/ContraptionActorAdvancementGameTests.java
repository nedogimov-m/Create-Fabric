package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.gantry.*;
import com.simibubi.create.content.contraptions.piston.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.gantry.*;
import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.test.mixin.GameTestHelperNativeInfoAccessor;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;

/** Actual source bodies and independent station/operator UUIDs. Authored; runtime UNRUN. */
public final class ContraptionActorAdvancementGameTests {
    private static final String STRUCTURE="create_test:reservoir";
    private static final BlockPos R=new BlockPos(10,8,12), PINION=R.above(), PISTON=new BlockPos(12,8,12);
    private static final BlockPos RAIL=new BlockPos(3,6,4), TARGET=RAIL.east(), STATION=TARGET.south(2), BOGEY=TARGET.east().above();

    private static boolean done(ServerPlayer player,String id){return GameplayAdvancementGameTests.done(player,id);}
    private static void placed(GameTestHelper h,BlockPos pos,ServerPlayer player){GameplayAdvancementGameTests.placed(h,pos,player);}
    private static void speed(KineticBlockEntity be,float rpm){GameplayAdvancementGameTests.drive(be,rpm);}

    private static final class Case implements AutoCloseable {
        final GameTestHelper h;
        final NativeSurvivalTestPlayer owner,operator;
        final AtomicBoolean closed=new AtomicBoolean();
        GantryContraptionEntity gantry;
        MechanicalPistonBlockEntity piston;
        StationBlockEntity station;
        Train train;
        Case(GameTestHelper h,BlockPos near){
            this.h=h;
            var at=Vec3.atCenterOf(h.absolutePos(near));
            owner=NativeSurvivalTestPlayer.join(h,at);operator=NativeSurvivalTestPlayer.join(h,at.add(0,0,1));
            h.assertFalse(owner.player().getUUID().equals(operator.player().getUUID()),"Two independent real registered survival UUIDs");
            ((GameTestHelperNativeInfoAccessor)(Object)h).create$getActualNativeTestInfo().addListener(new GameTestListener(){
                public void testStructureLoaded(GameTestInfo info){}
                public void testPassed(GameTestInfo info,GameTestRunner runner){close();}
                public void testFailed(GameTestInfo info,GameTestRunner runner){close();}
                public void testAddedForRerun(GameTestInfo old,GameTestInfo next,GameTestRunner runner){close();}
            });
        }
        @Override public void close(){
            if(!closed.compareAndSet(false,true))return;
            try{
                if(gantry!=null&&gantry.hasOwnedStructure())h.assertTrue(gantry.disassemble(),"Actual gantry body restores before fixture cleanup");
                if(piston!=null&&piston.getMovedContraption()!=null)h.assertTrue(piston.disassemble(),"Actual piston body restores before fixture cleanup");
                if(train!=null&&TrainManager.get(h.getLevel()).get(train.id)==train)
                    h.assertTrue(train.disassemble(Direction.EAST,h.absolutePos(TARGET.above()),operator.player()),"Actual original train bodies restore before fixture cleanup");
            }finally{owner.close();operator.close();}
        }
    }

    @GameTest(structure=STRUCTURE,maxTicks=240)
    public void acceptedGantryHarvesterBodyAwardsOnlyItsOriginalPlacingOwner(GameTestHelper h){
        var test=new Case(h,PINION.south(3));
        for(int i=0;i<5;i++)h.setBlock(R.east(i),AllBlocks.GANTRY_SHAFT.defaultBlockState().setValue(GantryShaftBlock.FACING,Direction.EAST));
        h.setBlock(PINION,AllBlocks.GANTRY_CARRIAGE.defaultBlockState().setValue(GantryCarriageBlock.FACING,Direction.UP).setValue(GantryCarriageBlock.AXIS_ALONG_FIRST_COORDINATE,false));
        h.setBlock(PINION.above(),AllBlocks.MECHANICAL_HARVESTER);
        placed(h,PINION,test.owner.player());
        var carriage=h.getBlockEntity(PINION,GantryCarriageBlockEntity.class);
        h.startSequence().thenWaitUntil(()->NativeCarriageSourceReadiness.require(h,List.of(R,PINION,PINION.above())))
            .thenExecute(()->{
                try{
                    h.setBlock(R.north(),Blocks.REDSTONE_BLOCK);
                    h.assertFalse(carriage.assemble(),"Actual powered rack refuses movement and does not capture the harvester");
                    h.assertTrue(!done(test.owner.player(),"contraption_actors")&&h.getBlockState(PINION.above()).is(AllBlocks.MECHANICAL_HARVESTER),"Refused actual assembly awards nobody and retains the original source actor");
                    h.setBlock(R.north(),Blocks.AIR);
                    for(int i=0;i<5;i++)speed(h.getBlockEntity(R.east(i),GantryShaftBlockEntity.class),-64);
                    h.assertTrue(carriage.assemble(),"Actual native gantry reservation and complete original actor-body capture");
                    var owners=h.getLevel().getEntitiesOfClass(GantryContraptionEntity.class,new AABB(h.absolutePos(PINION)).inflate(3));
                    h.assertValueEqual(owners.size(),1,"One actual native gantry entity owns the captured source body");test.gantry=owners.getFirst();
                    h.assertTrue(test.gantry.getType()==AllEntityTypes.GANTRY_CONTRAPTION&&test.gantry.hasOwnedStructure()
                        &&test.gantry.getContraption().containsBlockBreakers()&&h.getBlockState(PINION.above()).isAir(),"The accepted carrier owns the actual removed harvester, not metadata alone");
                    h.assertTrue(done(test.owner.player(),"contraption_actors")&&!done(test.operator.player(),"contraption_actors"),"Successful real gantry actor assembly awards only the exact original placing UUID");
                }catch(AssemblyException error){
                    var failure=h.assertionException("Unexpected actual source assembly failure: "+error.getMessage());
                    failure.initCause(error);throw failure;
                }
            }).thenExecute(test::close).thenSucceed();
    }

    @GameTest(structure=STRUCTURE,maxTicks=240)
    public void acceptedPistonHarvesterBodyAwardsOnlyItsOriginalPlacingOwner(GameTestHelper h){
        var test=new Case(h,PISTON.south(3));
        h.setBlock(PISTON,AllBlocks.MECHANICAL_PISTON.defaultBlockState().setValue(MechanicalPistonBlock.FACING,Direction.EAST));
        for(int i=1;i<=3;i++)h.setBlock(PISTON.west(i),AllBlocks.PISTON_EXTENSION_POLE.defaultBlockState().setValue(PistonExtensionPoleBlock.FACING,Direction.EAST));
        h.setBlock(PISTON.east(),AllBlocks.MECHANICAL_HARVESTER);
        placed(h,PISTON,test.owner.player());test.piston=h.getBlockEntity(PISTON,MechanicalPistonBlockEntity.class);
        h.startSequence().thenWaitUntil(()->NativeCarriageSourceReadiness.require(h,List.of(PISTON,PISTON.east(),PISTON.west(),PISTON.west(2),PISTON.west(3))))
            .thenExecute(()->{
                try{
                    speed(test.piston,0);h.assertFalse(test.piston.assemble(),"Actual stopped piston cannot start translating the actor body");
                    h.assertTrue(!done(test.owner.player(),"contraption_actors")&&h.getBlockState(PISTON.east()).is(AllBlocks.MECHANICAL_HARVESTER),"Refused stopped source leaves real actor and progress unchanged");
                    speed(test.piston,-256);h.assertTrue(test.piston.assemble(),"Actual outward piston reservation and complete actor-body capture");
                    var moved=test.piston.getMovedContraption();
                    h.assertTrue(moved!=null&&moved.hasOwnedStructure()&&moved.getContraption().containsBlockBreakers()
                        &&h.getBlockState(PISTON.east()).isAir(),"One actual admitted moving body owns the removed original harvester and spindle");
                    h.assertTrue(done(test.owner.player(),"contraption_actors")&&!done(test.operator.player(),"contraption_actors"),"Successful real piston actor assembly awards only its exact original placing UUID");
                }catch(AssemblyException error){
                    var failure=h.assertionException("Unexpected actual source assembly failure: "+error.getMessage());
                    failure.initCause(error);throw failure;
                }
            }).thenExecute(test::close).thenSucceed();
    }

    private static void station(GameTestHelper h,Case test){
        var rail=AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.XO);
        for(int i=0;i<25;i++){h.setBlock(RAIL.east(i).below(),Blocks.STONE);h.setBlock(RAIL.east(i),rail);}
        for(int i=0;i<25;i++)h.assertTrue(TrackPropagator.reconcile(h.getLevel(),h.absolutePos(RAIL.east(i)),rail,false).complete(),"Actual native rail graph is reconciled");
        for(int dx=-2;dx<=2;dx++)for(int dz=0;dz<=3;dz++)h.setBlock(STATION.offset(dx,-1,dz),Blocks.STONE);
        var stock=new ItemStack(AllBlocks.TRACK_STATION);test.owner.player().setItemInHand(InteractionHand.MAIN_HAND,stock);
        var support=h.absolutePos(STATION.below());
        var hit=new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false);
        h.assertTrue(((BlockItem)stock.getItem()).place(new BlockPlaceContext(test.owner.player(),InteractionHand.MAIN_HAND,stock,hit)).consumesAction()
            &&stock.isEmpty(),"Literal original station owner physically places and pays one native station BlockItem");
        test.station=h.getBlockEntity(STATION,StationBlockEntity.class);
        h.assertTrue(test.station.setTarget(new TrackTargetingSelection(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(TARGET)),true,new Vec3(1,0,0),Optional.empty())),"Actual placed station binds its source native graph point");
        h.assertTrue(test.station.configure(test.operator.player(),"Distinct owner source",true,DoorControl.ALL),"The separate real operator remains authorized by ordinary station permissions");
        var data=test.station.saveWithFullMetadata(h.getLevel().registryAccess());
        h.assertValueEqual(data.read("Owner",UUIDUtil.CODEC).orElseThrow(),test.owner.player().getUUID(),"Actual original placing UUID is persisted in the genuine station save");
        var previous=test.station;
        var restored=BlockEntity.loadStatic(previous.getBlockPos(),previous.getBlockState(),data,h.getLevel().registryAccess());
        h.assertTrue(restored instanceof StationBlockEntity&&restored.getLevel()==null,"Native station factory decodes the actual saved state before level binding");
        restored.setLevel(h.getLevel());h.getLevel().setBlockEntity(restored);test.station=(StationBlockEntity)restored;
        h.assertValueEqual(test.station.advancementOwnerStorage().getPlayerId(),test.owner.player().getUUID(),"Native reload preserves source award recipient independently of operator authorization UUID");
        for(int i=0;i<6;i++){
            var bogey=BOGEY.east(4*i);
            h.setBlock(bogey,AllBlocks.SMALL_BOGEY.defaultBlockState().setValue(AbstractBogeyBlock.AXIS,Direction.Axis.X));
            h.setBlock(bogey.above(),i==0?AllBlocks.TRAIN_CONTROLS.defaultBlockState().setValue(ControlsBlock.FACING,Direction.EAST):Blocks.STONE.defaultBlockState());
            var end=bogey.above();
            if(i==0){end=end.east();h.setBlock(end,AllBlocks.MECHANICAL_HARVESTER);}
            h.assertTrue(h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(bogey),h.absolutePos(end)))),"Real native glue joins each independent original carriage body");
        }
    }

    @GameTest(structure=STRUCTURE,maxTicks=240)
    public void reloadedStationAwardsItsOriginalOwnerForSixRealHarvesterCarriagesAssembledByDifferentOperator(GameTestHelper h){
        var test=new Case(h,STATION.south());station(h,test);
        var sourceCells=new ArrayList<BlockPos>();
        sourceCells.add(STATION);sourceCells.add(BOGEY.above().east());
        for(int i=0;i<6;i++){sourceCells.add(BOGEY.east(4*i));sourceCells.add(BOGEY.east(4*i).above());}
        h.startSequence().thenWaitUntil(()->{
            NativeCarriageSourceReadiness.require(h,sourceCells);
            h.assertTrue(StationAssemblyPreflight.check(test.station,test.operator.player()).readyForCapture(),"Naturally ready original six-carriage source passes actual operator permissions and physical preflight");
        }).thenExecute(()->{
            h.assertTrue(test.station.tryAssemble(test.operator.player()),"Distinct actual operator physically captures the original six independent carriage bodies");
            test.train=test.station.getStation().getPresentTrain();
            h.assertTrue(test.train!=null&&TrainManager.get(h.getLevel()).get(test.train.id)==test.train&&test.train.carriages.size()==6,
                "The native manager owns exactly six actual source carriages");
            h.assertValueEqual(test.train.owner,test.operator.player().getUUID(),"Actual train ownership/authorization UUID retains the separate assembling operator");
            h.assertTrue(test.train.carriages.stream().map(Carriage::anyAvailableEntity).allMatch(body->body!=null&&body.hasOwnedStructure()),"All six actual admitted native bodies retain their source ownership");
            var harvester=test.train.carriages.getFirst().anyAvailableEntity().getContraption();
            h.assertTrue(harvester.containsBlockBreakers()&&harvester.getBlocks().values().stream().anyMatch(row->row.state().is(AllBlocks.MECHANICAL_HARVESTER)),"The actual first captured source contains a real harvester actor");
            for(var id:List.of("train","long_train","contraption_actors"))
                h.assertTrue(done(test.owner.player(),id)&&!done(test.operator.player(),id),"Native source "+id+" awards persisted station placer, independently of the authorized operator");
        }).thenExecute(test::close).thenSucceed();
    }
}
