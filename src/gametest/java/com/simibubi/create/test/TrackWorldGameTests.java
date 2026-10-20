package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Proposed native track dispatch, exact inventory/refund, persistence and honest unloaded discovery. */
public final class TrackWorldGameTests {
    private static final String STRUCTURE="create_test:reservoir";
    private static final BlockPos P=new BlockPos(4,6,4),Q=P.offset(8,0,8);
    private static BlockState rail(TrackShape shape){return AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,shape);}
    private static Player player(GameTestHelper h,int count){var p=h.makeMockPlayer(GameType.SURVIVAL);p.setPos(Vec3.atBottomCenterOf(h.absolutePos(P)).add(0,1,2));var stack=new ItemStack(AllBlocks.TRACK,count);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Track construction stock"));p.setItemInHand(InteractionHand.MAIN_HAND,stack);return p;}
    private static void place(GameTestHelper h,BlockPos pos,TrackShape shape){h.setBlock(pos.below(),Blocks.STONE);h.setBlock(pos,rail(shape));}
    private static InteractionResult click(GameTestHelper h,Player player,BlockPos at,float yaw){player.setYRot(yaw);player.setXRot(0);var pos=h.absolutePos(at);return player.getMainHandItem().getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atBottomCenterOf(pos).add(0,.125,0),Direction.UP,pos,false)));}
    private static Player curve(GameTestHelper h){place(h,P,TrackShape.XO);place(h,Q,TrackShape.ZO);var p=player(h,64);h.assertTrue(click(h,p,P,-90).consumesAction(),"Actual item selects first real rail");h.assertTrue(p.getMainHandItem().has(AllDataComponents.TRACK_CONNECTING_FROM),"Selection is a native component");h.assertTrue(click(h,p,Q,180).consumesAction(),"Actual second native item use commits source turn");return p;}
    private static BezierConnection connection(GameTestHelper h){return h.getBlockEntity(P,TrackBlockEntity.class).getConnections().get(h.absolutePos(Q));}
    private static int newlyDropped(GameTestHelper h,Set<UUID> before){return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(Vec3.atLowerCornerOf(h.absolutePos(P)),Vec3.atLowerCornerOf(h.absolutePos(Q).offset(2,3,2)))).stream().filter(e->!before.contains(e.getUUID())&&e.getItem().is(AllBlocks.TRACK.asItem())).mapToInt(e->e.getItem().getCount()).sum();}
    private static Set<UUID> droppedIds(GameTestHelper h){var ids=new HashSet<UUID>();h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(Vec3.atLowerCornerOf(h.absolutePos(P)),Vec3.atLowerCornerOf(h.absolutePos(Q).offset(2,3,2)))).forEach(e->ids.add(e.getUUID()));return ids;}

    @GameTest(structure=STRUCTURE)public void actualStraightSelectionBuildsEveryCellAndSpendsOnlyNewPhysicalTracks(GameTestHelper h){
        place(h,P,TrackShape.XO);place(h,P.east(7),TrackShape.XO);var p=player(h,64);click(h,p,P,-90);click(h,p,P.east(7),-90);
        for(int i=0;i<=7;i++)h.assertTrue(h.getBlockState(P.east(i)).is(AllBlocks.TRACK)&&!h.getBlockState(P.east(i)).getValue(TrackBlock.HAS_BE),"Complete native straight rail "+i);
        h.assertTrue(p.getMainHandItem().getCount()==58&&p.getMainHandItem().has(DataComponents.CUSTOM_NAME)&&!p.getMainHandItem().has(AllDataComponents.TRACK_CONNECTING_FROM),"Exactly six new tracks; original item components remain");h.succeed();
    }
    @GameTest(structure=STRUCTURE)public void actualTurnStoresSourceReciprocalGeometryAndSpendsExactCurveCost(GameTestHelper h){
        var p=curve(h);var a=connection(h);var b=h.getBlockEntity(Q,TrackBlockEntity.class).getConnections().get(h.absolutePos(P));
        h.assertTrue(a!=null&&b!=null&&a.primary&&!b.primary&&a.equalsSansMaterial(b)&&a.getMaterial()==TrackMaterial.ANDESITE,"One physical curve in both real endpoint BEs");
        h.assertTrue(a.getPosition(0).distanceTo(a.starts.getFirst())<1e-6&&a.getPosition(1).distanceTo(a.starts.getSecond())<1e-6&&a.getLength()>10,"Source Bezier endpoints and arc length");
        h.assertValueEqual(p.getMainHandItem().getCount(),64-a.getTrackItemCost(),"Exact source track item cost");h.succeed();
    }
    @GameTest(structure=STRUCTURE)public void insufficientTurnInventoryLeavesBothWorldEndpointsAndSelectionUntouched(GameTestHelper h){
        place(h,P,TrackShape.XO);place(h,Q,TrackShape.ZO);var p=player(h,1);click(h,p,P,-90);var original=p.getMainHandItem().get(AllDataComponents.TRACK_CONNECTING_FROM);h.assertTrue(click(h,p,Q,180)==InteractionResult.FAIL,"Actual item refuses insufficient stock");
        h.assertTrue(!h.getBlockState(P).getValue(TrackBlock.HAS_BE)&&!h.getBlockState(Q).getValue(TrackBlock.HAS_BE)&&h.getLevel().getBlockEntity(h.absolutePos(P))==null&&h.getLevel().getBlockEntity(h.absolutePos(Q))==null,"No partial conditional BE/world mutation");h.assertTrue(p.getMainHandItem().getCount()==1&&original.equals(p.getMainHandItem().get(AllDataComponents.TRACK_CONNECTING_FROM)),"No debit or lost selection");h.succeed();
    }
    @GameTest(structure=STRUCTURE)public void obstructedStraightRejectsBeforeTouchingAnyTrackOrNamedWorldCargo(GameTestHelper h){
        place(h,P,TrackShape.XO);place(h,P.east(7),TrackShape.XO);h.setBlock(P.east(3),Blocks.BARREL);var cargo=new ItemStack(Items.DIAMOND,7);cargo.set(DataComponents.CUSTOM_NAME,Component.literal("Track preflight cargo"));var barrel=h.getBlockEntity(P.east(3),BarrelBlockEntity.class);barrel.setItem(0,cargo);var p=player(h,64);click(h,p,P,-90);
        h.assertTrue(click(h,p,P.east(7),-90)==InteractionResult.FAIL,"Actual world obstruction denies complete operation");h.assertBlockPresent(Blocks.AIR,P.east());h.assertTrue(h.getBlockEntity(P.east(3),BarrelBlockEntity.class)==barrel&&ItemStack.isSameItemSameComponents(barrel.getItem(0),cargo)&&barrel.getItem(0).getCount()==7&&p.getMainHandItem().getCount()==64,"Exact original barrel identity/cargo and construction stock remain");h.succeed();
    }
    @GameTest(structure=STRUCTURE)public void repeatingSameTurnDoesNotReplaceMaterialOrDebitAgain(GameTestHelper h){var p=curve(h);var before=connection(h);int stock=p.getMainHandItem().getCount();click(h,p,P,-90);click(h,p,Q,180);h.assertTrue(connection(h)==before&&p.getMainHandItem().getCount()==stock,"Existing physical curve keeps object/material and has zero additional cost");h.succeed();}
    @GameTest(structure=STRUCTURE)public void nativeBlockEntitySaveReadPreservesRelativeCurveFieldsAndBinding(GameTestHelper h){
        curve(h);var original=h.getBlockEntity(P,TrackBlockEntity.class);var tag=original.saveWithFullMetadata(h.getLevel().registryAccess());var restored=BlockEntity.loadStatic(original.getBlockPos(),original.getBlockState(),tag,h.getLevel().registryAccess());h.assertTrue(restored instanceof TrackBlockEntity,"Native registry creates exact TrackBlockEntity");var curve=((TrackBlockEntity)restored).getConnections().get(h.absolutePos(Q));h.assertTrue(curve!=null&&curve.equalsSansMaterial(connection(h))&&curve.primary&&curve.getMaterial()==TrackMaterial.ANDESITE,"Connections source keys decode at real BE local origin");h.succeed();
    }
    @GameTest(structure=STRUCTURE)public void removingPrimaryEndpointRefundsOneCurveAndClearsReciprocalConditionalBe(GameTestHelper h){curve(h);int cost=connection(h).getTrackItemCost();var before=droppedIds(h);h.getLevel().destroyBlock(h.absolutePos(P),false);h.assertTrue(!h.getBlockState(Q).getValue(TrackBlock.HAS_BE)&&h.getLevel().getBlockEntity(h.absolutePos(Q))==null,"Real native destruction clears remote turn and removes conditional BE");h.assertValueEqual(newlyDropped(h,before),cost,"Exactly one physical curve refund");h.getLevel().destroyBlock(h.absolutePos(Q),false);h.assertValueEqual(newlyDropped(h,before),cost,"Remote endpoint removal never refunds the same curve again");h.succeed();}
    @GameTest(structure=STRUCTURE)public void removingSecondaryEndpointRefundsSameCurveExactlyOnce(GameTestHelper h){curve(h);int cost=connection(h).getTrackItemCost();var before=droppedIds(h);h.getLevel().destroyBlock(h.absolutePos(Q),false);h.assertTrue(!h.getBlockState(P).getValue(TrackBlock.HAS_BE),"Actual reverse endpoint destruction removes primary");h.assertValueEqual(newlyDropped(h,before),cost,"Source cost independent of endpoint broken");h.succeed();}
    @GameTest(structure=STRUCTURE)public void creativeNativeBreakDoesNotRefundSurvivalCurveItems(GameTestHelper h){curve(h);var before=droppedIds(h);var pos=h.absolutePos(P);var state=h.getBlockState(P);state.getBlock().playerWillDestroy(h.getLevel(),pos,state,h.makeMockPlayer(GameType.CREATIVE));h.getLevel().destroyBlock(pos,false);h.assertValueEqual(newlyDropped(h,before),0,"Native creative destruction respects curve cancelDrops");h.succeed();}
    @GameTest(structure=STRUCTURE)public void loadedMissingReciprocalIsRepairedWithExactSourceSecondary(GameTestHelper h){curve(h);var first=h.getBlockEntity(P,TrackBlockEntity.class);h.getLevel().removeBlockEntity(h.absolutePos(Q));h.getLevel().setBlock(h.absolutePos(Q),rail(TrackShape.ZO),Block.UPDATE_ALL);first.validateConnections();var other=h.getBlockEntity(Q,TrackBlockEntity.class);h.assertTrue(other.getConnections().get(h.absolutePos(P)).equalsSansMaterial(connection(h))&&!other.getConnections().get(h.absolutePos(P)).primary,"Loaded endpoint repair recreates exact source reciprocal");h.succeed();}
    @GameTest(structure=STRUCTURE)public void cleanupReceiptCannotDeleteAnotherCurveAtSameEndpointPair(GameTestHelper h){curve(h);var current=connection(h);var wrong=new BezierConnection(current.bePositions.swap(),current.starts.swap().map(v->v.add(.125,0,0)),current.axes.swap(),current.normals.swap(),false,false,current.getMaterial());TrackConnectionCleanup.removeOrQueue(h.getLevel(),wrong);h.assertTrue(h.getBlockEntity(Q,TrackBlockEntity.class).getConnections().containsKey(h.absolutePos(P))&&TrackConnectionCleanup.get(h.getLevel()).pendingCount()==0,"Actual loaded cleanup matches full curve geometry before mutation");h.succeed();}
    @GameTest(structure=STRUCTURE)public void dimensionMismatchSelectionCannotConnectLocalTrackOrSpendItems(GameTestHelper h){place(h,P,TrackShape.XO);place(h,Q,TrackShape.ZO);var p=player(h,64);click(h,p,P,-90);var selection=p.getMainHandItem().get(AllDataComponents.TRACK_CONNECTING_FROM);p.getMainHandItem().set(AllDataComponents.TRACK_CONNECTING_FROM,new TrackPlacement.ConnectingFrom(selection.pos(),selection.axis(),selection.normal(),selection.end(),Level.NETHER));h.assertTrue(click(h,p,Q,180)==InteractionResult.FAIL&&p.getMainHandItem().getCount()==64&&!h.getBlockState(P).getValue(TrackBlock.HAS_BE),"Native cross-dimension stale selection is rejected before world mutation");h.succeed();}
    @GameTest(structure=STRUCTURE)public void sourceCurveMetadataRoundtripRetainsGirderAndSmoothingWithoutConstructingMissingWorldFeature(GameTestHelper h){curve(h);var current=connection(h);var saved=new BezierConnection(current.bePositions,current.starts,current.axes,current.normals,true,true,current.getMaterial());saved.smoothing=net.createmod.catnip.data.Couple.create(-3,4);var decoded=BezierConnection.CODEC.parse(NbtOps.INSTANCE,BezierConnection.CODEC.encodeStart(NbtOps.INSTANCE,saved).getOrThrow()).getOrThrow();h.assertTrue(decoded.hasGirder&&decoded.smoothing.getFirst()==-3&&decoded.smoothing.getSecond()==4&&decoded.getMaterial()==saved.getMaterial(),"Source unsupported feature metadata remains intact in actual codec");h.succeed();}
    @GameTest(structure=STRUCTURE)public void sourceStraightDiscoveryUsesExactHalfBlockPositionsAndDimensionIdentity(GameTestHelper h){place(h,P,TrackShape.XO);var pos=h.absolutePos(P);var locations=AllBlocks.TRACK.getConnected(h.getLevel(),pos,h.getBlockState(P),false,null);h.assertValueEqual(locations.size(),2,"Actual track exposes two source ends");for(var location:locations)h.assertTrue(location.dimension.equals(h.getLevel().dimension())&&location.getLocation().y==pos.getY()&&location.getLocation().z==pos.getZ()+.5,"Exact source half-grid coordinates and real dimension");h.succeed();}
    @GameTest(structure=STRUCTURE)public void actualWaterloggedBlockItemPlacementPreservesNativeFluidAndInventory(GameTestHelper h){h.setBlock(P,Blocks.WATER);h.setBlock(P.below(),Blocks.STONE);var p=player(h,3);p.setYRot(-90);var pos=h.absolutePos(P);var ctx=new BlockPlaceContext(p,InteractionHand.MAIN_HAND,p.getMainHandItem(),new BlockHitResult(Vec3.atBottomCenterOf(pos),Direction.UP,pos,false));h.assertTrue(((BlockItem)p.getMainHandItem().getItem()).place(ctx).consumesAction(),"Native block item places a real rail");h.assertTrue(h.getBlockState(P).getValue(TrackBlock.WATERLOGGED)&&h.getBlockState(P).getFluidState().getType()==net.minecraft.world.level.material.Fluids.WATER&&p.getMainHandItem().getCount()==2,"Native source waterlogging spends one item");h.succeed();}
    @GameTest(structure=STRUCTURE)public void realMissingNativeTerrainThrowsFrontierWithoutForcingAChunk(GameTestHelper h){var far=new BlockPos(25000000,64,25000000);h.assertTrue(!LoadedLevelAccess.isLoaded(h.getLevel(),far),"Actual native far chunk starts absent");boolean deferred=false;try{ITrackBlock.walkConnectedTracks(h.getLevel(),new TrackNodeLocation(Vec3.atCenterOf(far)).in(h.getLevel()),false);}catch(UnloadedTrackException frontier){deferred=frontier.dimension().equals(h.getLevel().dimension())&&!frontier.positions().isEmpty();}h.assertTrue(deferred&&!LoadedLevelAccess.isLoaded(h.getLevel(),far),"Missing terrain is an explicit frontier and remains unloaded");h.succeed();}
    @GameTest(structure=STRUCTURE)public void invisibleNoneTrackHasNoGraphDiscoveryOrNativeItemSelection(GameTestHelper h){
        place(h,P,TrackShape.NONE);var p=player(h,64);h.assertValueEqual(AllBlocks.TRACK.getConnected(h.getLevel(),h.absolutePos(P),h.getBlockState(P),false,null).size(),0,"NONE exposes no physical source direction");
        h.assertTrue(click(h,p,P,-90)==InteractionResult.FAIL&&!p.getMainHandItem().has(AllDataComponents.TRACK_CONNECTING_FROM)&&p.getMainHandItem().getCount()==64,"Native zero-axis block cannot create a curve selection");h.succeed();
    }
    @GameTest(structure=STRUCTURE)public void validSelectionCannotConnectToAZeroAxisTargetOrSpendItems(GameTestHelper h){
        place(h,P,TrackShape.XO);place(h,Q,TrackShape.NONE);var p=player(h,64);click(h,p,P,-90);h.assertTrue(click(h,p,Q,180)==InteractionResult.FAIL&&p.getMainHandItem().getCount()==64&&p.getMainHandItem().has(AllDataComponents.TRACK_CONNECTING_FROM)&&!h.getBlockState(P).getValue(TrackBlock.HAS_BE),"Source NONE target is rejected before geometry construction or any world/item mutation");h.succeed();
    }

}
