package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.glue.*;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.content.redstone.link.*;
import com.simibubi.create.content.redstone.link.controller.*;
import com.simibubi.create.content.trains.signal.*;
import com.simibubi.create.content.trains.track.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Original accepted glue-selection, controller-input and targeting-placement callbacks. UNRUN. */
public final class InteractionAdvancementGameTests {
    private static final BlockPos P=new BlockPos(3,3,3),Q=P.east();
    private static final BlockPos R=new BlockPos(3,6,4),A=new BlockPos(5,6,8);
    private static NativeSurvivalTestPlayer join(GameTestHelper h,BlockPos near){return NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(near)).add(0,0,2));}
    private static boolean done(ServerPlayer player,String id){return GameplayAdvancementGameTests.done(player,id);}
    private static void stones(GameTestHelper h){h.setBlock(P,Blocks.STONE);h.setBlock(Q,Blocks.STONE);}
    private static SuperGlueSelectionPacket selection(GameTestHelper h){return new SuperGlueSelectionPacket(h.absolutePos(P),h.absolutePos(Q));}

    @GameTest public void acceptedPaidGlueSelectionAwardsOnlyActualInteractor(GameTestHelper h){
        h.setBlock(P,Blocks.STONE);h.setBlock(P.east(2),Blocks.STONE);
        try(var session=join(h,P);var observer=join(h,P)){
            var player=session.player();var glue=new ItemStack(AllItems.SUPER_GLUE);player.setItemInHand(InteractionHand.MAIN_HAND,glue);
            h.assertTrue(!SuperGlueHandler.select(player,new SuperGlueSelectionPacket(h.absolutePos(P),h.absolutePos(P.east(2))))&&!done(player,"super_glue")&&glue.getDamageValue()==0,"Disconnected real selection is refused without award or payment");
            h.setBlock(Q,Blocks.STONE);
            h.assertTrue(SuperGlueHandler.select(player,selection(h)),"Actual native two-block selection accepted");
            var volumes=h.getLevel().getEntitiesOfClass(SuperGlueEntity.class,new AABB(h.absolutePos(P)).inflate(3));
            h.assertTrue(volumes.size()==1&&volumes.getFirst().getBoundingBox().equals(SuperGlueEntity.span(h.absolutePos(P),h.absolutePos(Q)))&&glue.getDamageValue()==1,"One admitted original glue span is paid exactly once");
            h.assertTrue(done(player,"super_glue")&&!done(observer.player(),"super_glue"),"Only actual successful selection interactor earns original custom criterion");
        }h.succeed();
    }
    @GameTest public void fakeGlueSelectionDoesNotManufactureAdvancementProgress(GameTestHelper h){
        stones(h);var player=new DeployerFakePlayer(h.getLevel(),UUID.randomUUID());player.setPos(Vec3.atCenterOf(h.absolutePos(P)).add(0,0,2));player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.SUPER_GLUE));
        h.assertTrue(SuperGlueHandler.select(player,selection(h))&&!done(player,"super_glue"),"Fake physical glue operation cannot earn genuine player progress");player.discard();h.succeed();
    }
    @GameTest public void sourceOffhandPlacementDoesNotAwardSelectionOnlyCriterion(GameTestHelper h){
        h.setBlock(P,Blocks.STONE);
        try(var session=join(h,P)){
            var player=session.player();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE,2));player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AllItems.SUPER_GLUE));
            var support=h.absolutePos(P);var point=Vec3.atCenterOf(support).add(0,0,.5);var direction=point.subtract(player.getEyePosition());
            player.setYRot((float)(Math.atan2(direction.z,direction.x)*180/Math.PI)-90);player.setXRot((float)(-Math.atan2(direction.y,Math.sqrt(direction.x*direction.x+direction.z*direction.z))*180/Math.PI));
            h.assertTrue(player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(point,Direction.SOUTH,support,false))).consumesAction(),"Actual native offhand placement accepted");
            h.assertTrue(SuperGlueEntity.isGlued(h.getLevel(),support,Direction.SOUTH,null)&&player.getOffhandItem().getDamageValue()==1&&!done(player,"super_glue"),"Pinned selection-only award is not broadened to successful offhand attachment");
        }h.succeed();
    }

    private static RedstoneLinkBlockEntity link(GameTestHelper h,BlockPos pos,boolean receiver){
        h.setBlock(pos.below(),Blocks.STONE);h.setBlock(pos,AllBlocks.REDSTONE_LINK.defaultBlockState().setValue(RedstoneLinkBlock.FACING,Direction.UP).setValue(RedstoneLinkBlock.RECEIVER,receiver));
        var link=h.getBlockEntity(pos,RedstoneLinkBlockEntity.class);link.getLink().setFrequency(true,RedstoneLinkGameTests.sample(h,true));link.getLink().setFrequency(false,RedstoneLinkGameTests.sample(h,false));link.revalidateNetwork();return link;
    }
    @GameTest public void genuineControllerInputAwardsOnlyAfterListeningLinkExists(GameTestHelper h){
        link(h,P,false);
        try(var session=join(h,P);var observer=join(h,P)){
            var player=session.player();var input=new LinkedControllerPackets.Input(List.of(3),true,null);
            h.assertTrue(!LinkedControllerPackets.input(player,input)&&!done(player,"linked_controller"),"No held controller means refused actual input and no award");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.LINKED_CONTROLLER));
            h.assertTrue(LinkedControllerPackets.bind(player,new LinkedControllerPackets.Bind(3,h.absolutePos(P))),"Native bind copies actual physical link frequencies");
            h.assertTrue(LinkedControllerPackets.input(player,input)&&!done(player,"linked_controller"),"A real press with only a transmitter cannot manufacture receiver activation progress");
            LinkedControllerServerHandler.releasePlayer(h.getLevel().getServer(),player.getUUID());var receiver=link(h,P.east(3),true);
            h.assertTrue(LinkedControllerPackets.input(player,new LinkedControllerPackets.Input(List.of(3),false,null))&&!done(player,"linked_controller"),"Release never earns the source new-press criterion");
            h.assertTrue(LinkedControllerPackets.input(player,input)&&receiver.getReceivedSignal()==15,"New real pressed input actually powers native listening LinkBehaviour");
            h.assertTrue(done(player,"linked_controller")&&!done(observer.player(),"linked_controller"),"Only the actual input session player earns original remote activation criterion");
            LinkedControllerServerHandler.releasePlayer(h.getLevel().getServer(),player.getUUID());
        }h.succeed();
    }
    @GameTest public void fakeControllerInputCannotAwardUnregisteredActor(GameTestHelper h){
        var receiver=link(h,P,true);var player=new DeployerFakePlayer(h.getLevel(),UUID.randomUUID());player.setPos(Vec3.atCenterOf(h.absolutePos(P)).add(0,0,2));player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.LINKED_CONTROLLER));
        h.assertTrue(LinkedControllerPackets.bind(player,new LinkedControllerPackets.Bind(0,h.absolutePos(P))),"Actual native fake actor bind still obeys range/item rules");
        h.assertTrue(LinkedControllerPackets.input(player,new LinkedControllerPackets.Input(List.of(0),true,null))&&receiver.getReceivedSignal()==15&&!done(player,"linked_controller"),"Real network work cannot award a fake/unregistered player identity");
        LinkedControllerServerHandler.releasePlayer(h.getLevel().getServer(),player.getUUID());player.discard();h.succeed();
    }

    private static void rails(GameTestHelper h){
        SignalEdgePoints.register();var state=AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.XO);
        for(int i=0;i<13;i++){h.setBlock(R.east(i).below(),Blocks.STONE);h.setBlock(R.east(i),state);}
        for(int i=0;i<13;i++)h.assertTrue(TrackPropagator.reconcile(h.getLevel(),h.absolutePos(R.east(i)),state,false).complete(),"Native loaded rails discover actual graph");h.setBlock(A.below(),Blocks.STONE);
    }
    private static InteractionResult signalUse(GameTestHelper h,ServerPlayer player,BlockPos pos){var actual=h.absolutePos(pos);return player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(actual).add(0,.5,0),Direction.UP,actual,false)));}
    @GameTest(structure="create_test:reservoir") public void nativeSignalSelectionAndFailedPlacementAwardOnlySuccessfulOwner(GameTestHelper h){
        rails(h);
        try(var session=join(h,R.east(4));var observer=join(h,A)){
            var player=session.player();player.setYRot(-90);var stack=new ItemStack(AllBlocks.TRACK_SIGNAL,2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            h.assertTrue(signalUse(h,player,R.east(4)).consumesAction()&&stack.has(AllDataComponents.TRACK_TARGETING_SELECTION)&&!done(player,"track_signal"),"Actual rail selection alone is not placement achievement");
            player.setPos(Vec3.atCenterOf(h.absolutePos(A)).add(0,0,2));h.setBlock(A,Blocks.STONE);
            h.assertTrue(!signalUse(h,player,A.below()).consumesAction()&&stack.getCount()==2&&stack.has(AllDataComponents.TRACK_TARGETING_SELECTION)&&!done(player,"track_signal"),"Physically blocked native placement retains original paid stock/selection without awarding");
            h.setBlock(A,Blocks.AIR);
            h.assertTrue(signalUse(h,player,A.below()).consumesAction(),"Actual selected signal BlockItem placement accepted");var signal=h.getBlockEntity(A,SignalBlockEntity.class);
            h.assertTrue(signal.getSignal()!=null&&signal.getSignal().hasOwner(signal.owner())&&stack.getCount()==1&&!stack.has(AllDataComponents.TRACK_TARGETING_SELECTION),"Exactly one native signal owns bound original graph point and one finite item was paid");
            h.assertTrue(done(player,"track_signal")&&!done(observer.player(),"track_signal"),"Only successful actual signal interactor receives source placement criterion");
        }h.succeed();
    }
    @GameTest(structure="create_test:reservoir") public void fakeSignalPlacementCannotManufactureAdvancementProgress(GameTestHelper h){
        rails(h);var player=new DeployerFakePlayer(h.getLevel(),UUID.randomUUID());player.setPos(Vec3.atCenterOf(h.absolutePos(R.east(4))).add(0,0,2));player.setYRot(-90);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.TRACK_SIGNAL,2));
        h.assertTrue(signalUse(h,player,R.east(4)).consumesAction(),"Actual fake actor selects native rail");player.setPos(Vec3.atCenterOf(h.absolutePos(A)).add(0,0,2));
        h.assertTrue(signalUse(h,player,A.below()).consumesAction()&&h.getBlockEntity(A,SignalBlockEntity.class).getSignal()!=null&&!done(player,"track_signal"),"Actual fake placement binds real native signal but cannot earn genuine player progress");player.discard();h.succeed();
    }
}
