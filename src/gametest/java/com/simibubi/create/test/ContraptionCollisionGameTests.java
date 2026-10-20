package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.actors.seat.*;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

public class ContraptionCollisionGameTests {
    private static final BlockPos P=new BlockPos(3,3,3),T=P.south(3);
    private static ControlledContraptionEntity moving(GameTestHelper h,Map<BlockPos,BlockState> blocks,BlockPos anchor)throws Exception{
        blocks.forEach(h::setBlock);var min=blocks.keySet().stream().reduce((a,b)->new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()))).orElseThrow();var max=blocks.keySet().stream().reduce((a,b)->new BlockPos(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()))).orElseThrow();if(blocks.size()>1)h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(min),h.absolutePos(max))));
        var structure=new BearingContraption(false,Direction.UP);h.assertTrue(structure.assemble(h.getLevel(),h.absolutePos(anchor.below())),"Structure assembled");h.assertTrue(structure.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO),"World ownership taken");var entity=new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION,h.getLevel());entity.setContraption(structure);entity.setPos(Vec3.atLowerCornerOf(h.absolutePos(anchor)));entity.setRotationAxis(Direction.Axis.Y);h.assertTrue(h.getLevel().addFreshEntity(entity),"Real entity added");return entity;
    }
    private static ControlledContraptionEntity platform(GameTestHelper h)throws Exception{return moving(h,Map.of(P,Blocks.STONE.defaultBlockState()),P);}
    private static BlockState seat(){return AllBlocks.SEATS.get(DyeColor.RED).defaultBlockState();}
    private static void checked(Checked body){try{body.run();}catch(RuntimeException failure){throw failure;}catch(Exception failure){throw new RuntimeException(failure);}}
    @FunctionalInterface private interface Checked{void run()throws Exception;}
    private static void near(GameTestHelper h,double actual,double expected,double tolerance,String message){h.assertTrue(Math.abs(actual-expected)<=tolerance,message+" actual="+actual+" expected="+expected);}

    @GameTest(maxTicks=45) public void actualPigFallsOntoRemovedWorldBlockAndStaysGrounded(GameTestHelper h)throws Exception{
        var owner=platform(h);var pig=h.spawnWithNoFreeWill(EntityTypes.PIG,P.above(3));pig.setNoGravity(false);h.runAfterDelay(30,()->{near(h,pig.getY(),h.absolutePos(P).getY()+1,.02,"Real animal rests on moving geometry");h.assertTrue(pig.onGround(),"Surface sets actual ground state");h.assertTrue(owner.collidingEntities.containsKey(pig),"Actual support tracked");h.assertTrue(pig.fallDistance<.1,"Fall distance reset");h.assertBlockPresent(Blocks.AIR,P);h.succeed();});
    }
    @GameTest public void floorContactAtExactlyTheEndOfNativeMotionRetainsSupport(GameTestHelper h)throws Exception{
        var owner=platform(h);var pig=h.spawnWithNoFreeWill(EntityTypes.PIG,P.above());pig.setPos(owner.position().add(.5,1.0001,.5));pig.setDeltaMovement(0,-.0001,0);com.simibubi.create.content.contraptions.ContraptionCollider.collideEntities(owner);h.assertTrue(owner.collidingEntities.containsKey(pig),"Exact end-of-sweep contact must retain floor support");h.assertTrue(pig.onGround(),"Contact retains native on-ground state");h.succeed();
    }
    @GameTest(maxTicks=45) public void bottomSlabUsesHalfHeightRatherThanFullCube(GameTestHelper h)throws Exception{
        moving(h,Map.of(P,Blocks.STONE_SLAB.defaultBlockState()),P);var pig=h.spawnWithNoFreeWill(EntityTypes.PIG,P.above(3));pig.setNoGravity(false);h.runAfterDelay(30,()->{near(h,pig.getY(),h.absolutePos(P).getY()+.5,.02,"Slab shape retains half height");h.succeed();});
    }
    @GameTest(maxTicks=60) public void nativeStairRetainsBothItsLowAndHighSurfaces(GameTestHelper h)throws Exception{
        moving(h,Map.of(P,Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.NORTH)),P);var low=h.spawnWithNoFreeWill(EntityTypes.CHICKEN,P.above(2));var high=h.spawnWithNoFreeWill(EntityTypes.CHICKEN,P.above(2));low.setNoGravity(false);high.setNoGravity(false);var origin=h.absolutePos(P);low.setPos(origin.getX()+.5,origin.getY()+2,origin.getZ()+.82);high.setPos(origin.getX()+.5,origin.getY()+2,origin.getZ()+.18);h.runAfterDelay(45,()->{near(h,low.getY(),origin.getY()+.5,.025,"Low stair tread");near(h,high.getY(),origin.getY()+1,.025,"High stair tread");h.succeed();});
    }
    @GameTest(maxTicks=45) public void hollowFrameDoesNotBecomeItsSolidBroadPhaseBounds(GameTestHelper h)throws Exception{
        var blocks=new LinkedHashMap<BlockPos,BlockState>();for(var pos:List.of(P.south(),P.west().south(),P.east().south(),P.west(),P.east()))blocks.put(pos,Blocks.STONE.defaultBlockState());var owner=moving(h,blocks,P.south());var pig=h.spawnWithNoFreeWill(EntityTypes.PIG,P.above(3));pig.setNoGravity(false);h.runAfterDelay(30,()->{h.assertTrue(pig.getY()<h.absolutePos(P).getY()-.5,"Animal falls through real gap inside owner bounds");h.assertFalse(owner.collidingEntities.containsKey(pig),"No fictitious surface in gap");h.succeed();});
    }
    @GameTest(maxTicks=35) public void collisionWithVerticalPanelStopsSidewaysMotion(GameTestHelper h)throws Exception{
        moving(h,Map.of(P,Blocks.STONE.defaultBlockState(),P.above(),Blocks.STONE.defaultBlockState()),P);var pig=h.spawnWithNoFreeWill(EntityTypes.PIG,P.west(2));pig.setNoGravity(false);pig.setNoGravity(true);pig.setDeltaMovement(.35,0,0);h.runAfterDelay(22,()->{h.assertTrue(pig.getBoundingBox().maxX<=h.absolutePos(P).getX()+.02,"Animal cannot pass through panel");h.assertTrue(pig.getX()>h.absolutePos(P.west(2)).getX()+.7,"Animal actually approached panel");h.succeed();});
    }
    @GameTest(maxTicks=25) public void hittingUndersideDoesNotPretendToStandOnTheCeiling(GameTestHelper h)throws Exception{
        var owner=platform(h);var pig=h.spawnWithNoFreeWill(EntityTypes.PIG,P.below());pig.setNoGravity(false);pig.setNoGravity(true);pig.setDeltaMovement(0,.25,0);h.runAfterDelay(8,()->{h.assertTrue(pig.getBoundingBox().maxY<=h.absolutePos(P).getY()+.02,"Ceiling separates downwards");h.assertFalse(pig.onGround(),"Ceiling is not a supporting floor");h.assertFalse(owner.collidingEntities.containsKey(pig),"No ceiling ground privilege");h.succeed();});
    }
    @GameTest(maxTicks=45) public void standingAnimalReceivesOriginalRotationContactMotion(GameTestHelper h)throws Exception{
        var blocks=new LinkedHashMap<BlockPos,BlockState>();for(var pos:List.of(P,P.east(),P.south(),P.east().south()))blocks.put(pos,Blocks.STONE.defaultBlockState());var owner=moving(h,blocks,P);var pig=h.spawnWithNoFreeWill(EntityTypes.PIG,P.east().above(3));pig.setNoGravity(false);h.runAfterDelay(20,()->{h.assertTrue(owner.collidingEntities.containsKey(pig),"Grounded before rotation");owner.setAngle(90);});h.runAfterDelay(24,()->{near(h,pig.getX(),h.absolutePos(P).getX()+.5,.08,"Quarter turn carries x");near(h,pig.getZ(),h.absolutePos(P).getZ()-.5,.08,"Quarter turn carries z");near(h,pig.getY(),h.absolutePos(P).getY()+1,.03,"Carrier retains floor height");h.succeed();});
    }
    @GameTest public void supportPointMotionIncludesAnchorTranslationWithoutDoubleCounting(GameTestHelper h)throws Exception{
        var owner=platform(h);owner.finishCollisionTick();var point=owner.position().add(1.5,1,.5);owner.setPos(owner.position().add(.25,.5,-.75));var motion=owner.getContactPointMotion(point);near(h,motion.x,.25,.000001,"Anchor x");near(h,motion.y,.5,.000001,"Anchor y");near(h,motion.z,-.75,.000001,"Anchor z");h.succeed();
    }
    @GameTest public void spectatorProjectileAndVehiclePassengersAreExcluded(GameTestHelper h)throws Exception{
        var owner=platform(h);var spectator=h.makeMockPlayer(GameType.SPECTATOR);GameType.SPECTATOR.updatePlayerAbilities(spectator.getAbilities());h.assertFalse(owner.canCollideWith(spectator),"Spectator excluded");var arrow=new net.minecraft.world.entity.projectile.arrow.Arrow(EntityTypes.ARROW,h.getLevel());h.assertFalse(owner.canCollideWith(arrow),"Projectile excluded");h.assertFalse(owner.canCollideWith(new SeatEntity(h.getLevel())),"Seat proxy excluded");h.assertFalse(owner.canCollideWith(owner),"Owner excluded");h.succeed();
    }
    @GameTest(maxTicks=25) public void occupiedStationarySeatHandsItsAnimalToTheNewMovingOwner(GameTestHelper h)throws Exception{
        h.setBlock(P,seat());var pig=h.spawnWithNoFreeWill(EntityTypes.PIG,P.above());pig.setNoGravity(false);SeatBlock.sitDown(h.getLevel(),h.absolutePos(P),pig);h.assertTrue(pig.getVehicle() instanceof SeatEntity,"Initially on real world seat");var owner=moving(h,Map.of(P,seat()),P);h.runAfterDelay(3,()->{h.assertTrue(pig.getVehicle()==owner,"Assembly preserves actual passenger");h.assertValueEqual(owner.getContraption().getSeatOf(pig.getUUID()),BlockPos.ZERO,"Source UUID to local seat mapping");near(h,pig.position().distanceTo(owner.getPassengerPosition(pig,1).add(0,SeatEntity.getCustomEntitySeatOffset(pig)-.125,0)),0,.0001,"Native attachment follows source seat formula");h.succeed();});
    }
    @GameTest public void sourcePassengerMappingSavesUuidAndRejectsDuplicateSeatOwners(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,seat()),P);var pig=h.spawnWithNoFreeWill(EntityTypes.PIG,P.above());pig.setNoGravity(false);h.assertTrue(owner.addSittingPassenger(pig,0),"Passenger boards");var data=owner.getContraption().writeNBT(h.getLevel().registryAccess(),false);var decoded=Contraption.fromNBT(h.getLevel(),data,false);h.assertValueEqual(decoded.getSeatOf(pig.getUUID()),BlockPos.ZERO,"Passenger UUID survives source format");var mapping=(CompoundTag)data.getListOrEmpty("Passengers").getFirst();var duplicate=mapping.copy();duplicate.store("Id",UUIDUtil.CODEC,UUID.randomUUID());data.getListOrEmpty("Passengers").add(duplicate);boolean refused=false;try{Contraption.fromNBT(h.getLevel(),data,false);}catch(IllegalArgumentException expected){refused=true;}h.assertTrue(refused,"Two UUIDs cannot own one seat");h.succeed();
    }
    @GameTest public void actualTwoSeatsHaveIndependentSlotsAndPlayerCanReplaceOnlyAnAnimal(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,seat(),P.east(),seat()),P);var first=h.spawnWithNoFreeWill(EntityTypes.PIG,P.above());var second=h.spawnWithNoFreeWill(EntityTypes.PIG,P.east().above());first.setNoGravity(false);second.setNoGravity(false);h.assertTrue(owner.addSittingPassenger(first,0),"First seat");h.assertTrue(owner.addSittingPassenger(second,1),"Second seat");var third=h.spawnWithNoFreeWill(EntityTypes.PIG,P.above());h.assertFalse(owner.addSittingPassenger(third,0),"Animal cannot replace animal");var player=h.makeMockPlayer(GameType.SURVIVAL);h.assertTrue(owner.addSittingPassenger(player,0),"Player may replace animal");h.assertTrue(first.getVehicle()==null&&second.getVehicle()==owner,"Other slot remains independent");h.assertFalse(owner.addSittingPassenger(h.makeMockPlayer(GameType.SURVIVAL),0),"Player cannot replace player");h.succeed();
    }
    @GameTest public void currentPassengerCanMoveToAnEmptySeatAndClearStaleSavedOwnership(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,seat(),P.east(),seat()),P);var pig=h.spawnWithNoFreeWill(EntityTypes.PIG,P.above());h.assertTrue(owner.addSittingPassenger(pig,0),"First slot boards");var stale=UUID.randomUUID();owner.getContraption().getSeatMapping().put(stale,1);h.assertTrue(owner.addSittingPassenger(pig,1),"Existing rider can change slots without remounting");h.assertTrue(pig.getVehicle()==owner&&owner.getContraption().getSeatMapping().size()==1&&!owner.getContraption().getSeatMapping().containsKey(stale),"Only current real slot owner survives");h.assertValueEqual(owner.getContraption().getSeatOf(pig.getUUID()),owner.getContraption().getSeats().get(1),"New slot selected");Contraption.fromNBT(h.getLevel(),owner.getContraption().writeNBT(h.getLevel().registryAccess(),false),false);h.succeed();
    }
    @GameTest(maxTicks=25) public void disassemblyTransfersPassengerBackToTheRestoredWorldSeat(GameTestHelper h)throws Exception{
        var owner=moving(h,Map.of(P,Blocks.STONE.defaultBlockState(),P.east(),seat()),P);var pig=h.spawnWithNoFreeWill(EntityTypes.PIG,P.east().above());pig.setNoGravity(false);int index=owner.getContraption().getSeats().indexOf(new BlockPos(1,0,0));h.assertTrue(owner.addSittingPassenger(pig,index),"Board actual moving seat");owner.setAngle(90);h.assertTrue(owner.disassemble(),"Structure returns");h.runAfterDelay(3,()->{h.assertTrue(pig.getVehicle() instanceof SeatEntity,"Real native world vehicle restored");h.assertValueEqual(pig.getVehicle().blockPosition(),h.absolutePos(P.north()),"Seat follows quarter turn");h.assertTrue(pig.isAlive(),"Passenger not destroyed with owner");h.succeed();});
    }
    @GameTest public void currentSeatOccupantIsRefreshedBeforeRemoval(GameTestHelper h)throws Exception{
        h.setBlock(P,seat());var first=h.spawnWithNoFreeWill(EntityTypes.PIG,P.above());var second=h.spawnWithNoFreeWill(EntityTypes.PIG,P.above());first.setNoGravity(false);second.setNoGravity(false);SeatBlock.sitDown(h.getLevel(),h.absolutePos(P),first);var structure=new BearingContraption(false,Direction.UP);structure.assemble(h.getLevel(),h.absolutePos(P.below()));var worldSeat=(SeatEntity)first.getVehicle();first.stopRiding();second.startRiding(worldSeat,true,true);structure.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO);h.assertTrue(structure.getInitialPassengers().containsValue(second)&&!structure.getInitialPassengers().containsValue(first),"Latest occupant transferred instead of stale search snapshot");h.succeed();
    }
    @GameTest public void unsupportedOrForgedMotionCannotResetPlayerGroundState(GameTestHelper h)throws Exception{
        var owner=platform(h);var player=(net.minecraft.server.level.ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);player.setPos(owner.position().add(.5,1,.5));var packet=new com.simibubi.create.content.contraptions.sync.ClientMotionPacket(owner.getId(),owner.getUUID(),Vec3.ZERO,true,0);h.assertFalse(packet.apply(player),"No authority without actual server support contact");owner.registerColliding(player);h.assertFalse(new com.simibubi.create.content.contraptions.sync.ClientMotionPacket(owner.getId(),UUID.randomUUID(),Vec3.ZERO,true,0).apply(player),"Identity checked");h.assertFalse(new com.simibubi.create.content.contraptions.sync.ClientMotionPacket(owner.getId(),owner.getUUID(),new Vec3(Double.NaN,0,0),true,0).apply(player),"Invalid motion rejected");for(int i=0;i<4;i++)owner.beginCollisionTick();h.assertFalse(packet.apply(player),"Expired support rejected");h.succeed();
    }
}
