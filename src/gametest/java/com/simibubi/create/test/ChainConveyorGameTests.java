package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.*;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.logistics.box.*;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;

public class ChainConveyorGameTests {
    private static final BlockPos P=new BlockPos(1,3,2),Q=P.east(5);
    private static ChainConveyorBlockEntity place(GameTestHelper h,BlockPos pos) { h.setBlock(pos,AllBlocks.CHAIN_CONVEYOR); return h.getBlockEntity(pos,ChainConveyorBlockEntity.class); }
    private static KineticBlockEntity motor(GameTestHelper h,int speed) { h.setBlock(P.below(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.UP)); var motor=h.getBlockEntity(P.below(),KineticBlockEntity.class); motor.setGeneratedSpeed(speed); return motor; }
    private static ItemStack box() { var box=PackageItem.containing(List.of(new ItemStack(Items.DIAMOND,7))); PackageItem.addAddress(box,"Market"); return box; }
    private static int chains(net.minecraft.world.entity.player.Player player) { int total=0; for(int i=0;i<player.getInventory().getContainerSize();i++) if(player.getInventory().getItem(i).is(Items.IRON_CHAIN))total+=player.getInventory().getItem(i).getCount(); return total; }
    private static void click(GameTestHelper h,BlockPos p,net.minecraft.world.entity.player.Player player) { var pos=h.absolutePos(p); h.getBlockState(p).useItemOn(player.getMainHandItem(),h.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)); }

    @GameTest public void survivalUsesTwoActualBlockClicksAndRefundsOnlyOnce(GameTestHelper h) {
        var first=place(h,P);var second=place(h,Q);var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_CHAIN,6));click(h,P,player);h.assertValueEqual(chains(player),6,"Selecting first endpoint is free");click(h,Q,player);
        h.assertTrue(first.isConnectedTo(second),"Both endpoints committed");h.assertValueEqual(chains(player),4,"Five blocks costs two chains");
        h.assertFalse(first.connectTo(second,player),"Duplicate denied");h.assertValueEqual(chains(player),4,"Duplicate does not spend");
        h.assertTrue(first.disconnect(Q.subtract(P),player),"Existing connection removed");h.assertValueEqual(chains(player),6,"Exact refund");
        h.assertFalse(first.disconnect(Q.subtract(P),player),"Replay denied");h.assertFalse(second.disconnect(P.subtract(Q),player),"Reverse replay denied");h.assertValueEqual(chains(player),6,"No refund duplication");
        h.assertTrue(first.connectTo(second,player),"Reconnected before wrench removal");player.getInventory().setItem(9,player.getMainHandItem());player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.WRENCH));player.setShiftKeyDown(true);
        var pos=h.absolutePos(P);AllItems.WRENCH.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)));
        h.assertTrue(h.getBlockState(P).isAir()&&second.getConnections().isEmpty(),"Actual wrench dismantles both ends");h.assertValueEqual(chains(player),6,"Wrench returns exact chains to inventory once");h.succeed();
    }
    @GameTest public void missingInventoryAndInvalidGeometryLeaveBothEndpointsUnchanged(GameTestHelper h) {
        var first=place(h,P);var second=place(h,Q);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_CHAIN));
        h.assertFalse(first.connectTo(second,player),"Insufficient chains denied");h.assertValueEqual(chains(player),1,"Nothing partially consumed");h.assertTrue(first.getConnections().isEmpty()&&second.getConnections().isEmpty(),"No unilateral edge");
        for(var offset:List.of(BlockPos.ZERO,new BlockPos(2,0,0),new BlockPos(0,3,0),new BlockPos(3,3,0),new BlockPos(32,0,0)))h.assertFalse(ChainConveyorBlockEntity.validConnection(offset),"Invalid distance/slope: "+offset);
        for(var offset:List.of(new BlockPos(3,0,0),new BlockPos(5,3,0),new BlockPos(0,0,31)))h.assertTrue(ChainConveyorBlockEntity.validConnection(offset),"Valid geometry: "+offset);h.succeed();
    }
    @GameTest public void connectionLimitAppliesAtBothEndpoints(GameTestHelper h) {
        var centre=place(h,new BlockPos(3,3,3));
        for(var target:List.of(new BlockPos(0,3,0),new BlockPos(6,3,0),new BlockPos(0,3,6),new BlockPos(6,3,6)))h.assertTrue(centre.connectTo(place(h,target),null),"Four branches allowed");
        var fifth=place(h,new BlockPos(3,3,7));h.assertFalse(fifth.connectTo(centre,null),"Full target rejects incoming fifth branch");h.assertTrue(fifth.getConnections().isEmpty(),"Rejected branch not half-connected");h.succeed();
    }
    @GameTest(maxTicks=30) public void remoteChainTransmitsPositiveRatioAndDisconnectStopsTarget(GameTestHelper h) {
        var first=place(h,P);var second=place(h,Q);first.connectTo(second,null);var motor=motor(h,64);
        h.runAfterDelay(5,()->{h.assertValueEqual(second.getSpeed(),64f,"Remote chain ratio +1");motor.setGeneratedSpeed(-48);});
        h.runAfterDelay(9,()->{h.assertValueEqual(second.getSpeed(),-48f,"Reversal propagates unchanged");first.disconnect(Q.subtract(P),h.makeMockPlayer(GameType.CREATIVE));});
        h.runAfterDelay(13,()->{h.assertValueEqual(first.getSpeed(),-48f,"Driven shaft remains powered");h.assertValueEqual(second.getSpeed(),0f,"Disconnected remote target stops");h.succeed();});
    }
    @GameTest public void parcelCapacityCountsEveryTravellingParcelAndOwnsOneItem(GameTestHelper h) {
        var first=place(h,P);var second=place(h,Q);first.connectTo(second,null);var stack=box();stack.setCount(3);
        for(int i=0;i<20;i++)h.assertTrue(first.addTravellingPackage(new ChainConveyorPackage(.5f,stack),Q.subtract(P)),"Twenty actual parcels accepted");
        h.assertFalse(first.canAcceptMorePackages(),"Capacity counts parcels rather than branch lists");h.assertFalse(first.addLoopingPackage(new ChainConveyorPackage(0,box())),"Twenty-first denied");
        h.assertTrue(first.getTravellingPackages().get(Q.subtract(P)).stream().allMatch(parcel->parcel.item.getCount()==1),"One parcel per hanger");h.assertValueEqual(stack.getCount(),3,"Caller item untouched until its own transfer");h.succeed();
    }
    @GameTest public void duplicateOwnershipAndNonPackagesAreRejected(GameTestHelper h) {
        var first=place(h,P);var second=place(h,Q);first.connectTo(second,null);var parcel=new ChainConveyorPackage(0,box());first.addLoopingPackage(parcel);
        h.assertFalse(first.addLoopingPackage(parcel),"Same owned object rejected");h.assertFalse(first.addTravellingPackage(parcel,Q.subtract(P)),"Same parcel cannot occupy loop and travel");
        h.assertFalse(second.addLoopingPackage(parcel),"Another conveyor cannot claim an owned parcel");
        h.assertFalse(first.addLoopingPackage(new ChainConveyorPackage(0,new ItemStack(Items.APPLE))),"Only parcels can hang");h.assertFalse(first.addLoopingPackage(new ChainConveyorPackage(Float.NaN,box())),"Nonfinite angle rejected");
        var taken=first.takePackage(parcel.netId);h.assertTrue(ItemStack.isSameItemSameComponents(taken,parcel.item),"Exact component contents returned");h.assertTrue(first.takePackage(parcel.netId).isEmpty(),"Pickup replay empty");h.succeed();
    }
    @GameTest(maxTicks=30) public void arrivalTransfersExactlyOnceAndDoesNotMoveAgainInSameTick(GameTestHelper h) {
        var first=place(h,P);var second=place(h,Q);first.connectTo(second,null);motor(h,64);
        h.runAfterDelay(5,()->{
            var stats=first.getConnectionStats(Q.subtract(P));var parcel=new ChainConveyorPackage(stats.chainLength()-.001f,box());first.addTravellingPackage(parcel,Q.subtract(P));
            ChainConveyorBlockEntity.tick(h.getLevel(),first.getBlockPos(),first.getBlockState(),first);
            float arrival=parcel.chainPosition;h.assertValueEqual(first.packageCount(),0,"Source relinquishes ownership");h.assertValueEqual(second.packageCount(),1,"Destination owns one parcel");
            ChainConveyorBlockEntity.tick(h.getLevel(),second.getBlockPos(),second.getBlockState(),second);h.assertValueEqual(parcel.chainPosition,arrival,"Tick-order independent arrival");h.assertValueEqual(PackageItem.getContents(parcel.item).getFirst().getCount(),7,"Seven diamonds intact");h.succeed();
        });
    }
    @GameTest(maxTicks=35) public void reversingInFlightChangesOwnerButNeverDuplicatesParcel(GameTestHelper h) {
        var first=place(h,P);var second=place(h,Q);first.connectTo(second,null);var drive=motor(h,64);var parcel=new ChainConveyorPackage(1,box());
        h.runAfterDelay(5,()->{h.assertTrue(first.addTravellingPackage(parcel,Q.subtract(P)),"Parcel attached to outbound chain");drive.setGeneratedSpeed(-64);});
        h.runAfterDelay(9,()->{h.assertValueEqual(first.packageCount()+second.packageCount(),1,"One owner after reversal");h.assertTrue(second.getTravellingPackages().getOrDefault(P.subtract(Q),List.of()).contains(parcel),"Opposite conveyor owns reversed strand");h.assertTrue(parcel.reversed,"Direction travels with parcel");h.succeed();});
    }
    @GameTest public void nativeSaveRestoresConnectionsCargoDirectionAndIds(GameTestHelper h) {
        var first=place(h,P);var second=place(h,Q);first.connectTo(second,null);first.addLoopingPackage(new ChainConveyorPackage(350,box()));var travelling=new ChainConveyorPackage(1.25f,box());first.addTravellingPackage(travelling,Q.subtract(P));
        var saved=first.saveWithFullMetadata(h.getLevel().registryAccess());h.assertValueEqual(saved.getStringOr("id",""),"create:chain_conveyor","Source registry id");
        var restored=(ChainConveyorBlockEntity)BlockEntity.loadStatic(first.getBlockPos(),first.getBlockState(),saved,h.getLevel().registryAccess());h.assertValueEqual(restored.packageCount(),2,"Both owned parcels restored");h.assertTrue(restored.getConnections().equals(first.getConnections()),"Relative edges preserved");
        var parcel=restored.getTravellingPackages().get(Q.subtract(P)).getFirst();h.assertValueEqual(parcel.chainPosition,1.25f,"Exact chain coordinate");h.assertValueEqual(parcel.identity,travelling.identity,"Persistent parcel identity");h.assertTrue(parcel.netId!=travelling.netId,"Reload allocates a fresh session id to avoid unloaded-save collisions");h.assertTrue(ItemStack.isSameItemSameComponents(parcel.item,travelling.item),"All cargo/address components survive");h.succeed();
    }
    @GameTest public void destroyingConnectionDropsRealParcelOnceAndClearsRemoteEdge(GameTestHelper h) {
        var first=place(h,P);var second=place(h,Q);first.connectTo(second,null);first.addTravellingPackage(new ChainConveyorPackage(1,box()),Q.subtract(P));first.addLoopingPackage(new ChainConveyorPackage(180,box()));
        h.setBlock(P,Blocks.AIR);h.assertTrue(second.getConnections().isEmpty(),"Remote edge removed during actual block removal");
        var entities=h.getLevel().getEntitiesOfClass(PackageEntity.class,new AABB(h.absolutePos(P)).inflate(6));h.assertValueEqual(entities.size(),2,"Exactly two real package entities");
        second.removeInvalidConnections();h.assertValueEqual(h.getLevel().getEntitiesOfClass(PackageEntity.class,new AABB(h.absolutePos(P)).inflate(6)).size(),2,"Validation never repeats drops");h.succeed();
    }
    @GameTest public void sourceRoutingPrefersNamedAddressThenWildcardAndExpiresRoutes(GameTestHelper h) {
        var table=new ChainConveyorRoutingTable();var fallback=new BlockPos(3,0,0);var named=new BlockPos(0,0,3);
        table.receivePortInfo("*",fallback);table.receivePortInfo("Market",named);h.assertValueEqual(table.getExitFor(box()),named,"Named destination beats fallback");
        var neighbour=new ChainConveyorRoutingTable();table.advertiseTo(fallback,neighbour);h.assertValueEqual(neighbour.getExitFor(box()),fallback.multiply(-1),"Neighbour route points back with distance +1");
        for(int i=0;i<100;i++){table.tick();neighbour.tick();}h.assertValueEqual(table.getExitFor(box()),BlockPos.ZERO,"Stale port disappears");h.assertValueEqual(neighbour.getExitFor(box()),BlockPos.ZERO,"Stale advertised destination disappears");h.succeed();
    }
    @GameTest public void thresholdsCrossZeroInBothDirectionsAndGeometryHasSourceDimensions(GameTestHelper h) {
        h.assertTrue(ChainConveyorBlockEntity.loopThresholdCrossed(4,356,0,false),"Forward seam crossing");h.assertTrue(ChainConveyorBlockEntity.loopThresholdCrossed(356,4,0,true),"Reverse seam crossing");
        h.assertFalse(ChainConveyorBlockEntity.loopThresholdCrossed(180,176,0,false),"Antipode is not a crossing");h.assertFalse(ChainConveyorBlockEntity.loopThresholdCrossed(4,4,4,false),"Stationary parcel not crossing");
        var first=place(h,P);var second=place(h,Q);first.connectTo(second,null);var stats=first.getConnectionStats(Q.subtract(P));
        h.assertTrue(Math.abs(stats.tangentAngle()-55)<.001,"East tangent angle 90 minus 35");h.assertTrue(Math.abs(stats.start().y-first.getBlockPos().getY()-6/16d)<.0001,"Hook height 6/16");
        h.assertTrue(Math.abs(first.getPackagePosition(90,null).x-first.getBlockPos().getX()-.5-.875)<.0001,"Loop radius .875");h.succeed();
    }
    @GameTest public void placementRejectsAdjacentConveyorAndOriginalSurvivalRecipeWorks(GameTestHelper h) {
        place(h,P);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.CHAIN_CONVEYOR,2));
        var pos=h.absolutePos(P.east());var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,player.getMainHandItem(),new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
        h.assertTrue(AllBlocks.CHAIN_CONVEYOR.getStateForPlacement(context)==null,"3x3 same-height placement spacing");
        var slots=List.of(ItemStack.EMPTY,new ItemStack(AllBlocks.ANDESITE_CASING),ItemStack.EMPTY,new ItemStack(AllBlocks.ANDESITE_CASING),new ItemStack(AllBlocks.LARGE_COGWHEEL),new ItemStack(AllBlocks.ANDESITE_CASING),ItemStack.EMPTY,new ItemStack(AllBlocks.ANDESITE_CASING),ItemStack.EMPTY);
        var grid=net.minecraft.world.item.crafting.CraftingInput.of(3,3,slots);var recipe=h.getLevel().getServer().getRecipeManager().getRecipes().stream().filter(holder->holder.id().identifier().equals(Create.asResource("crafting/kinetics/chain_conveyor"))).findFirst().orElseThrow();
        var crafting=(net.minecraft.world.item.crafting.CraftingRecipe)recipe.value();h.assertTrue(crafting.matches(grid,h.getLevel()),"Original recipe matches");h.assertValueEqual(crafting.assemble(grid).getCount(),2,"Original output is two conveyors");h.succeed();
    }
    @GameTest public void observerDetectsFilteredLoopCargoWithoutInspectingContentsOrTravellingBranch(GameTestHelper h) {
        var first=place(h,P);var second=place(h,Q);first.connectTo(second,null);var observerPos=P.south();
        h.setBlock(observerPos,AllBlocks.SMART_OBSERVER.defaultBlockState().setValue(com.simibubi.create.content.redstone.DirectedDirectionalBlock.FACING,Direction.NORTH));
        var observer=h.getBlockEntity(observerPos,com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity.class);
        first.addTravellingPackage(new ChainConveyorPackage(1,box()),Q.subtract(P));
        com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity.tick(h.getLevel(),observer.getBlockPos(),observer.getBlockState(),observer);
        h.assertValueEqual(observer.turnOffTicks,0,"Travelling branch does not count as loop cargo");var parcel=new ChainConveyorPackage(0,box());first.addLoopingPackage(parcel);observer.filtering.setFilter(new ItemStack(Items.DIAMOND));
        com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity.tick(h.getLevel(),observer.getBlockPos(),observer.getBlockState(),observer);
        h.assertValueEqual(observer.turnOffTicks,0,"Observer tests parcel item rather than its seven diamonds");observer.filtering.setFilter(parcel.item);
        com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity.tick(h.getLevel(),observer.getBlockPos(),observer.getBlockState(),observer);
        h.assertValueEqual(observer.turnOffTicks,6,"Matching loop parcel activates original six-tick pulse");h.assertValueEqual(first.packageCount(),2,"Detection never consumes cargo");h.succeed();
    }
    private static void aim(net.minecraft.world.entity.player.Player player,Vec3 target) {
        var delta=target.subtract(player.getEyePosition());player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
    }
    @GameTest public void authoritativeAttachmentSpendsOneAndRejectsForgedCoordinatesAndItemType(GameTestHelper h) {
        var conveyor=place(h,P);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atBottomCenterOf(conveyor.getBlockPos()).add(0,-1,3.5));aim(player,conveyor.getPackagePosition(0,null));
        var from=player.getEyePosition();var hit=ChainConveyorInteraction.trace(conveyor,from,from.add(player.getLookAngle().scale(player.blockInteractionRange()+1)));h.assertTrue(hit!=null,"Real loop geometry hit");
        var stack=box();stack.setCount(2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        h.assertFalse(ChainConveyorInteraction.apply(player,new ChainConveyorInteraction.Packet(conveyor.getBlockPos(),BlockPos.ZERO,Float.NaN,ChainConveyorInteraction.ATTACH,0)),"NaN payload rejected");
        h.assertFalse(ChainConveyorInteraction.apply(player,new ChainConveyorInteraction.Packet(conveyor.getBlockPos(),BlockPos.ZERO,hit.position()+45,ChainConveyorInteraction.ATTACH,0)),"Unseen hanger rejected");
        var packet=new ChainConveyorInteraction.Packet(conveyor.getBlockPos(),hit.connection(),hit.position(),ChainConveyorInteraction.ATTACH,0);
        h.assertTrue(ChainConveyorInteraction.apply(player,packet),"Actual selected loop accepts parcel");h.assertValueEqual(stack.getCount(),1,"Exactly one held item spent");h.assertValueEqual(conveyor.packageCount(),1,"One owned parcel");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND,2));h.assertFalse(ChainConveyorInteraction.apply(player,packet),"Client cannot claim arbitrary held item is a package");h.assertValueEqual(conveyor.packageCount(),1,"Invalid request creates no cargo");h.succeed();
    }
    @GameTest public void authoritativePickupRequiresVisibleParcelAndReplayCannotDuplicate(GameTestHelper h) {
        var conveyor=place(h,P);var parcel=new ChainConveyorPackage(0,box());conveyor.addLoopingPackage(parcel);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atBottomCenterOf(conveyor.getBlockPos()).add(0,-2,3.5));
        var packet=new ChainConveyorInteraction.Packet(conveyor.getBlockPos(),BlockPos.ZERO,0,ChainConveyorInteraction.TAKE,parcel.netId);aim(player,player.getEyePosition().add(0,0,3));
        h.assertFalse(ChainConveyorInteraction.apply(player,packet),"Known network id is insufficient when looking away");aim(player,ChainConveyorInteraction.packageBounds(parcel,0).getCenter());
        h.setBlock(P.south(2).below(),Blocks.STONE);h.assertFalse(ChainConveyorInteraction.apply(player,packet),"Solid obstacle prevents pickup through a wall");h.setBlock(P.south(2).below(),Blocks.AIR);
        h.assertTrue(ChainConveyorInteraction.apply(player,packet),"Aimed package picked up");h.assertTrue(ItemStack.isSameItemSameComponents(player.getMainHandItem(),parcel.item),"Exact parcel contents returned");h.assertFalse(ChainConveyorInteraction.apply(player,packet),"Pickup replay denied");h.assertValueEqual(conveyor.packageCount(),0,"Ownership fully removed");h.succeed();
    }
    @GameTest public void closedUnloadedEdgeCannotRefundAgainAndAcknowledgementCleansSavedData(GameTestHelper h) {
        var first=place(h,P);var second=place(h,Q);first.connectTo(second,null);var id=first.getConnectionId(Q.subtract(P));var data=ChainConveyorSavedData.get(h.getLevel());
        h.assertTrue(data.close(id,second.getBlockPos(),false),"First removal owns refund");first.clearContent();
        var player=h.makeMockPlayer(GameType.SURVIVAL);h.assertTrue(second.disconnect(P.subtract(Q),player),"Stale endpoint can be cleaned even while its former owner is unavailable");
        h.assertValueEqual(chains(player),0,"Previously settled edge never refunds twice");h.assertFalse(data.isClosed(id),"Returning endpoint acknowledges and releases tombstone");h.succeed();
    }
    @GameTest public void closedEdgeRecordPersistsAndDoesNotAffectReconnectedGeneration(GameTestHelper h) {
        var first=place(h,P);var second=place(h,Q);first.connectTo(second,null);var oldId=first.getConnectionId(Q.subtract(P));var data=new ChainConveyorSavedData();
        data.close(oldId,second.getBlockPos(),false);var encoded=ChainConveyorSavedData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,data).getOrThrow();
        var restored=ChainConveyorSavedData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,encoded).getOrThrow();h.assertTrue(restored.isClosed(oldId),"Pending unloaded-end removal survives native SavedData codec");
        first.disconnect(Q.subtract(P),h.makeMockPlayer(GameType.CREATIVE));first.connectTo(second,null);var newId=first.getConnectionId(Q.subtract(P));h.assertFalse(oldId.equals(newId)||restored.isClosed(newId),"Reconnecting same block coordinates gets an independent generation");
        restored.acknowledge(oldId,second.getBlockPos());h.assertValueEqual(restored.pendingCount(),0,"Acknowledged removal no longer grows persisted state");h.succeed();
    }
}
