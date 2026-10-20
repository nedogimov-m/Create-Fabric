package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.bearing.*;
import com.simibubi.create.content.equipment.bell.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.phys.*;

/** Genuine original parent device callbacks. Physical placement/capture/interaction/native progress only. UNRUN. */
public final class DeviceAdvancementGameTests {
    private static final BlockPos P=new BlockPos(4,3,4),H=P.above(),M=P.above(2);
    private record Fixture(ServerPlayer player,ClockworkBearingBlockEntity bearing,ItemStack physicalStack,NativeSurvivalTestPlayer session) {}
    private static AdvancementHolder advancement(GameTestHelper h,String id) {
        var actual=h.getLevel().getServer().getAdvancements().get(Create.asResource(id));
        h.assertTrue(actual!=null,"Actual original advancement and native parent resource closure loaded: "+id);return actual;
    }
    private static NativeSurvivalTestPlayer player(GameTestHelper h) {
        h.setBlock(P.south(2).below(),Blocks.STONE);var session=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(P)).add(0,0,2));session.player().setXRot(90);return session;
    }
    private static Fixture place(GameTestHelper h,int count) {
        h.setBlock(P.below(),Blocks.STONE);var session=player(h);var player=session.player();advancement(h,"clockwork_bearing");
        var item=new ItemStack(AllBlocks.CLOCKWORK_BEARING,count);player.setItemInHand(InteractionHand.MAIN_HAND,item);
        var support=h.absolutePos(P.below());
        h.assertTrue(item.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false))).consumesAction(),"Native BlockItem places the actual clockwork bearing");
        var bearing=h.getBlockEntity(P,ClockworkBearingBlockEntity.class);
        h.assertTrue(item.getCount()==count-1 && h.getBlockState(P).getValue(BearingBlock.FACING)==Direction.UP && player.getUUID().equals(bearing.advancementOwner()),"One original physical bearing is debited and the real native player owns its source award");
        return new Fixture(player,bearing,item,session);
    }
    private static void hands(GameTestHelper h) {
        h.setBlock(H,Blocks.STONE);h.setBlock(M,Blocks.BARREL);var freight=new ItemStack(Items.DIAMOND,7);
        freight.set(DataComponents.CUSTOM_NAME,Component.literal("Original device freight"));h.getBlockEntity(M,BarrelBlockEntity.class).setItem(0,freight);
    }
    private static void assertRestored(GameTestHelper h,Fixture actual) {
        h.assertTrue(!actual.bearing.isRunning() && actual.bearing.getHourHand()==null && actual.bearing.getMinuteHand()==null && h.getBlockState(H).is(Blocks.STONE) && h.getBlockState(M).is(Blocks.BARREL),"Both actual native hands return to their physical anchors");
        var freight=h.getBlockEntity(M,BarrelBlockEntity.class).getItem(0);
        h.assertTrue(freight.is(Items.DIAMOND)&&freight.getCount()==7&&freight.getHoverName().getString().equals("Original device freight"),"Exact original physical component-bearing cargo restores once");
    }
    @GameTest public void nativeClockworkOwnerUuidPersistsAcrossNoWorldLoadAndResave(GameTestHelper h) {
        var actual=place(h,2);var saved=actual.bearing.saveWithFullMetadata(h.getLevel().registryAccess());
        h.assertTrue(saved.read("Owner",UUIDUtil.CODEC).orElseThrow().equals(actual.player.getUUID()),"Original Owner key uses the genuine native UUID codec");
        var copy=(ClockworkBearingBlockEntity)BlockEntity.loadStatic(h.absolutePos(P),h.getBlockState(P),saved,h.getLevel().registryAccess());
        h.assertTrue(copy!=null&&copy.getLevel()==null&&actual.player.getUUID().equals(copy.advancementOwner())&&copy.saveWithFullMetadata(h.getLevel().registryAccess()).read("Owner",UUIDUtil.CODEC).orElseThrow().equals(actual.player.getUUID()),"Actual source owner survives native no-world load/resave without constructing a synthetic controller");
        h.assertFalse(actual.player.getAdvancements().getOrStartProgress(advancement(h,"clockwork_bearing")).isDone(),"Placement alone cannot claim a completed clockwork assembly");actual.session.close();h.succeed();
    }
    @GameTest(maxTicks=100) public void realEmptyHandAssemblyAwardsOnlyAfterBothPhysicalOwnersAcceptAndRestoreCargo(GameTestHelper h) {
        var actual=place(h,1);hands(h);var holder=advancement(h,"clockwork_bearing");
        h.assertTrue(actual.player.getMainHandItem().isEmpty(),"Actual paid single-item placement naturally leaves the interacting hand empty");h.useBlock(P,actual.player);
        h.startSequence().thenWaitUntil(()->{
            h.assertTrue(actual.bearing.isRunning()&&!actual.bearing.isRecovering()&&actual.bearing.getHourHand()!=null&&actual.bearing.getMinuteHand()!=null,"Genuine server ticks perform accepted dual-body capture");
            h.assertTrue(h.getBlockState(H).isAir()&&h.getBlockState(M).isAir(),"Both original physical hands actually leave the world");
            h.assertTrue(actual.player.getAdvancements().getOrStartProgress(holder).isDone()&&actual.bearing.advancementOwner()==null,"Real native criterion advances only on accepted capture and releases the completed source owner");
        }).thenExecute(()->h.assertTrue(actual.bearing.disassemble(),"Actual paired owner restoration begins"))
                .thenWaitUntil(()->assertRestored(h,actual)).thenExecute(actual.session::close).thenSucceed();
    }
    @GameTest(maxTicks=100) public void failedEmptyCaptureRetainsRealOwnerAndCannotAwardUntilPhysicalRetrySucceeds(GameTestHelper h) {
        var actual=place(h,1);var holder=advancement(h,"clockwork_bearing");actual.bearing.assemble();
        h.assertTrue(!actual.bearing.isRunning()&&actual.bearing.getHourHand()==null&&!actual.player.getAdvancements().getOrStartProgress(holder).isDone()&&actual.player.getUUID().equals(actual.bearing.advancementOwner()),"Actual absent hand refuses capture, preserves native owner and cannot manufacture progress");
        hands(h);h.useBlock(P,actual.player);
        h.startSequence().thenWaitUntil(()->h.assertTrue(actual.bearing.isRunning()&&actual.player.getAdvancements().getOrStartProgress(holder).isDone(),"A genuine later physical hand/native retry can earn the original award"))
                .thenExecute(()->h.assertTrue(actual.bearing.disassemble(),"Actual successful retry restores both hand owners"))
                .thenWaitUntil(()->assertRestored(h,actual)).thenExecute(actual.session::close).thenSucceed();
    }
    @GameTest(maxTicks=120) public void unownedNativeClockworkCannotAwardAnUnrelatedNearbyPlayer(GameTestHelper h) {
        var session=player(h);var player=session.player();var holder=advancement(h,"clockwork_bearing");
        h.setBlock(P,AllBlocks.CLOCKWORK_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP));hands(h);
        var bearing=h.getBlockEntity(P,ClockworkBearingBlockEntity.class);
        h.assertTrue(bearing.advancementOwner()==null,"Actual world-set bearing has no invented placed-by owner before capture");
        h.assertFalse(player.getAdvancements().getOrStartProgress(holder).isDone(),"Unrelated native player has no device award before actual capture");
        h.startSequence().thenWaitUntil(()->{
            // Native query visibility includes the complete actual +/-32-block glue union at random chunk seams.
            // Readiness is observed only: no tickets, visibility mutations, fake flags or replaced owners.
            var checked=new java.util.HashSet<Long>();
            try{for(var relative:java.util.List.of(P,H,M)){var pos=h.absolutePos(relative);com.simibubi.create.foundation.utility.LoadedEntityAccess.requireReady(h.getLevel(),pos);com.simibubi.create.foundation.utility.LoadedEntityAccess.requireGlueQueriesReady(h.getLevel(),pos,checked);}}
            catch(com.simibubi.create.content.contraptions.AssemblyException pending){throw h.assertionException("Actual source/glue entity query visibility is pending at "+pending.getPosition()+": "+pending.getMessage());}
        }).thenExecute(()->h.useBlock(P,player)).thenWaitUntil(()->{
            h.assertTrue(bearing.isRunning(),"Natural native empty-hand dispatch must actually capture the unowned clock: "+bearing.getLastAssemblyException());
            h.assertFalse(bearing.isRecovering(),"Actual unowned paired capture completes rather than retaining a partial owner");
            h.assertTrue(bearing.getHourHand()!=null&&bearing.getMinuteHand()!=null,"Both real accepted native hand owners must exist");
            h.assertTrue(h.getBlockState(H).isAir()&&h.getBlockState(M).isAir(),"Both original physical hands actually leave world custody");
            h.assertTrue(bearing.advancementOwner()==null,"Accepted capture cannot invent a placed-by owner from nearby users");
            h.assertFalse(player.getAdvancements().getOrStartProgress(holder).isDone(),"Actual unowned capture cannot award the unrelated nearby native player");
        }).thenExecute(()->h.assertTrue(bearing.disassemble(),"Actual unowned body restoration begins"))
                .thenWaitUntil(()->assertRestored(h,new Fixture(player,bearing,ItemStack.EMPTY,session)))
                .thenExecute(()->{h.assertTrue(bearing.advancementOwner()==null&&!player.getAdvancements().getOrStartProgress(holder).isDone(),"Actual complete cargo restoration retains null ownership and no unrelated source award");session.close();}).thenSucceed();
    }
    private static HauntedBellBlockEntity bell(GameTestHelper h) {
        h.setBlock(P.below(),Blocks.STONE);h.setBlock(P,AllBlocks.HAUNTED_BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT,BellAttachType.FLOOR).setValue(BellBlock.FACING,Direction.NORTH));
        return h.getBlockEntity(P,HauntedBellBlockEntity.class);
    }
    private static boolean use(GameTestHelper h,ServerPlayer player,Direction side) {
        var pos=h.absolutePos(P);return h.getBlockState(P).useWithoutItem(h.getLevel(),player,new BlockHitResult(Vec3.atCenterOf(pos),side,pos,false)).consumesAction();
    }
    @GameTest public void genuineNativeHauntedBellHitAwardsOnlyOnAnAcceptedSourceRing(GameTestHelper h) {
        var bell=bell(h);var session=player(h);var player=session.player();var holder=advancement(h,"haunted_bell");
        h.assertTrue(!use(h,player,Direction.UP)&&!bell.isRinging&&!player.getAdvancements().getOrStartProgress(holder).isDone(),"Actual invalid vertical hit cannot ring or grant progress");
        h.assertTrue(use(h,player,Direction.NORTH)&&bell.isRinging&&bell.ringingTicks==0&&player.getAdvancements().getOrStartProgress(holder).isDone(),"Actual native valid empty-hand interaction reaches real ring/pulse and original registered criterion");session.close();h.succeed();
    }
    @GameTest(maxTicks=90) public void physicalRedstoneAndRejectedCooldownDoNotAwardPlayerUntilNaturalRecharge(GameTestHelper h) {
        var bell=bell(h);var session=player(h);var player=session.player();var holder=advancement(h,"haunted_bell");h.setBlock(P.east(),Blocks.REDSTONE_BLOCK);
        h.assertTrue(bell.isRinging&&!player.getAdvancements().getOrStartProgress(holder).isDone(),"Actual physical redstone ring has no manufactured player award");
        h.assertTrue(!use(h,player,Direction.NORTH)&&!player.getAdvancements().getOrStartProgress(holder).isDone(),"Original native cooldown rejection cannot grant progress");
        h.runAfterDelay(66,()->{h.assertTrue(use(h,player,Direction.NORTH)&&bell.ringingTicks==0&&player.getAdvancements().getOrStartProgress(holder).isDone(),"Only a genuine later accepted native player ring after natural server recharge earns the original award");session.close();h.succeed();});
    }
}
