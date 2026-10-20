package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.signal.*;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.track.*;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;

/** Genuine world rails/discovery/shared signals/items/persistence/native menu. STAGED, UNCOMPILED, UNRUN. */
public final class SignalGameTests {
    private static final String STRUCTURE = "create_test:reservoir";
    private static final BlockPos R = new BlockPos(3,6,4), A = new BlockPos(5,6,8), B = A.east(3), C = B.east(3);
    public SignalGameTests() { SignalEdgePoints.register(); }
    private static void line(GameTestHelper h) {
        var state = AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE, TrackShape.XO);
        for (int i=0;i<13;i++) { h.setBlock(R.east(i).below(), Blocks.STONE); h.setBlock(R.east(i), state); }
        for (int i=0;i<13;i++) h.assertTrue(TrackPropagator.reconcile(h.getLevel(),h.absolutePos(R.east(i)),state,false).complete(),"Actual loaded rails discover their native graph");
    }
    private static TrackTargetingSelection selected(GameTestHelper h,BlockPos rail,boolean front) {
        return new TrackTargetingSelection(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(rail)),front,new Vec3(1,0,0),Optional.empty());
    }
    private static SignalBlockEntity signal(GameTestHelper h,BlockPos pos) { return h.getBlockEntity(pos,SignalBlockEntity.class); }
    private static SignalBlockEntity bind(GameTestHelper h,BlockPos pos,BlockPos rail,boolean front) {
        h.setBlock(pos,AllBlocks.TRACK_SIGNAL);var signal=signal(h,pos);h.assertTrue(signal.setTarget(selected(h,rail,front)),"Actual native target selection accepted");
        h.assertTrue(signal.getSignal()!=null,"Real native signal point is bound to its actual owner");return signal;
    }
    private static SignalBlockEntity basic(GameTestHelper h) { line(h);return bind(h,A,R.east(4),true); }
    private static boolean side(SignalBoundary boundary,GlobalPos owner) { if(boundary.owners(true).containsKey(owner))return true;if(boundary.owners(false).containsKey(owner))return false;throw new AssertionError("Missing actual signal owner"); }
    private static void samePoint(GameTestHelper h) { basic(h);bind(h,B,R.east(4),true);bind(h,C,R.east(4),false); }
    private static Player player(GameTestHelper h,ItemStack stack,BlockPos close) { var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atBottomCenterOf(h.absolutePos(close)).add(0,0,2));player.setItemInHand(InteractionHand.MAIN_HAND,stack);return player; }
    private static InteractionResult use(GameTestHelper h,Player player,BlockPos pos) { var actual=h.absolutePos(pos);return player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atBottomCenterOf(actual).add(0,.125,0),Direction.UP,actual,false))); }

    /** Read actual resolved native traffic; no synthetic Train/group/state/readiness is installed. */
    private static boolean actualEmptyNativeTraffic(SignalBlockEntity owner){
        if(owner==null||!(owner.getLevel() instanceof net.minecraft.server.level.ServerLevel level))return false;
        var boundary=owner.getSignal();if(boundary==null||boundary.graph()==null)return false;
        var traffic=com.simibubi.create.content.trains.TrainManager.get(level);
        if(!traffic.isReady()||boundary.graph().manager()!=com.simibubi.create.content.trains.RailwayGraphManager.get(level))return false;
        Boolean primary=null;
        if(boundary.owners(true).containsKey(owner.owner()))primary=true;
        else if(boundary.owners(false).containsKey(owner.owner()))primary=false;
        if(primary==null)return false;
        var group=traffic.signalEdgeGroups().get(boundary.group(primary));
        return group!=null&&group.manager()==traffic&&group.hasResolvedTopology()&&group.trains.isEmpty()&&group.reserved==null;
    }

    @GameTest(structure=STRUCTURE) public void actualSignalTypeAndNativeItemUseTheRealGenericTargetContract(GameTestHelper h) {
        var signal=basic(h);h.assertTrue(SignalEdgePoints.SIGNAL==EdgePointType.TYPES.get(Create.asResource("signal"))&&signal.targeting().pointType()==SignalEdgePoints.SIGNAL&&AllBlocks.TRACK_SIGNAL.asItem()instanceof TrackTargetingBlockItem,"Actual native registry/custom item/generic targeting type");
        h.assertTrue(signal.getSignal().graph().getPoint(SignalEdgePoints.SIGNAL,signal.targeting().id())==signal.getSignal(),"Actual graph owns exactly this concrete source boundary");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualSignalItemSelectsPhysicalRailThenPlacesWithOneSurvivalDebit(GameTestHelper h) {
        line(h);h.setBlock(A.below(),Blocks.STONE);var stack=new ItemStack(AllBlocks.TRACK_SIGNAL,2);var player=player(h,stack,R.east(4));player.setYRot(-90);
        h.assertTrue(use(h,player,R.east(4)).consumesAction()&&stack.has(AllDataComponents.TRACK_TARGETING_SELECTION)&&stack.getCount()==2,"Real source track selection uses a native component");player.setPos(Vec3.atCenterOf(h.absolutePos(A)).add(0,0,2));
        var support=h.absolutePos(A.below());var result=stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false)));
        h.assertTrue(result.consumesAction()&&signal(h,A).getSignal()!=null&&stack.getCount()==1&&!stack.has(AllDataComponents.TRACK_TARGETING_SELECTION),"Real native item placement binds actual owner and debits/clears the remaining stack once");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void threeActualOwnersShareOneBoundaryWithOppositeSides(GameTestHelper h) {
        samePoint(h);var boundary=signal(h,A).getSignal();h.assertTrue(signal(h,B).getSignal()==boundary&&signal(h,C).getSignal()==boundary&&boundary.owners(true).size()+boundary.owners(false).size()==3,"Concrete graph boundary is shared, never duplicated per BE");
        h.assertTrue(side(boundary,signal(h,A).owner())==side(boundary,signal(h,B).owner())&&side(boundary,signal(h,A).owner())!=side(boundary,signal(h,C).owner()),"Actual target direction assigns both real boundary sides");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualSharedBoundaryHasExactlyOneDualOverlayRepresentativeAndTransfersItOnRemoval(GameTestHelper h) {
        samePoint(h);var boundary=signal(h,A).getSignal();var owners=List.of(signal(h,A),signal(h,B),signal(h,C));var drawn=owners.stream().filter(owner->boundary.getOverlayFor(owner.owner())!=SignalBlockEntity.OverlayState.SKIP).toList();h.assertTrue(drawn.size()==1&&boundary.getOverlayFor(drawn.getFirst().owner())==SignalBlockEntity.OverlayState.DUAL,"Actual shared marker has one source dual overlay, never two coplanar copies");var removed=drawn.getFirst().getBlockPos();h.getLevel().destroyBlock(removed,false);long remaining=owners.stream().filter(owner->!owner.getBlockPos().equals(removed)).filter(owner->boundary.getOverlayFor(owner.owner())!=SignalBlockEntity.OverlayState.SKIP).count();h.assertValueEqual(remaining,1L,"Actual surviving owner deterministically takes source overlay custody");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualSharedOwnerRetargetCreatesUniqueBoundaryAndConservesTheOtherOwners(GameTestHelper h) {
        samePoint(h);var moving=signal(h,A);var original=moving.getSignal();var oldId=original.id;h.assertTrue(moving.setTarget(selected(h,R.east(9),true)),"Actual native shared owner accepts complete new track target");var replacement=moving.getSignal();h.assertTrue(replacement!=null&&replacement!=original&&!replacement.id.equals(oldId)&&original.graph().getPoint(SignalEdgePoints.SIGNAL,oldId)==original&&original.owners(true).size()+original.owners(false).size()==2&&!original.hasOwner(moving.owner())&&replacement.hasOwner(moving.owner()),"Real retarget moves exactly one actual owner and cannot duplicate the still-owned graph UUID");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualSharedOwnerChangingFrontRejoinsTheSameBoundaryOnTheOtherSide(GameTestHelper h) {
        samePoint(h);var moving=signal(h,A);var original=moving.getSignal();boolean before=side(original,moving.owner());h.assertTrue(moving.setTarget(selected(h,R.east(4),false)),"Actual existing source owner changes selected track direction");h.assertTrue(moving.getSignal()==original&&original.hasOwner(moving.owner())&&side(original,moving.owner())!=before&&original.owners(true).size()+original.owners(false).size()==3,"Actual canMerge path readds exactly one source owner on the new side before adopting old shared point ID");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualNativeLoadedOwnerCanRetargetBeforeItsFirstTickWithoutTakingSharedUuid(GameTestHelper h) {
        samePoint(h);var moving=signal(h,A);var original=moving.getSignal();var saved=moving.saveWithFullMetadata(h.getLevel().registryAccess());moving.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),saved));h.assertTrue(moving.targeting().getEdgePoint()==null&&h.getBlockEntity(A,SignalBlockEntity.class)==moving,"Actual native load reset cached binding on the exact published owning BE");h.assertTrue(moving.setTarget(selected(h,R.east(9),true)),"Actual reloaded owner can retarget before passive tick rebinds it");var replacement=moving.getSignal();h.assertTrue(replacement!=null&&replacement!=original&&!replacement.id.equals(original.id)&&original.owners(true).size()+original.owners(false).size()==2&&!original.hasOwner(moving.owner()),"Real relocation path retires only this owner and allocates a unique boundary while old shared point remains");h.succeed();
    }
    /** Let real free traffic settle through the unchanged source15-tick red-entry delay. */
    private static void afterActualFreeTraffic(GameTestHelper h,List<SignalBlockEntity> owners,Runnable action){
        h.startSequence().thenWaitUntil(()->{
            for(var owner:owners)h.assertTrue(actualEmptyNativeTraffic(owner)&&owner.getState()==SignalBlockEntity.SignalState.GREEN,
                "The actual native traffic owner and all real signal sides must become free naturally");
        }).thenIdle(16).thenExecute(()->{
            for(var owner:owners)h.assertTrue(actualEmptyNativeTraffic(owner)&&owner.getState()==SignalBlockEntity.SignalState.GREEN,
                "Real empty traffic must stay GREEN through the source red-entry warmup");
            action.run();
        });
    }
    @GameTest(structure=STRUCTURE,maxTicks=70) public void realNeighborPowerForcesOnlyTheActualSharedSideRed(GameTestHelper h) {
        samePoint(h);
        afterActualFreeTraffic(h,List.of(signal(h,A),signal(h,B),signal(h,C)),()->{
            h.setBlock(A.west(),Blocks.REDSTONE_BLOCK);h.runAfterDelay(8,()->{var boundary=signal(h,A).getSignal();h.assertTrue(signal(h,A).getState()==SignalBlockEntity.SignalState.RED&&signal(h,B).getState()==SignalBlockEntity.SignalState.RED&&actualEmptyNativeTraffic(signal(h,C))&&signal(h,C).getState()==SignalBlockEntity.SignalState.GREEN,"Actual shared-side input forces RED while the real resolved empty native opposite group is GREEN");h.assertTrue(boundary.isForcedRed(side(boundary,signal(h,A).owner()))&&!boundary.isForcedRed(side(boundary,signal(h,C).owner())),"Real owner power is recorded per actual boundary side");h.succeed();});
        });
    }
    @GameTest(structure=STRUCTURE,maxTicks=70) public void nativeFallingInputRetainsSourceFourTickDelayAndComparatorFollowsRed(GameTestHelper h) {
        basic(h);
        afterActualFreeTraffic(h,List.of(signal(h,A)),()->{
            h.setBlock(A.west(),Blocks.REDSTONE_BLOCK);h.runAfterDelay(7,()->{h.assertTrue(signal(h,A).isPowered()&&h.getBlockState(A).getAnalogOutputSignal(h.getLevel(),h.absolutePos(A),Direction.NORTH)==15,"Actual RED signal supplies source comparator15");h.setBlock(A.west(),Blocks.AIR);});
            h.runAfterDelay(9,()->h.assertTrue(h.getBlockState(A).getValue(SignalBlock.POWERED),"Actual falling input stays latched before four ticks"));
            h.runAfterDelay(15,()->{h.assertTrue(!h.getBlockState(A).getValue(SignalBlock.POWERED)&&actualEmptyNativeTraffic(signal(h,A))&&signal(h,A).getState()==SignalBlockEntity.SignalState.GREEN&&h.getBlockState(A).getAnalogOutputSignal(h.getLevel(),h.absolutePos(A),Direction.NORTH)==0,"Actual native scheduled falling edge clears forced red and the genuine resolved empty traffic group is GREEN");h.succeed();});
        });
    }
    /** Use each boundary's own real directed edge: native discovery may retain collinear vertices. */
    private static TrackEdge increasingXEdge(GameTestHelper h,TrackGraph graph,SignalBoundary point) {
        var first=graph.locateNode(point.edgeLocation.getFirst());var second=graph.locateNode(point.edgeLocation.getSecond());
        h.assertTrue(first!=null&&second!=null,"Actual signal endpoints remain owned by its genuine graph");
        var edge=graph.getConnectionsFrom(first).get(second);
        if(first.getLocation().getX()>second.getLocation().getX())edge=graph.getConnectionsFrom(second).get(first);
        h.assertTrue(edge!=null&&!edge.isTurn()&&edge.node1.getLocation().getX()<edge.node2.getLocation().getX()&&edge.node1.getLocation().getY()==edge.node2.getLocation().getY()&&edge.node1.getLocation().getZ()==edge.node2.getLocation().getZ()&&edge.getEdgeData().getPoints().contains(point),"Actual line fixture uses each signal's own ascending-X directed straight edge");
        return edge;
    }
    private static void assertThreeActualLinePartitions(GameTestHelper h,TrackGraph graph,SignalBoundary first,SignalBoundary second) {
        h.assertTrue(first!=second&&first.graph()==graph&&second.graph()==graph,"Two actual distinct boundaries belong to this same genuine graph");
        SignalTopology.refresh(graph);var firstEdge=increasingXEdge(h,graph,first);var secondEdge=increasingXEdge(h,graph,second);
        double firstPosition=first.getLocationOn(firstEdge),secondPosition=second.getLocationOn(secondEdge);
        double firstWorldX=firstEdge.getPosition(graph,firstPosition/firstEdge.getLength()).x,secondWorldX=secondEdge.getPosition(graph,secondPosition/secondEdge.getLength()).x;
        h.assertTrue(firstWorldX<secondWorldX,"Actual source boundary order follows physical rail coordinates across any retained vertices");
        var left=first.groupFor(firstEdge.node1);var middle=first.groupFor(firstEdge.node2);var right=second.groupFor(secondEdge.node2);
        String geometry="; firstEdge="+firstEdge.node1.getLocation()+" -> "+firstEdge.node2.getLocation()+", secondEdge="+secondEdge.node1.getLocation()+" -> "+secondEdge.node2.getLocation()+", groups="+left+" / "+middle+" / "+right;
        h.assertTrue(left!=null&&middle!=null&&right!=null&&!left.equals(middle)&&!middle.equals(right)&&!left.equals(right)&&middle.equals(second.groupFor(secondEdge.node1)),"Real line has three actual source signal partitions"+geometry);
        h.assertTrue(firstEdge.getEdgeData().getGroupAtPosition(graph,firstPosition-.1).equals(left)&&firstEdge.getEdgeData().getGroupAtPosition(graph,firstPosition+.1).equals(middle)&&secondEdge.getEdgeData().getGroupAtPosition(graph,secondPosition-.1).equals(middle)&&secondEdge.getEdgeData().getGroupAtPosition(graph,secondPosition+.1).equals(right),"Actual EdgeData resolves both sides of each concrete point on its own directed edge"+geometry);
        var reverseFirst=graph.getConnectionsFrom(firstEdge.node2).get(firstEdge.node1);var reverseSecond=graph.getConnectionsFrom(secondEdge.node2).get(secondEdge.node1);
        h.assertTrue(reverseFirst.getEdgeData().getGroupAtPosition(graph,reverseFirst.getLength()-firstPosition-.1).equals(middle)&&reverseFirst.getEdgeData().getGroupAtPosition(graph,reverseFirst.getLength()-firstPosition+.1).equals(left)&&reverseSecond.getEdgeData().getGroupAtPosition(graph,reverseSecond.getLength()-secondPosition-.1).equals(right)&&reverseSecond.getEdgeData().getGroupAtPosition(graph,reverseSecond.getLength()-secondPosition+.1).equals(middle),"Actual reciprocal directed edges resolve the same physical three partitions"+geometry);
        SignalTopology.refresh(graph);h.assertTrue(first.groupFor(firstEdge.node1).equals(left)&&first.groupFor(firstEdge.node2).equals(middle)&&second.groupFor(secondEdge.node1).equals(middle)&&second.groupFor(secondEdge.node2).equals(right),"Unchanged native topology keeps all real saved partition identities"+geometry);
    }
    @GameTest(structure=STRUCTURE) public void actualTwoBoundariesPartitionRealDirectedEdgeIntervalsAndKeepGroupIdentity(GameTestHelper h) {
        var a=basic(h);var b=bind(h,B,R.east(8),true);assertThreeActualLinePartitions(h,a.getSignal().graph(),a.getSignal(),b.getSignal());h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualSubdividedLineKeepsThreePartitionsForBothNodeInsertionOrders(GameTestHelper h) {
        h.setBlock(A,AllBlocks.TRACK_SIGNAL);h.setBlock(B,AllBlocks.TRACK_SIGNAL);
        for(boolean reverse:new boolean[]{false,true}) {
            var graph=new TrackGraph();var high=reverse?RailGraphTestFixtures.node(graph,13,0,0):null;var low=RailGraphTestFixtures.node(graph,0,0,0);var middle=RailGraphTestFixtures.node(graph,6,0,0);if(high==null)high=RailGraphTestFixtures.node(graph,13,0,0);
            RailGraphTestFixtures.link(graph,low,middle);RailGraphTestFixtures.link(graph,middle,high);
            var first=SignalEdgePoints.SIGNAL.create();first.setLocation(Couple.create(low.getLocation(),middle.getLocation()),4.5);first.blockEntityAdded(signal(h,A),true);graph.addPoint(SignalEdgePoints.SIGNAL,first);
            var second=SignalEdgePoints.SIGNAL.create();second.setLocation(Couple.create(middle.getLocation(),high.getLocation()),2.5);second.blockEntityAdded(signal(h,B),true);graph.addPoint(SignalEdgePoints.SIGNAL,second);
            h.assertTrue(graph.getPoints(SignalEdgePoints.SIGNAL).size()==2&&graph.getNodes().size()==3,"Genuine regression keeps two actual points on distinct adjacent edges; no group identities are injected");assertThreeActualLinePartitions(h,graph,first,second);
        }
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualLoopConnectsBothBoundarySidesWithoutInventingFreeTraffic(GameTestHelper h) {
        h.setBlock(A,AllBlocks.TRACK_SIGNAL);var graph=new TrackGraph();var a=RailGraphTestFixtures.node(graph,0,0,0);var b=RailGraphTestFixtures.node(graph,4,0,0);var c=RailGraphTestFixtures.node(graph,4,0,4);var d=RailGraphTestFixtures.node(graph,0,0,4);
        RailGraphTestFixtures.link(graph,a,b);RailGraphTestFixtures.link(graph,b,c);RailGraphTestFixtures.link(graph,c,d);RailGraphTestFixtures.link(graph,d,a);var boundary=SignalEdgePoints.SIGNAL.create();boundary.setLocation(Couple.create(a.getLocation(),b.getLocation()),2);boundary.blockEntityAdded(signal(h,A),true);graph.addPoint(SignalEdgePoints.SIGNAL,boundary);SignalTopology.refresh(graph);boundary.tick(graph,false);
        h.assertTrue(boundary.group(true)!=null&&boundary.group(true).equals(boundary.group(false))&&boundary.getStateFor(signal(h,A).owner())==SignalBlockEntity.SignalState.INVALID,"Actual loop has one connected region on both sides and no fictional train occupancy result");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualNativeGraphCodecKeepsFullMultiOwnerSignalDataAndOneSharedPointInstance(GameTestHelper h) {
        samePoint(h);var original=signal(h,A).getSignal();SignalTopology.refresh(original.graph());original.setSignalType(signal(h,A).owner(),SignalBlock.SignalType.CROSS_SIGNAL);var tag=RailGraphTestFixtures.encode(original.graph(),h.getLevel().registryAccess());var graph=RailGraphTestFixtures.decode(tag,h.getLevel().registryAccess());var loaded=graph.getPoint(SignalEdgePoints.SIGNAL,original.id);
        h.assertTrue(loaded!=original&&loaded.owners(true).equals(original.owners(true))&&loaded.owners(false).equals(original.owners(false))&&Objects.equals(loaded.group(true),original.group(true))&&loaded.getTypeFor(signal(h,A).owner())==SignalBlock.SignalType.CROSS_SIGNAL,"Actual full typed point codec preserves source ownership/type/group fields");
        var edge=graph.getConnection(loaded.edgeLocation.map(graph::locateNode));h.assertTrue(edge.getEdgeData().getPoints().contains(loaded)&&graph.getConnectionsFrom(edge.node2).get(edge.node1).getEdgeData().getPoints().contains(loaded),"Both native directed representations reference the same decoded point");graph.validate();h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void sourceLegacyTilesAndTypeFieldsMigrateIntoActualGlobalOwners(GameTestHelper h) {
        var signal=basic(h);var boundary=signal.getSignal();var tag=new CompoundTag();var dimensions=new DimensionPalette();boundary.write(tag,h.getLevel().registryAccess(),dimensions);var tile=new CompoundTag();var pos=new CompoundTag();pos.putInt("X",signal.getBlockPos().getX());pos.putInt("Y",signal.getBlockPos().getY());pos.putInt("Z",signal.getBlockPos().getZ());tile.put("Pos",pos);tile.putBoolean("Power",true);var tiles=new ListTag();tiles.add(tile);int index=side(boundary,signal.owner())?1:2;tag.put("Tiles"+index,tiles);tag.putString("Type"+index,"CROSS_SIGNAL");var loaded=SignalEdgePoints.SIGNAL.create();loaded.read(tag,h.getLevel().registryAccess(),false,dimensions);
        h.assertTrue(loaded.hasOwner(signal.owner())&&loaded.isForcedRed(side(loaded,signal.owner()))&&loaded.getTypeFor(signal.owner())==SignalBlock.SignalType.CROSS_SIGNAL,"Actual source Pos X/Y/Z, Power and enum fields migrate without placeholder owners");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void nativeMigrationRetainsGeometryCustodyAndDoesNotCopyOtherSignalOwners(GameTestHelper h) {
        samePoint(h);var original=signal(h,A).getSignal();var data=new CompoundTag();var dimensions=new DimensionPalette();original.write(data,h.getLevel().registryAccess(),dimensions);var migrated=SignalEdgePoints.SIGNAL.create();migrated.read(data,h.getLevel().registryAccess(),true,dimensions);
        h.assertTrue(migrated.owners(true).isEmpty()&&migrated.owners(false).isEmpty()&&migrated.group(true)==null&&migrated.edgeLocation==null,"Actual source migration waits for target discovery and only the new actual owner is added");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualRemovalRetainsOtherOwnersThenRemovesLastPointAndRestoresPassiveGroups(GameTestHelper h) {
        samePoint(h);var boundary=signal(h,A).getSignal();var graph=boundary.graph();SignalTopology.refresh(graph);h.getLevel().destroyBlock(h.absolutePos(A),false);h.assertTrue(graph.getPoint(SignalEdgePoints.SIGNAL,boundary.id)==boundary&&boundary.owners(true).size()+boundary.owners(false).size()==2,"Removing one real native owner retains shared graph point");h.getLevel().destroyBlock(h.absolutePos(B),false);h.getLevel().destroyBlock(h.absolutePos(C),false);h.assertTrue(graph.getPoint(SignalEdgePoints.SIGNAL,boundary.id)==null,"Last actual signal removal retires actual point");for(var node:graph.getNodes())for(var edge:graph.getConnectionsFrom(graph.locateNode(node)).values())h.assertTrue(EdgeData.passiveGroup.equals(edge.getEdgeData().getSingleSignalGroup()),"Actual no-boundary edges return to source passive group identity");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void realSignalSaveLoadBeforeLevelPreservesTargetSelectionIdentityAndSourceState(GameTestHelper h) {
        var original=basic(h);var saved=original.saveWithFullMetadata(h.getLevel().registryAccess());var copy=(SignalBlockEntity)BlockEntity.loadStatic(original.getBlockPos(),original.getBlockState(),saved,h.getLevel().registryAccess());h.assertTrue(copy!=null&&copy.getLevel()==null&&copy.targeting().id().equals(original.targeting().id())&&copy.targeting().selection().equals(original.targeting().selection())&&copy.getState()==original.getState(),"Actual native BE codec retains relative target and point UUID before world publication");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void genuineUnknownOwnerFrontierDefersAllMigrationWithoutCreatingTerrainTickets(GameTestHelper h) {
        var original=basic(h).getSignal();var side=new SignalBoundary.Side(List.of(new SignalBoundary.Owner(GlobalPos.of(h.getLevel().dimension(),new BlockPos(2600000,80,2600000)),false)),SignalBlock.SignalType.ENTRY_SIGNAL,Optional.empty(),true,SignalBlockEntity.SignalState.INVALID);var tag=(CompoundTag)SignalBoundary.CODEC.encodeStart(NbtOps.INSTANCE,original).getOrThrow();tag.put("First",SignalBoundary.Side.CODEC.encodeStart(NbtOps.INSTANCE,side).getOrThrow());var loaded=SignalBoundary.CODEC.parse(NbtOps.INSTANCE,tag).getOrThrow();var far=side.owners().getFirst().position().pos();boolean deferred=false;try{loaded.invalidate(h.getLevel());}catch(UnloadedTrackException pending){deferred=true;}
        h.assertTrue(deferred&&loaded.owners(true).size()==1&&h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Actual global owner preflight preserves migration custody and requests no missing terrain");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualNativeModePacketRequiresExactMenuOwnerAndFreshRevision(GameTestHelper h) {
        var signal=basic(h);ServerPlayer player=h.makeMockServerPlayerInLevel();player.setPos(Vec3.atCenterOf(signal.getBlockPos()).add(0,0,2));SignalMenu.open(player,signal);h.assertTrue(player.containerMenu instanceof SignalMenu,"Real server opened actual native signal menu");int id=player.containerMenu.containerId;var packet=new SignalConfigurationPacket(id,signal.getBlockPos(),signal.revision(),SignalBlock.SignalType.CROSS_SIGNAL);h.assertTrue(packet.apply(player)&&signal.getSignal().getTypeFor(signal.owner())==SignalBlock.SignalType.CROSS_SIGNAL,"Actual native editor configures actual shared point type");h.assertFalse(packet.apply(player),"Consumed stale configuration revision cannot overwrite again");h.assertFalse(new SignalConfigurationPacket(id+1,signal.getBlockPos(),signal.revision(),SignalBlock.SignalType.ENTRY_SIGNAL).apply(player),"Other native container has no authority");h.setBlock(A,Blocks.AIR);h.setBlock(A,AllBlocks.TRACK_SIGNAL);h.assertFalse(new SignalConfigurationPacket(id,h.absolutePos(A),signal(h,A).revision(),SignalBlock.SignalType.ENTRY_SIGNAL).apply(player),"Replacement actual BE cannot inherit former menu authority");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualSignalCannotOverlapARealStationAtTheSameTrackPoint(GameTestHelper h) {
        line(h);h.setBlock(A,AllBlocks.TRACK_STATION);var station=h.getBlockEntity(A,StationBlockEntity.class);h.assertTrue(station.setTarget(selected(h,R.east(4),true)),"Actual native station bound first");h.setBlock(B,AllBlocks.TRACK_SIGNAL);h.assertFalse(signal(h,B).setTarget(selected(h,R.east(4),true)),"Source incompatible point overlap is rejected against the actual station");h.assertTrue(station.getStation()!=null&&signal(h,B).getSignal()==null,"No fake successful boundary or lost station ownership");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void actualOffWorldTransformRotatesRelativeTargetAndAllocatesNewOwnerPointIdentity(GameTestHelper h) {
        var original=basic(h);var copy=(SignalBlockEntity)BlockEntity.loadStatic(original.getBlockPos(),original.getBlockState(),original.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());var before=copy.targeting().selection();var id=copy.targeting().id();var transform=new StructureTransform(BlockPos.ZERO,Direction.Axis.Y,Rotation.CLOCKWISE_90,Mirror.NONE);copy.transform(copy,transform);var after=copy.targeting().selection();h.assertTrue(!copy.targeting().id().equals(id)&&after.target().dimension().equals(before.target().dimension())&&after.target().pos().subtract(copy.getBlockPos()).equals(transform.applyWithoutOffset(before.target().pos().subtract(copy.getBlockPos())))&&after.axis().equals(transform.applyWithoutOffsetUncentered(before.axis()))&&copy.targeting().getEdgePoint()==null,"Actual no-world transform changes saved geometry/identity without fabricating graph publication");h.succeed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=50) public void realNativeCurveConstructionBindsActualSignalAtItsRealSegmentAndSavesRelativeCurve(GameTestHelper h) {
        var end=R.offset(8,0,8);for(var pos:List.of(R,end)){h.setBlock(pos.below(),Blocks.STONE);h.setBlock(pos,AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,pos.equals(R)?TrackShape.XO:TrackShape.ZO));}
        var builder=player(h,new ItemStack(AllBlocks.TRACK,64),R);builder.setYRot(-90);h.assertTrue(use(h,builder,R).consumesAction(),"Actual native track item selects real first endpoint");builder.setPos(Vec3.atBottomCenterOf(h.absolutePos(end)).add(0,0,2));builder.setYRot(180);h.assertTrue(use(h,builder,end).consumesAction(),"Actual native track item builds physical reciprocal Bezier and spends real stock");
        h.runAfterDelay(8,()->{var actual=h.getBlockEntity(R,TrackBlockEntity.class).getConnections().get(h.absolutePos(end));h.assertTrue(actual!=null&&actual.isPrimary(),"Actual primary curve exists");TrackPropagator.reconcile(h.getLevel(),h.absolutePos(R),h.getBlockState(R),false);int segment=Math.min(actual.getSegmentCount()-2,actual.getSegmentCount()/2);double count=Math.floor(actual.getLength()*2);var tangent=actual.getPosition((segment+2)/count).subtract(actual.getPosition(segment/count)).normalize();var owner=R.west().south(3);h.setBlock(owner,AllBlocks.TRACK_SIGNAL);var signal=signal(h,owner);var selected=new TrackTargetingSelection(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(R)),true,tangent,Optional.of(new BezierTrackPointLocation(h.absolutePos(end),segment)));h.assertTrue(signal.setTarget(selected)&&signal.getSignal()!=null&&signal.targeting().isOnCurve()&&!signal.targeting().isOrthogonal(),"Actual native signal binds actual curved graph interval at selected physical segment");var copy=(SignalBlockEntity)BlockEntity.loadStatic(signal.getBlockPos(),signal.getBlockState(),signal.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(copy!=null&&copy.getLevel()==null&&copy.targeting().selection().curve().equals(selected.curve())&&copy.targeting().selection().axis().equals(selected.axis()),"Actual native no-world save/load preserves real relative curve endpoint/segment and signed tangent");h.succeed();});
    }
    @GameTest(structure=STRUCTURE) public void actualSignalRecipeAndLootPreserveSourceFourOutputAndInputs(GameTestHelper h) {
        var input=CraftingInput.of(2,1,List.of(new ItemStack(AllBlocks.RAILWAY_CASING),new ItemStack(AllItems.ELECTRON_TUBE)));var output=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value().assemble(input);h.assertTrue(output.is(AllBlocks.TRACK_SIGNAL.asItem())&&output.getCount()==4,"Actual source railway casing+electron tube recipe yields four real signals");h.setBlock(A,AllBlocks.TRACK_SIGNAL);var drops=Block.getDrops(h.getBlockState(A),h.getLevel(),h.absolutePos(A),signal(h,A));h.assertTrue(drops.size()==1&&drops.getFirst().is(AllBlocks.TRACK_SIGNAL.asItem()),"Actual native signal loot returns its real item");h.succeed();
    }
}
