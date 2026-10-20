package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.redstone.link.*;
import com.simibubi.create.content.redstone.link.controller.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;

public class LinkedControllerGameTests{
    private static final BlockPos P=new BlockPos(4,4,4);
    private static ServerPlayer player(GameTestHelper h){var player=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);player.setPos(Vec3.atBottomCenterOf(h.absolutePos(P)).add(0,-1,1));player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.LINKED_CONTROLLER));return player;}
    private static LinkedControllerMenu menu(ServerPlayer player){return new LinkedControllerMenu(7,player.getInventory(),player.getMainHandItem(),player.getInventory().getSelectedSlot());}
    private static RedstoneLinkBlockEntity link(GameTestHelper h,boolean receiver){
        h.setBlock(P.below(),Blocks.STONE);h.setBlock(P,AllBlocks.REDSTONE_LINK.defaultBlockState().setValue(RedstoneLinkBlock.FACING,Direction.UP).setValue(RedstoneLinkBlock.RECEIVER,receiver));var link=h.getBlockEntity(P,RedstoneLinkBlockEntity.class);link.getLink().setFrequency(true,RedstoneLinkGameTests.sample(h,true));link.getLink().setFrequency(false,RedstoneLinkGameTests.sample(h,false));link.revalidateNetwork();return link;
    }
    private static void bindController(GameTestHelper h,ItemStack controller,int button){var items=LinkedControllerItem.getFrequencyItems(controller);items.set(button*2,RedstoneLinkGameTests.sample(h,true));items.set(button*2+1,RedstoneLinkGameTests.sample(h,false));LinkedControllerItem.setFrequencyItems(controller,items);}
    @GameTest public void ghostMenuCopiesTwelveSamplesWithoutConsumingCursorOrInventory(GameTestHelper h){
        var player=player(h);var menu=menu(player);var sample=new ItemStack(Items.DIAMOND,21);sample.set(DataComponents.CUSTOM_NAME,Component.literal("Ghost display"));menu.setCarried(sample);
        for(int slot=36;slot<48;slot++)menu.clicked(slot,0,ContainerInput.PICKUP,player);
        h.assertValueEqual(menu.getCarried().getCount(),21,"Cursor keeps all real diamonds");var items=LinkedControllerItem.getFrequencyItems(player.getMainHandItem());
        h.assertTrue(items.size()==12&&items.stream().allMatch(stack->stack.getCount()==1&&stack.has(DataComponents.CUSTOM_NAME)),"Every button has two independent component-bearing ghosts");
        menu.setCarried(ItemStack.EMPTY);menu.clicked(36,0,ContainerInput.PICKUP,player);h.assertTrue(menu.getCarried().isEmpty()&&menu.getSlot(36).getItem().isEmpty(),"Ghost cannot be extracted");h.succeed();
    }
    @GameTest public void repeatedSamplesAreAllowedAndClearIsServerAuthoritative(GameTestHelper h){
        var player=player(h);player.getInventory().setItem(9,new ItemStack(Items.EMERALD,11));var menu=menu(player);menu.quickMoveStack(player,0);menu.quickMoveStack(player,0);
        h.assertTrue(menu.getSlot(36).getItem().is(Items.EMERALD)&&menu.getSlot(37).getItem().is(Items.EMERALD)&&player.getInventory().getItem(9).getCount()==11,"Repeats copied, original stack untouched");
        h.assertFalse(menu.clickMenuButton(player,99),"Unknown clear action refused");h.assertTrue(menu.clickMenuButton(player,0)&&LinkedControllerItem.getFrequencyItems(player.getMainHandItem()).stream().allMatch(ItemStack::isEmpty),"Twelve ghosts cleared, no real item drop");h.succeed();
    }
    @GameTest public void controllerCannotBeSwappedOutOfItsOwnOpenMenu(GameTestHelper h){
        var player=player(h);var original=player.getMainHandItem();var menu=menu(player);int slot=player.getInventory().getSelectedSlot();
        menu.clicked(27+slot,0,ContainerInput.PICKUP,player);menu.clicked(0,slot,ContainerInput.SWAP,player);h.assertTrue(player.getMainHandItem()==original&&menu.getCarried().isEmpty(),"Owner slot protected");
        player.setItemInHand(InteractionHand.MAIN_HAND,original.copy());h.assertFalse(menu.stillValid(player),"Same-valued replacement has a different owner");h.assertFalse(menu.clickMenuButton(player,0),"Stale menu cannot alter replacement");h.succeed();
    }
    @GameTest public void componentCodecRetainsAllSixButtonsAndTheirOrder(GameTestHelper h){
        var player=player(h);var controller=player.getMainHandItem();for(int button=0;button<6;button++)bindController(h,controller,button);
        var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{ItemStack.STREAM_CODEC.encode(buffer,controller);var decoded=ItemStack.STREAM_CODEC.decode(buffer);for(int button=0;button<6;button++)h.assertTrue(LinkedControllerItem.toFrequency(decoded,button).equals(LinkedControllerItem.toFrequency(controller,button)),"Button pair survives native registry stream");h.assertValueEqual(buffer.readableBytes(),0,"No trailing component bytes");}finally{buffer.release();}h.succeed();
    }
    @GameTest public void bindingReadsRealLoadedLinkAndCopiesOnlyTheRequestedButton(GameTestHelper h){
        var link=link(h,false);var player=player(h);h.assertTrue(LinkedControllerPackets.bind(player,new LinkedControllerPackets.Bind(5,h.absolutePos(P))),"Native reach/held-controller binding");
        h.assertTrue(LinkedControllerItem.toFrequency(player.getMainHandItem(),5).equals(link.getLink().getNetworkKey())&&LinkedControllerItem.getFrequencyItems(player.getMainHandItem()).getFirst().isEmpty(),"Sixth button copied, first untouched");h.succeed();
    }
    @GameTest public void invalidBindingNeverLoadsTheRequestedChunkOrModifiesComponents(GameTestHelper h){
        var player=player(h);var far=new BlockPos(2400000,70,2400000);h.assertFalse(LinkedControllerPackets.bind(player,new LinkedControllerPackets.Bind(-1,far)),"Negative button");h.assertFalse(LinkedControllerPackets.bind(player,new LinkedControllerPackets.Bind(6,far)),"Upper bound");h.assertFalse(LinkedControllerPackets.bind(player,new LinkedControllerPackets.Bind(0,far)),"Far/unloaded link");
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null&&!player.getMainHandItem().has(AllDataComponents.LINKED_CONTROLLER_ITEMS),"Rejected packets leave both chunks and item untouched");h.succeed();
    }
    @GameTest public void controllerSelectionUsesMainHandBeforeOffhand(GameTestHelper h){
        var player=player(h);var main=player.getMainHandItem();var off=main.copy();player.setItemInHand(InteractionHand.OFF_HAND,off);h.assertTrue(LinkedControllerItem.heldController(player)==main,"Main hand priority");player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));h.assertTrue(LinkedControllerItem.heldController(player)==off,"Actual offhand fallback");h.succeed();
    }
    @GameTest public void pressedButtonsPowerRealReceiverAndReleaseOnTheNextWorldTick(GameTestHelper h){
        var receiver=link(h,true);var player=player(h);bindController(h,player.getMainHandItem(),3);
        try{h.assertTrue(LinkedControllerPackets.input(player,new LinkedControllerPackets.Input(List.of(3),true,null))&&receiver.getReceivedSignal()==15,"Server derives actual bound frequency");h.assertTrue(LinkedControllerPackets.input(player,new LinkedControllerPackets.Input(List.of(3),false,null)),"Release accepted");LinkedControllerServerHandler.tick(h.getLevel());h.assertValueEqual(receiver.getReceivedSignal(),0,"Release removes source within one world tick");}finally{LinkedControllerServerHandler.releasePlayer(h.getLevel().getServer(),player.getUUID());}h.succeed();
    }
    @GameTest public void missingReleaseExpiresExactlyAtThirtyAndKeepaliveRenewsIt(GameTestHelper h){
        var receiver=link(h,true);var player=player(h);bindController(h,player.getMainHandItem(),0);var packet=new LinkedControllerPackets.Input(List.of(0),true,null);
        try{LinkedControllerPackets.input(player,packet);for(int tick=0;tick<29;tick++)LinkedControllerServerHandler.tick(h.getLevel());h.assertValueEqual(receiver.getReceivedSignal(),15,"Still active after29");LinkedControllerPackets.input(player,packet);for(int tick=0;tick<29;tick++)LinkedControllerServerHandler.tick(h.getLevel());h.assertValueEqual(receiver.getReceivedSignal(),15,"Heartbeat resets full30");LinkedControllerServerHandler.tick(h.getLevel());h.assertValueEqual(receiver.getReceivedSignal(),0,"Lost release expires at30");}finally{LinkedControllerServerHandler.releasePlayer(h.getLevel().getServer(),player.getUUID());}h.succeed();
    }
    @GameTest public void twoPlayersHaveIndependentHoldsForTheSameFrequency(GameTestHelper h){
        var receiver=link(h,true);var a=player(h);var b=player(h);bindController(h,a.getMainHandItem(),0);bindController(h,b.getMainHandItem(),0);
        try{LinkedControllerPackets.input(a,new LinkedControllerPackets.Input(List.of(0),true,null));LinkedControllerPackets.input(b,new LinkedControllerPackets.Input(List.of(0),true,null));LinkedControllerServerHandler.releasePlayer(h.getLevel().getServer(),a.getUUID());h.assertValueEqual(receiver.getReceivedSignal(),15,"Other player still owns a hold");LinkedControllerServerHandler.releasePlayer(h.getLevel().getServer(),b.getUUID());h.assertValueEqual(receiver.getReceivedSignal(),0,"Final owner release clears power");}finally{LinkedControllerServerHandler.releasePlayer(h.getLevel().getServer(),a.getUUID());LinkedControllerServerHandler.releasePlayer(h.getLevel().getServer(),b.getUUID());}h.succeed();
    }
    @GameTest public void duplicateAndOutOfRangeButtonsAreRejectedBeforeFrequencyLookup(GameTestHelper h){
        var player=player(h);h.assertFalse(LinkedControllerPackets.input(player,new LinkedControllerPackets.Input(List.of(0,0),true,null)),"Duplicate button");h.assertFalse(LinkedControllerPackets.input(player,new LinkedControllerPackets.Input(List.of(6),true,null)),"Invalid upper index");h.assertFalse(LinkedControllerPackets.input(player,new LinkedControllerPackets.Input(List.of(-1),true,null)),"Negative index");h.succeed();
    }
    @GameTest public void inputCodecBoundsTheListBeforeAllocatingArbitraryEntries(GameTestHelper h){
        var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());boolean rejected=false;
        try{buffer.writeVarInt(Integer.MAX_VALUE);try{LinkedControllerPackets.Input.CODEC.decode(buffer);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected,"Unbounded button list refused at decode");}finally{buffer.release();}h.succeed();
    }
    @GameTest public void installingControllerTransfersExactDataAndConsumesOnlyInSurvival(GameTestHelper h){
        var player=player(h);var held=player.getMainHandItem();bindController(h,held,2);h.setBlock(P,Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING,Direction.EAST));
        h.assertTrue(LecternControllerBlock.install(h.getLevel(),h.absolutePos(P),h.getBlockState(P),held,false)&&held.isEmpty(),"Survival ownership transferred");var lectern=h.getBlockEntity(P,LecternControllerBlockEntity.class);h.assertTrue(lectern.getBlockState().getValue(LecternBlock.FACING)==Direction.EAST&&LinkedControllerItem.toFrequency(lectern.getController(),2).equals(LinkedControllerItem.toFrequency(newControllerWithBinding(h,2),2)),"Facing and pair retained");
        var creative=new ItemStack(AllItems.LINKED_CONTROLLER);h.setBlock(P.east(2),Blocks.LECTERN);h.assertTrue(LecternControllerBlock.install(h.getLevel(),h.absolutePos(P.east(2)),h.getBlockState(P.east(2)),creative,true)&&creative.getCount()==1,"Creative item preserved");h.succeed();
    }
    private static ItemStack newControllerWithBinding(GameTestHelper h,int button){var controller=new ItemStack(AllItems.LINKED_CONTROLLER);bindController(h,controller,button);return controller;}
    @GameTest public void occupiedVanillaLecternDoesNotLoseItsBookToControllerInstall(GameTestHelper h){
        var player=player(h);h.setBlock(P,Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK,true));h.assertFalse(LecternControllerBlock.install(h.getLevel(),h.absolutePos(P),h.getBlockState(P),player.getMainHandItem(),false),"Book-bearing lectern refused");h.assertTrue(h.getBlockState(P).is(Blocks.LECTERN)&&player.getMainHandItem().getCount()==1,"No replacement or consumption");h.succeed();
    }
    @GameTest public void nativeLecternSaveBeforeSetLevelRetainsGhostsWithoutCreatingASecondController(GameTestHelper h){
        h.setBlock(P,AllBlocks.LECTERN_CONTROLLER);var lectern=h.getBlockEntity(P,LecternControllerBlockEntity.class);lectern.setController(newControllerWithBinding(h,5));var data=lectern.saveWithFullMetadata(h.getLevel().registryAccess());var restored=(LecternControllerBlockEntity)BlockEntity.loadStatic(h.absolutePos(P),h.getBlockState(P),data,h.getLevel().registryAccess());
        h.assertTrue(LinkedControllerItem.toFrequency(restored.getController(),5).equals(LinkedControllerItem.toFrequency(lectern.getController(),5)),"Native load preserves pair before level assignment");h.succeed();
    }
    @GameTest public void lecternAllowsOnlyOneOwnedSessionAndRejectsAnotherLocation(GameTestHelper h){
        h.setBlock(P,AllBlocks.LECTERN_CONTROLLER);var lectern=h.getBlockEntity(P,LecternControllerBlockEntity.class);var a=player(h);var b=player(h);
        try{h.assertTrue(lectern.tryStartUsing(a),"Near player claims lectern");h.assertFalse(lectern.tryStartUsing(b),"Second user refused");h.assertFalse(LinkedControllerServerHandler.claimSession(h.getLevel(),a.getUUID(),h.absolutePos(P.east())),"One user cannot claim two lecterns");lectern.tryStopUsing(b);h.assertTrue(lectern.isUsedBy(a),"Other player cannot release session");lectern.tryStopUsing(a);h.assertFalse(lectern.hasUser(),"Owner can release");h.assertFalse(lectern.tryStartUsing(b),"No reacquire in deactivation tick");}finally{LinkedControllerServerHandler.releasePlayer(h.getLevel().getServer(),a.getUUID());LinkedControllerServerHandler.releasePlayer(h.getLevel().getServer(),b.getUUID());}h.succeed();
    }
    @GameTest public void staleAndFarLecternPacketsCannotBorrowAnotherController(GameTestHelper h){
        var player=player(h);var far=new BlockPos(2500000,70,2500000);h.assertFalse(LinkedControllerPackets.input(player,new LinkedControllerPackets.Input(List.of(0),true,far)),"Unloaded lectern ignored");h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"No chunk generated by lectern input");h.setBlock(P,AllBlocks.LECTERN_CONTROLLER);h.assertFalse(LinkedControllerPackets.input(player,new LinkedControllerPackets.Input(List.of(0),true,h.absolutePos(P))),"Loaded but unowned lectern ignored");h.succeed();
    }
    @GameTest public void restoredStaleLecternCannotRevokeAPlayersNewSession(GameTestHelper h){
        h.setBlock(P,AllBlocks.LECTERN_CONTROLLER);h.setBlock(P.east(),AllBlocks.LECTERN_CONTROLLER);
        var first=h.getBlockEntity(P,LecternControllerBlockEntity.class);var second=h.getBlockEntity(P.east(),LecternControllerBlockEntity.class);var player=player(h);
        try{
            h.assertTrue(first.tryStartUsing(player),"First lectern session claimed");
            var saved=first.saveWithFullMetadata(h.getLevel().registryAccess());first.tryStopUsing(player);
            h.assertTrue(second.tryStartUsing(player),"Player moved to another lectern session");
            var stale=(LecternControllerBlockEntity)BlockEntity.loadStatic(h.absolutePos(P),h.getBlockState(P),saved,h.getLevel().registryAccess());stale.setLevel(h.getLevel());stale.stopUsing();
            h.assertTrue(second.isUsedBy(player)&&LinkedControllerServerHandler.ownsSession(h.getLevel(),player.getUUID(),h.absolutePos(P.east())),"Cleanup of saved stale user preserves the new session");
            second.tryStopUsing(player);LecternControllerBlockEntity.tick(h.getLevel(),h.absolutePos(P),h.getBlockState(P),first);
            h.assertTrue(first.tryStartUsing(player),"Live block entity owns the session at the original position");
            var staleAtSamePosition=(LecternControllerBlockEntity)BlockEntity.loadStatic(h.absolutePos(P),h.getBlockState(P),saved,h.getLevel().registryAccess());staleAtSamePosition.setLevel(h.getLevel());staleAtSamePosition.stopUsing();
            h.assertTrue(first.isUsedBy(player)&&LinkedControllerServerHandler.ownsSession(h.getLevel(),player.getUUID(),h.absolutePos(P)),"Stale handle at the same position cannot release the live owner");
        }finally{LinkedControllerServerHandler.releasePlayer(h.getLevel().getServer(),player.getUUID());}h.succeed();
    }
    @GameTest public void droppingLecternControllerHasExactlyOnePhysicalOwner(GameTestHelper h){
        h.setBlock(P,AllBlocks.LECTERN_CONTROLLER);var lectern=h.getBlockEntity(P,LecternControllerBlockEntity.class);lectern.setController(newControllerWithBinding(h,4));lectern.dropController();lectern.dropController();h.setBlock(P,Blocks.AIR);
        var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(h.absolutePos(P)).inflate(3),item->item.getItem().is(AllItems.LINKED_CONTROLLER));h.assertTrue(drops.size()==1&&drops.getFirst().getItem().getCount()==1&&LinkedControllerItem.toFrequency(drops.getFirst().getItem(),4).equals(LinkedControllerItem.toFrequency(newControllerWithBinding(h,4),4)),"Repeated drop/removal cannot duplicate component-bearing controller");h.succeed();
    }
}
