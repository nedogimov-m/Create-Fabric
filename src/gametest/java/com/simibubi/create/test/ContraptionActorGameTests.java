package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.bearing.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.drill.*;
import com.simibubi.create.content.kinetics.saw.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

public class ContraptionActorGameTests {
    private static final BlockPos P=new BlockPos(3,4,4),B=P.below(),TARGET=P.east(2).north();
    private static BlockState drill(){return AllBlocks.MECHANICAL_DRILL.defaultBlockState().setValue(DrillBlock.FACING,Direction.NORTH);}
    private static BlockState saw(){return AllBlocks.MECHANICAL_SAW.defaultBlockState().setValue(SawBlock.FACING,Direction.NORTH);}
    private static ControlledContraptionEntity moving(GameTestHelper h,Map<BlockPos,BlockState> blocks)throws Exception{
        blocks.forEach(h::setBlock);var min=blocks.keySet().stream().reduce((a,b)->new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()))).orElseThrow();var max=blocks.keySet().stream().reduce((a,b)->new BlockPos(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()))).orElseThrow();
        if(blocks.size()>1)h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(min),h.absolutePos(max))));
        var structure=new BearingContraption(false,Direction.UP);h.assertTrue(structure.assemble(h.getLevel(),h.absolutePos(B)),"Assemble");h.assertTrue(structure.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO),"Take ownership");var owner=new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION,h.getLevel());owner.setContraption(structure);owner.setPos(Vec3.atLowerCornerOf(h.absolutePos(P)));return owner;
    }
    private static MovementContext actor(ControlledContraptionEntity owner){return owner.getContraption().getActors().getFirst().right;}
    private static long count(ControlledContraptionEntity owner,Item item){long n=0;for(var view:owner.getContraption().getStorage().getAllItems())if(view.getResource().isOf(item))n+=view.getAmount();return n;}
    private static List<ItemEntity> drops(GameTestHelper h,ControlledContraptionEntity owner){return h.getLevel().getEntitiesOfClass(ItemEntity.class,owner.getBoundingBox().inflate(3));}
    private static void machine(GameTestHelper h,BlockState tool,BlockState target){
        h.setBlock(P,Blocks.STONE);h.setBlock(P.east(),Blocks.BARREL);h.setBlock(P.east(2),tool);h.setBlock(TARGET,target);h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(P),h.absolutePos(P.east(2)))));
        h.setBlock(B,AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP));h.setBlock(B.below(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING,Direction.UP));
    }
    private static MechanicalBearingBlockEntity bearing(GameTestHelper h){return h.getBlockEntity(B,MechanicalBearingBlockEntity.class);}
    @GameTest public void originalLifecycleVisitsTransformedPositionAndReportsMotion(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,Blocks.STONE.defaultBlockState(),P.east(),Blocks.STONE.defaultBlockState(),P.east(2),ContraptionTestFixtures.ACTOR.defaultBlockState()));var ctx=actor(owner);h.assertValueEqual(ctx.data.getIntOr("Starts",0),1,"Started once");owner.tickActors();h.assertValueEqual(ctx.data.getIntOr("Visits",0),0,"No initial fictitious motion");var previous=ctx.position;owner.setAngle(30);owner.tickActors();h.assertTrue(ctx.position.distanceTo(owner.toGlobalVector(Vec3.atCenterOf(ctx.localPos),1))<1e-8,"Actor uses actual transformed point");h.assertTrue(ctx.motion.distanceTo(ctx.position.subtract(previous))<1e-8,"World motion");h.assertTrue(ctx.relativeMotion.distanceTo(owner.reverseRotation(ctx.motion,1))<1e-8,"Local motion");h.assertValueEqual(ctx.data.getIntOr("Visits",0),1,"First movement visits");h.assertValueEqual(ctx.data.getIntOr("SpeedChanges",0),1,"Speed hook");h.succeed();
    }
    @GameTest public void stalledActorKeepsItsBreakingMotionAndDisableReleasesOwner(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,ContraptionTestFixtures.ACTOR.defaultBlockState()));var ctx=actor(owner);owner.tickActors();owner.setPos(owner.position().add(.2,0,0));owner.tickActors();var motion=ctx.motion;ctx.stall=true;owner.tickActors();h.assertTrue(owner.isStalled()&&ctx.motion.equals(motion),"Stalled motion survives stationary pose");owner.getContraption().setActorsActive(ItemStack.EMPTY,false);owner.tickActors();h.assertFalse(owner.isStalled(),"Disabled actor releases owner");h.succeed();
    }
    @GameTest public void disabledActorTicksOnlyWhenBehaviourExplicitlyRequestsIt(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,ContraptionTestFixtures.ACTOR.defaultBlockState(),P.east(),ContraptionTestFixtures.ALWAYS_TICK_ACTOR.defaultBlockState()));owner.getContraption().setActorsActive(ItemStack.EMPTY,false);owner.tickActors();for(var pair:owner.getContraption().getActors())h.assertValueEqual(pair.right.data.getIntOr("Ticks",0),pair.left.state().is(ContraptionTestFixtures.ALWAYS_TICK_ACTOR)?1:0,"Disabled ticking contract");h.succeed();
    }
    @GameTest public void sourceActorStatePersistsWithoutCallingStartAgain(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,ContraptionTestFixtures.ACTOR.defaultBlockState()));var ctx=actor(owner);ctx.motion=new Vec3(.2,0,.1);ctx.relativeMotion=new Vec3(0,0,.2);ctx.position=owner.position();ctx.stall=true;ctx.firstMovement=false;var saved=owner.getContraption().writeNBT(h.getLevel().registryAccess(),false);var restored=Contraption.fromNBT(h.getLevel(),saved,false).getActors().getFirst().right;h.assertTrue(restored.position.equals(ctx.position)&&restored.motion.equals(ctx.motion)&&restored.relativeMotion.equals(ctx.relativeMotion)&&restored.stall&&!restored.firstMovement,"Persistent source context");h.assertValueEqual(restored.data.getIntOr("Starts",0),1,"No repeated start on load");h.assertTrue(restored.data.getBooleanOr("ExtraWritten",false),"Extra data hook");h.succeed();
    }
    @GameTest public void duplicateActorPayloadCannotReplaceCurrentContexts(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,drill()));var structure=owner.getContraption();var ctx=actor(owner);var tag=structure.writeNBT(h.getLevel().registryAccess(),false);tag.getListOrEmpty("Actors").add(tag.getListOrEmpty("Actors").getFirst().copy());boolean refused=false;try{structure.readActors(h.getLevel(),tag);}catch(IllegalArgumentException expected){refused=true;}h.assertTrue(refused&&actor(owner)==ctx,"Invalid actor payload is atomic");h.succeed();
    }
    @GameTest public void actorVectorsRejectNonFiniteSaveData(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,drill()));var tag=new CompoundTag();var motion=new ListTag();motion.add(DoubleTag.valueOf(Double.NaN));motion.add(DoubleTag.valueOf(0));motion.add(DoubleTag.valueOf(0));tag.put("Motion",motion);boolean refused=false;try{MovementContext.readNBT(h.getLevel(),owner.getContraption().getBlocks().get(BlockPos.ZERO),tag,owner.getContraption());}catch(IllegalArgumentException expected){refused=true;}h.assertTrue(refused,"Non-finite actor vector rejected");h.succeed();
    }
    @GameTest public void disabledToolTypeSurvivesSourceMappingAndContextReload(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,drill()));owner.getContraption().setActorsActive(new ItemStack(AllBlocks.MECHANICAL_DRILL),false);var restored=Contraption.fromNBT(h.getLevel(),owner.getContraption().writeNBT(h.getLevel().registryAccess(),false),false);h.assertTrue(restored.getActors().getFirst().right.disabled&&restored.isActorTypeDisabled(new ItemStack(AllBlocks.MECHANICAL_DRILL)),"Source disabled actor list");h.succeed();
    }
    @GameTest public void lootFillsRemainingMountedSlotAndDropsOnlyTheExactRemainder(GameTestHelper h)throws Exception{
        h.setBlock(P.east(),Blocks.BARREL);var barrel=h.getBlockEntity(P.east(),BarrelBlockEntity.class);for(int i=0;i<27;i++)barrel.setItem(i,new ItemStack(Items.STONE,64));barrel.setItem(0,new ItemStack(Items.DIAMOND,63));var owner=moving(h,Map.of(P,drill(),P.east(),Blocks.BARREL.defaultBlockState()));var ctx=actor(owner);ctx.position=owner.position().add(.5,.5,.5);MovementBehaviour.REGISTRY.get(ctx.state).collectOrDropItem(ctx,new ItemStack(Items.DIAMOND,5));h.assertValueEqual(count(owner,Items.DIAMOND),64L,"One item inserted");h.assertValueEqual(drops(h,owner).stream().filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum(),4,"Only remainder spawned");h.succeed();
    }
    @GameTest public void actorCanRespectDisabledAutomaticStorageCollection(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,drill(),P.east(),Blocks.BARREL.defaultBlockState()));var ctx=actor(owner);ctx.position=owner.position();boolean previous=ContraptionSettings.moveItemsToStorage;try{ContraptionSettings.moveItemsToStorage=false;MovementBehaviour.REGISTRY.get(ctx.state).collectOrDropItem(ctx,new ItemStack(Items.DIAMOND,3));}finally{ContraptionSettings.moveItemsToStorage=previous;}h.assertValueEqual(count(owner,Items.DIAMOND),0L,"Storage setting respected");h.assertValueEqual(drops(h,owner).stream().filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum(),3,"Full stack dropped");h.succeed();
    }
    @GameTest public void drillRetainsPerpendicularMotionToleranceAndRejectsUnbreakableBlocks(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,drill()));var ctx=actor(owner);var behaviour=(DrillMovementBehaviour)MovementBehaviour.REGISTRY.get(ctx.state);ctx.relativeMotion=new Vec3(1,0,.1);h.assertTrue(behaviour.isActive(ctx),"Source .125 backward tolerance");ctx.relativeMotion=new Vec3(0,0,1);h.assertFalse(behaviour.isActive(ctx),"Backward drilling disabled");h.assertFalse(behaviour.canBreak(h.getLevel(),h.absolutePos(P),Blocks.BEDROCK.defaultBlockState()),"Unbreakable excluded");h.succeed();
    }
    @GameTest(maxTicks=110) public void actualBearingDrillStallsBreaksCollectsAndResumes(GameTestHelper h){
        machine(h,drill(),Blocks.STONE.defaultBlockState());var angle=new AtomicReference<Float>();h.runAfterDelay(12,()->{var owner=bearing(h).getMovedContraption();h.assertTrue(owner!=null&&owner.isStalled(),"Real drill stalls bearing");h.assertTrue(actor(owner).data.getIntOr("Progress",0)>0,"Actual breaking progress");angle.set(owner.getAngle(1));});h.runAfterDelay(18,()->h.assertValueEqual(bearing(h).getMovedContraption().getAngle(1),angle.get(),"Bearing holds exact stalled pose"));h.runAfterDelay(90,()->{var owner=bearing(h).getMovedContraption();h.assertBlockPresent(Blocks.AIR,TARGET);h.assertValueEqual(count(owner,Items.COBBLESTONE),1L,"Actual loot collected once");h.assertFalse(owner.isStalled(),"Bearing resumes");h.succeed();});
    }
    @GameTest(maxTicks=120) public void nativeEntitySaveDuringBreakingResumesWithoutLosingOrDuplicatingLoot(GameTestHelper h){
        machine(h,drill(),Blocks.STONE.defaultBlockState());var saved=new AtomicReference<CompoundTag>();var breaker=new AtomicInteger();h.runAfterDelay(12,()->{var owner=bearing(h).getMovedContraption();h.assertTrue(owner.isStalled(),"Stalled before save");breaker.set(actor(owner).data.getIntOr("BreakerId",0));var output=TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,h.getLevel().registryAccess());owner.saveWithoutId(output);saved.set(output.buildResult());owner.getContraption().getStorage().setOwned(false);owner.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);});h.runAfterDelay(14,()->{var restored=new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION,h.getLevel());restored.load(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,h.getLevel().registryAccess(),saved.get()));h.assertTrue(actor(restored).data.getIntOr("Progress",0)>0,"Saved progress resumes");h.assertTrue(actor(restored).data.getIntOr("BreakerId",0)!=breaker.get(),"Fresh noncolliding break overlay identity");h.assertTrue(h.getLevel().addFreshEntity(restored),"Actual native entity restored");});h.runAfterDelay(100,()->{h.assertBlockPresent(Blocks.AIR,TARGET);h.assertValueEqual(count(bearing(h).getMovedContraption(),Items.COBBLESTONE),1L,"Exactly one collected drop after reload");h.succeed();});
    }
    @GameTest(maxTicks=42) public void disablingMidBreakClearsProgressAndStopsHoldingTheBearing(GameTestHelper h){
        machine(h,drill(),Blocks.STONE.defaultBlockState());h.runAfterDelay(12,()->{var owner=bearing(h).getMovedContraption();h.assertTrue(owner.isStalled(),"Actual stall");owner.getContraption().setActorsActive(new ItemStack(AllBlocks.MECHANICAL_DRILL),false);});h.runAfterDelay(30,()->{var owner=bearing(h).getMovedContraption();h.assertFalse(owner.isStalled(),"Disabled breaker releases controller");h.assertFalse(actor(owner).data.contains("BreakingPos")||actor(owner).data.contains("Progress"),"Progress and overlay work cancelled");h.assertBlockPresent(Blocks.STONE,TARGET);h.succeed();});
    }
    @GameTest public void fallingColumnBreaksFromTheTopAndWaitsBeforeRevisiting(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,drill(),P.east(),Blocks.BARREL.defaultBlockState()));var ctx=actor(owner);var behaviour=(DrillMovementBehaviour)MovementBehaviour.REGISTRY.get(ctx.state);var target=P.north();h.setBlock(target.below(),Blocks.STONE);for(int i=0;i<3;i++)h.setBlock(target.above(i),Blocks.GRAVEL);ctx.position=owner.position();ctx.motion=ctx.relativeMotion=new Vec3(0,0,-.5);behaviour.visitNewPosition(ctx,h.absolutePos(target));for(int i=0;i<20&&h.getBlockState(target.above(2)).is(Blocks.GRAVEL);i++)behaviour.tick(ctx);h.assertBlockPresent(Blocks.AIR,target.above(2));h.assertBlockPresent(Blocks.GRAVEL,target);h.assertTrue(ctx.stall&&ctx.data.contains("WaitingTicks"),"Original falling column delay");h.assertValueEqual(count(owner,Items.GRAVEL)+count(owner,Items.FLINT),1L,"Top block loot only");h.succeed();
    }
    @GameTest public void unloadedBreakingPositionWaitsWithoutLoadingOrDropping(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,drill()));var ctx=actor(owner);var behaviour=(DrillMovementBehaviour)MovementBehaviour.REGISTRY.get(ctx.state);var far=new BlockPos(2000000,40,2000000);h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Fixture absent");ctx.position=owner.position();ctx.motion=ctx.relativeMotion=new Vec3(0,0,-.2);ctx.data.store("BreakingPos",BlockPos.CODEC,far);ctx.stall=true;behaviour.tick(ctx);h.assertTrue(ctx.stall&&ctx.data.contains("BreakingPos"),"Unloaded target retains work");h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Actor creates no chunk load");h.succeed();
    }
    @GameTest(maxTicks=120) public void actualMovingSawFellsConnectedTreeAndCollectsAllLogs(GameTestHelper h){
        machine(h,saw(),Blocks.OAK_LOG.defaultBlockState());for(int i=1;i<4;i++)h.setBlock(TARGET.above(i),Blocks.OAK_LOG);h.runAfterDelay(105,()->{var owner=bearing(h).getMovedContraption();for(int i=0;i<4;i++)h.assertBlockPresent(Blocks.AIR,TARGET.above(i));h.assertValueEqual(count(owner,Items.OAK_LOG),4L,"Moving saw collects tree");h.assertFalse(owner.isStalled(),"Saw releases controller");h.succeed();});
    }
    @GameTest public void sawCannotFellWhileItsActualRotatedFacingPointsVertically(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,saw()));var ctx=actor(owner);ctx.rotation=vector->StructureTransform.rotate(vector,90,Direction.Axis.X);h.setBlock(P.north(),Blocks.OAK_LOG);MovementBehaviour.REGISTRY.get(ctx.state).visitNewPosition(ctx,h.absolutePos(P.north()));h.assertFalse(ctx.stall||ctx.data.contains("BreakingPos"),"World facing, not local facing, restricts saw");h.assertBlockPresent(Blocks.OAK_LOG,P.north());h.succeed();
    }
    @GameTest public void movingToolDamagesAndThrowsMobsButLeavesDroppedItemsIntact(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,drill()));var ctx=actor(owner);var pos=P.north();var pig=h.spawnWithNoFreeWill(EntityTypes.PIG,pos);pig.setNoGravity(true);pig.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);var drop=new ItemEntity(h.getLevel(),pig.getX(),pig.getY(),pig.getZ(),new ItemStack(Items.DIAMOND,2));h.getLevel().addFreshEntity(drop);ctx.motion=ctx.relativeMotion=new Vec3(10,0,0);((DrillMovementBehaviour)MovementBehaviour.REGISTRY.get(ctx.state)).damageEntities(ctx,h.absolutePos(pos),h.getLevel());h.assertTrue(pig.getHealth()<pig.getMaxHealth(),"Original bounded moving-tool damage");h.assertTrue(pig.getDeltaMovement().length()<=4.001&&pig.getDeltaMovement().x>0,"Throw limited to four");h.assertTrue(drop.isAlive()&&drop.getItem().getCount()==2,"Items ignored");h.succeed();
    }
    @GameTest public void reenableOneToolAfterDisablingAllKeepsTheOtherTypeDisabledOnReload(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,drill(),P.east(),saw()));var structure=owner.getContraption();structure.setActorsActive(ItemStack.EMPTY,false);structure.setActorsActive(new ItemStack(AllBlocks.MECHANICAL_DRILL),true);var restored=Contraption.fromNBT(h.getLevel(),structure.writeNBT(h.getLevel().registryAccess(),false),false);for(var pair:restored.getActors())h.assertValueEqual(pair.right.disabled,pair.left.state().is(AllBlocks.MECHANICAL_SAW),"Only remaining disabled type persists");h.succeed();
    }
    @GameTest public void occupiedSeatActorEscapesOntoIntersectedSolidAndBottomSlab(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,AllBlocks.SEATS.get(DyeColor.RED).defaultBlockState()));var ctx=actor(owner);var pig=h.spawnWithNoFreeWill(EntityTypes.PIG,P.above());var behaviour=MovementBehaviour.REGISTRY.get(ctx.state);var target=P.north();
        for(var state:List.of(Blocks.STONE.defaultBlockState(),Blocks.STONE_SLAB.defaultBlockState())){h.setBlock(target,state);h.assertTrue(owner.addSittingPassenger(pig,0),"Actual passenger boards");behaviour.visitNewPosition(ctx,h.absolutePos(target));h.assertFalse(pig.isPassenger(),"Intersected world surface dismounts passenger");double y=h.absolutePos(target).getY()+(state.is(Blocks.STONE)?1.5:1);h.assertTrue(Math.abs(pig.getY()-y)<.0001,"Source escape height");}h.succeed();
    }
    @GameTest public void removalInsideActorTickDefersStopUntilTheCallbackHasFinished(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,ContraptionTestFixtures.ACTOR.defaultBlockState()));var ctx=actor(owner);ctx.data.putBoolean("RemoveWhileTicking",true);owner.tickActors();h.assertTrue(owner.isRemoved(),"Actor removed owner");h.assertValueEqual(ctx.data.getIntOr("Stops",0),1,"Stopped after callback");h.assertFalse(ctx.data.getBooleanOr("StoppedInsideTick",false),"Source skipActorStop guards reentrant cleanup");h.succeed();
    }
    @GameTest public void stoppedActorRunsCleanupOnlyOnceAndClearsTransientMotion(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,ContraptionTestFixtures.ACTOR.defaultBlockState()));var ctx=actor(owner);ctx.position=owner.position();ctx.motion=ctx.relativeMotion=new Vec3(.2,0,0);owner.getContraption().stop(h.getLevel());owner.getContraption().stop(h.getLevel());h.assertValueEqual(ctx.data.getIntOr("Stops",0),1,"No repeated stop hooks");h.assertTrue(ctx.position==null&&ctx.motion.equals(Vec3.ZERO)&&ctx.relativeMotion.equals(Vec3.ZERO),"Transient actor state cleared");h.succeed();
    }
}
