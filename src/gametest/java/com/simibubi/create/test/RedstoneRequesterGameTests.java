package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.packagerLink.*;
import com.simibubi.create.content.logistics.redstoneRequester.*;
import com.simibubi.create.content.logistics.stockTicker.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;

public class RedstoneRequesterGameTests {
    private static final BlockPos S=new BlockPos(2,2,2),P=new BlockPos(5,2,5);
    private record Station(PackagerBlockEntity packager,BarrelBlockEntity barrel,UUID network) {}
    private static Station station(GameTestHelper h,int stock) {
        var id=UUID.randomUUID();h.setBlock(S.north(),Blocks.BARREL);var barrel=h.getBlockEntity(S.north(),BarrelBlockEntity.class);barrel.setItem(0,new ItemStack(Items.APPLE,stock));
        h.setBlock(S,AllBlocks.PACKAGER.defaultBlockState().setValue(PackagerBlock.FACING,Direction.SOUTH));h.setBlock(S.above(),AllBlocks.STOCK_LINK);var link=h.getBlockEntity(S.above(),PackagerLinkBlockEntity.class);link.behaviour.setFrequency(id);link.initialize();return new Station(h.getBlockEntity(S,PackagerBlockEntity.class),barrel,id);
    }
    private static RedstoneRequesterBlockEntity requester(GameTestHelper h,UUID id,PackageOrderWithCrafts order) { h.setBlock(P,AllBlocks.REDSTONE_REQUESTER);var requester=h.getBlockEntity(P,RedstoneRequesterBlockEntity.class);requester.behaviour.setFrequency(id);requester.encodedRequest=order;requester.encodedTargetAdress="Requester";return requester; }
    private static PackageOrderWithCrafts order(int apples) { return PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.APPLE),apples))); }
    private static RedstoneRequesterMenu menu(GameTestHelper h,RedstoneRequesterBlockEntity requester,net.minecraft.world.entity.player.Player player) { player.setPos(Vec3.atCenterOf(requester.getBlockPos()).add(0,0,1));return new RedstoneRequesterMenu(12,player.getInventory(),new RedstoneRequesterMenu.Opening(requester.getBlockPos(),requester.getUpdateTag(h.getLevel().registryAccess()))); }
    private static List<Integer> amounts(int first,int second) { return List.of(first,second,1,1,1,1,1,1,1); }

    @GameTest public void risingEdgeOrdersOnceAndFallingEdgeResetsComparator(GameTestHelper h) {
        var station=station(h,20);var requester=requester(h,station.network,order(6));h.setBlock(P.east(),Blocks.REDSTONE_BLOCK);
        h.assertValueEqual(station.barrel.getItem(0).getCount(),14,"Rising edge creates one order");h.assertValueEqual(PackageItem.getAddress(station.packager.heldBox),"Requester","Encoded address");
        for(int i=0;i<10;i++)requester.onRedstonePowerChanged();h.assertValueEqual(station.barrel.getItem(0).getCount(),14,"Steady power does not repeat");
        h.assertValueEqual(requester.getBlockState().getAnalogOutputSignal(h.getLevel(),requester.getBlockPos(),Direction.NORTH),15,"Accepted trigger comparator");
        h.setBlock(P.east(),Blocks.AIR);h.assertFalse(requester.lastRequestSucceeded||requester.isRedstonePowered(),"Falling edge clears result");h.assertValueEqual(requester.getBlockState().getAnalogOutputSignal(h.getLevel(),requester.getBlockPos(),Direction.NORTH),0,"Comparator reset");h.succeed();
    }
    @GameTest public void fullRequestDoesNotExtractAnythingWhenOneIngredientIsMissing(GameTestHelper h) {
        var station=station(h,20);var requester=requester(h,station.network,PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.APPLE),3),new BigItemStack(new ItemStack(Items.DIAMOND),1))));requester.triggerRequest();
        h.assertValueEqual(station.barrel.getItem(0).getCount(),20,"No partial extraction");h.assertTrue(station.packager.heldBox.isEmpty(),"No incomplete box");h.assertFalse(requester.lastRequestSucceeded,"Denied request");h.succeed();
    }
    @GameTest public void fullRequestConsolidatesRepeatedItemLinesBeforeCheckingStock(GameTestHelper h) {
        var station=station(h,5);var requester=requester(h,station.network,PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.APPLE),3),new BigItemStack(new ItemStack(Items.APPLE),3))));requester.triggerRequest();h.assertValueEqual(station.barrel.getItem(0).getCount(),5,"Combined six cannot use five");h.assertFalse(requester.lastRequestSucceeded,"Duplicate ingredient shortage");h.succeed();
    }
    @GameTest public void partialRequestSendsAvailableStockAndPreservesSourceComparatorSemantics(GameTestHelper h) {
        var station=station(h,4);var requester=requester(h,station.network,PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.APPLE),8),new BigItemStack(new ItemStack(Items.DIAMOND),1))));requester.allowPartialRequests=true;requester.triggerRequest();
        h.assertTrue(station.barrel.getItem(0).isEmpty(),"Available four sent");h.assertValueEqual(PackageItem.getContents(station.packager.heldBox).stream().mapToInt(ItemStack::getCount).sum(),4,"No invented remainder");h.assertTrue(requester.lastRequestSucceeded,"Partial trigger accepted even when no complete line existed");h.succeed();
    }
    @GameTest public void emptyAndUnavailablePartialOrdersNeverCreateItems(GameTestHelper h) {
        var station=station(h,0);var requester=requester(h,station.network,PackageOrderWithCrafts.empty());requester.triggerRequest();h.assertFalse(requester.lastRequestSucceeded,"Empty order ignored");requester.encodedRequest=order(4);requester.allowPartialRequests=true;requester.triggerRequest();h.assertTrue(requester.lastRequestSucceeded,"Source records a nonempty partial trigger as accepted");h.assertTrue(station.packager.heldBox.isEmpty(),"No empty box manufactured");h.succeed();
    }
    @GameTest public void redstoneBusyThresholdPreventsExtractionWithoutChangingAcceptedTriggerSemantics(GameTestHelper h) {
        var station=station(h,20);for(int i=0;i<20;i++)station.packager.queuedExitingPackages.add(new BigItemStack(PackageItem.containing(List.of(new ItemStack(Items.CARROT))),1));
        var requester=requester(h,station.network,order(4));requester.triggerRequest();h.assertValueEqual(station.barrel.getItem(0).getCount(),20,"REDSTONE queue threshold blocks dispatch");h.assertTrue(requester.lastRequestSucceeded,"Original accepted-trigger comparator semantics");h.succeed();
    }
    @GameTest public void previewTransactionCannotDispatchARequest(GameTestHelper h) {
        var station=station(h,20);var requester=requester(h,station.network,order(4));try(var tx=Transaction.openOuter()){requester.triggerRequest();h.assertValueEqual(station.barrel.getItem(0).getCount(),20,"No nested dispatch");}h.assertFalse(requester.lastRequestSucceeded,"Preview cannot accept a trigger");h.succeed();
    }
    @GameTest public void requestPreservesMechanicalCraftingContextInItsParcel(GameTestHelper h) {
        var station=station(h,20);var recipe=PackageOrderWithCrafts.singleRecipe(List.of(new BigItemStack(new ItemStack(Items.APPLE),2))).orderedCrafts();var requester=requester(h,station.network,new PackageOrderWithCrafts(new PackageOrder(order(2).stacks()),recipe));requester.triggerRequest();var context=PackageItem.getOrderContext(station.packager.heldBox);h.assertTrue(context!=null&&context.orderedStacksMatchOrderedRecipes(),"Recipe context carried in actual box");h.succeed();
    }
    @GameTest public void reloadOfPoweredRequesterDoesNotRepeatTheSameEdge(GameTestHelper h) {
        var station=station(h,20);var requester=requester(h,station.network,order(4));h.setBlock(P.east(),Blocks.REDSTONE_BLOCK);var saved=requester.saveWithFullMetadata(h.getLevel().registryAccess());var restored=(RedstoneRequesterBlockEntity)BlockEntity.loadStatic(requester.getBlockPos(),requester.getBlockState(),saved,h.getLevel().registryAccess());restored.setLevel(h.getLevel());restored.onRedstonePowerChanged();h.assertTrue(restored.lastRequestSucceeded&&restored.isRedstonePowered(),"Result and edge survive reload");h.assertValueEqual(station.barrel.getItem(0).getCount(),16,"No second request on loaded power");h.assertValueEqual(restored.behaviour.freqId,station.network,"Network survives");h.assertValueEqual(restored.encodedTargetAdress,"Requester","Address survives");h.succeed();
    }
    @GameTest public void nativeMenuCopiesRepeatedGhostsAndNeverConsumesPlayerInventory(GameTestHelper h) {
        var requester=requester(h,UUID.randomUUID(),PackageOrderWithCrafts.empty());var player=h.makeMockPlayer(GameType.SURVIVAL);player.getInventory().setItem(9,new ItemStack(Items.APPLE,12));var menu=menu(h,requester,player);menu.clicked(9,0,ContainerInput.QUICK_MOVE,player);menu.clicked(9,0,ContainerInput.QUICK_MOVE,player);
        h.assertValueEqual(player.getInventory().getItem(9).getCount(),12,"Real stack retained");h.assertValueEqual(requester.encodedRequest.stacks().size(),2,"Repeated samples allowed");menu.apply(player,new RedstoneRequesterMenu.Update(12,"Menu",true,amounts(7,4)));menu.clicked(0,1,ContainerInput.PICKUP,player);h.assertValueEqual(requester.encodedRequest.stacks().size(),1,"Cleared one ghost");h.assertValueEqual(requester.encodedRequest.stacks().getFirst().count,4,"Later slot keeps its own count after compaction");h.assertTrue(requester.allowPartialRequests,"Partial setting applied");h.succeed();
    }
    @GameTest public void menuRejectsInvalidRangesRemotePlayersAndReplacedBlockEntities(GameTestHelper h) {
        var requester=requester(h,UUID.randomUUID(),order(2));var player=h.makeMockPlayer(GameType.SURVIVAL);var menu=menu(h,requester,player);h.assertFalse(menu.apply(player,new RedstoneRequesterMenu.Update(12,"Bad",false,amounts(257,1))),"256 maximum");h.assertFalse(menu.apply(player,new RedstoneRequesterMenu.Update(12,"x".repeat(26),false,amounts(2,1))),"Address length");h.assertFalse(menu.apply(player,new RedstoneRequesterMenu.Update(12,"Bad",false,List.of(2))),"Nine fields required");player.setPos(player.position().add(20,0,0));h.assertFalse(menu.apply(player,new RedstoneRequesterMenu.Update(12,"Remote",false,amounts(2,1))),"Out-of-reach rejected");player.setPos(Vec3.atCenterOf(requester.getBlockPos()));h.setBlock(P,Blocks.STONE);h.assertFalse(menu.apply(player,new RedstoneRequesterMenu.Update(12,"Stale",false,amounts(2,1))),"Removed owner rejected");h.succeed();
    }
    @GameTest public void lockedNetworkRejectsNonOwnerMenuEdits(GameTestHelper h) {
        var station=station(h,8);var requester=requester(h,station.network,order(2));var owner=h.makeMockPlayer(GameType.SURVIVAL);var other=h.makeMockPlayer(GameType.SURVIVAL);var manager=GlobalLogisticsManager.get(h.getLevel());manager.logisticsNetworks.get(station.network).owner=owner.getUUID();manager.setLocked(station.network,owner,true);var menu=menu(h,requester,other);h.assertFalse(menu.apply(other,new RedstoneRequesterMenu.Update(12,"Denied",false,amounts(3,1))),"Network lock enforced");h.assertValueEqual(requester.encodedTargetAdress,"Requester","Denied edit unchanged");h.succeed();
    }
    @GameTest public void menuRetainsMatchingCraftsAndDropsContextWhenAmountsChange(GameTestHelper h) {
        var crafts=PackageOrderWithCrafts.singleRecipe(List.of(new BigItemStack(new ItemStack(Items.APPLE),2))).orderedCrafts();var requester=requester(h,UUID.randomUUID(),new PackageOrderWithCrafts(new PackageOrder(order(2).stacks()),crafts));var player=h.makeMockPlayer(GameType.SURVIVAL);var menu=menu(h,requester,player);menu.apply(player,new RedstoneRequesterMenu.Update(12,"Craft",false,amounts(2,1)));h.assertFalse(requester.encodedRequest.orderedCrafts().isEmpty(),"Matching context retained");menu.apply(player,new RedstoneRequesterMenu.Update(12,"Craft",false,amounts(3,1)));h.assertTrue(requester.encodedRequest.orderedCrafts().isEmpty(),"Inconsistent recipe removed");h.succeed();
    }
    @GameTest public void programmedNativeItemPlacesNetworkAndComponentSensitiveOrderInSurvival(GameTestHelper h) {
        var id=UUID.randomUUID();var stack=new ItemStack(AllBlocks.REDSTONE_REQUESTER,2);var sample=new ItemStack(Items.APPLE);sample.set(DataComponents.CUSTOM_NAME,Component.literal("Exact"));var order=PackageOrderWithCrafts.simple(List.of(new BigItemStack(sample,7)));h.setBlock(S,AllBlocks.REDSTONE_REQUESTER);var origin=h.getBlockEntity(S,RedstoneRequesterBlockEntity.class);origin.behaviour.setFrequency(id);var programmer=h.makeMockPlayer(GameType.SURVIVAL);programmer.setItemInHand(InteractionHand.MAIN_HAND,stack);RedstoneRequesterBlock.programRequester(programmer,origin,order,"Encoded");
        var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);var encoded=ItemStack.CODEC.encodeStart(ops,stack).getOrThrow();stack=ItemStack.CODEC.parse(ops,encoded).getOrThrow();h.setBlock(P.below(),Blocks.STONE);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,stack);var hit=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P.below())).add(0,.5,0),Direction.UP,h.absolutePos(P.below()),false);h.assertTrue(((BlockItem)stack.getItem()).place(new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,hit))).consumesAction(),"Programmed survival placement");var requester=h.getBlockEntity(P,RedstoneRequesterBlockEntity.class);h.assertValueEqual(requester.behaviour.freqId,id,"Correct native typed BE frequency");h.assertTrue(ItemStack.isSameItemSameComponents(requester.encodedRequest.stacks().getFirst().stack,sample),"Sample components survive item persistence and placement");h.assertValueEqual(requester.encodedRequest.stacks().getFirst().count,7,"Encoded count");h.assertValueEqual(stack.getCount(),1,"One requester consumed");h.succeed();
    }
    @GameTest public void autoRequestOriginValidationPreservesSourceRangeAndDimensionRules(GameTestHelper h) {
        var stack=new ItemStack(AllBlocks.REDSTONE_REQUESTER);new AutoRequestData(order(3),"Range",h.absolutePos(P).east(127),h.getLevel().dimension().identifier().toString(),false).writeToItem(BlockPos.ZERO,stack);h.assertTrue(AutoRequestData.readFromItem(h.getLevel(),null,h.absolutePos(P),stack).isValid(),"127 blocks valid");h.assertFalse(AutoRequestData.readFromItem(h.getLevel(),null,h.absolutePos(P).west(),stack).isValid(),"128 blocks outside strict range");new AutoRequestData(order(3),"Other",h.absolutePos(P),"minecraft:the_nether",false).writeToItem(BlockPos.ZERO,stack);h.assertFalse(AutoRequestData.readFromItem(h.getLevel(),null,h.absolutePos(P),stack).isValid(),"Different dimension rejected");h.succeed();
    }
    @GameTest public void clearingFrequencyPreservesTheProgramForRetuning(GameTestHelper h) {
        var stack=new ItemStack(AllBlocks.REDSTONE_REQUESTER);LogisticallyLinkedBlockItem.assignFrequency(stack,null,UUID.randomUUID());new AutoRequestData(order(2),"Clear",BlockPos.ZERO,"minecraft:overworld",false).writeToItem(BlockPos.ZERO,stack);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,stack);stack.getItem().use(h.getLevel(),player,InteractionHand.MAIN_HAND);h.assertFalse(stack.has(DataComponents.BLOCK_ENTITY_DATA),"Connection cleared");h.assertTrue(stack.has(AllDataComponents.AUTO_REQUEST_DATA),"Source keeps the separate encoded program");var next=UUID.randomUUID();LogisticallyLinkedBlockItem.assignFrequency(stack,null,next);h.assertValueEqual(LogisticallyLinkedBlockItem.networkFromStack(stack),next,"New connection selected");h.assertValueEqual(stack.get(AllDataComponents.AUTO_REQUEST_DATA).encodedRequest().stacks().getFirst().count,2,"Program retained when retuning");h.succeed();
    }
    @GameTest public void sourceWeakPowerRuleDoesNotRelaySignalThroughTheRequester(GameTestHelper h) {
        var requester=requester(h,UUID.randomUUID(),PackageOrderWithCrafts.empty());h.setBlock(P.east(),Blocks.REDSTONE_BLOCK);
        h.assertTrue(requester.isRedstonePowered(),"Requester receives adjacent power");
        h.assertFalse(h.getLevel().hasNeighborSignal(h.absolutePos(P.west())),"Requester does not conduct weak power into the opposite neighbor");h.succeed();
    }
    @GameTest public void loadedSignalViewStillReceivesPowerThroughAnOrdinarySolidBlock(GameTestHelper h) {
        var requester=requester(h,UUID.randomUUID(),PackageOrderWithCrafts.empty());h.setBlock(P.east(),Blocks.STONE);h.setBlock(P.east(2),Blocks.LEVER.defaultBlockState().setValue(LeverBlock.POWERED,true).setValue(LeverBlock.FACE,net.minecraft.world.level.block.state.properties.AttachFace.WALL).setValue(LeverBlock.FACING,Direction.EAST));h.assertTrue(h.getLevel().hasNeighborSignal(requester.getBlockPos()),"Vanilla fixture supplies strong power");requester.onRedstonePowerChanged();
        h.assertTrue(requester.isRedstonePowered(),"Vanilla strong power through a solid neighbor is preserved");h.succeed();
    }
    @GameTest public void openingEditorPreservesLargerOrdersEncodedByStockTicker(GameTestHelper h) {
        var requester=requester(h,UUID.randomUUID(),order(1000));var player=h.makeMockPlayer(GameType.SURVIVAL);var menu=menu(h,requester,player);
        h.assertValueEqual(menu.amount(0),1000,"Encoded amount not clamped on open");h.assertTrue(menu.apply(player,new RedstoneRequesterMenu.Update(12,"Large",false,amounts(1000,1))),"Unchanged encoded amount retained");h.assertValueEqual(requester.encodedRequest.stacks().getFirst().count,1000,"Closing editor does not truncate a large order");h.succeed();
    }
    @GameTest public void wrenchRotatesHorizontalAxisWithoutLosingTheProgramAndRetrievesOneBlock(GameTestHelper h) {
        var requester=requester(h,UUID.randomUUID(),order(7));var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.WRENCH));
        var hit=new BlockHitResult(Vec3.atCenterOf(requester.getBlockPos()).add(0,.5,0),Direction.UP,requester.getBlockPos(),false);var use=new UseOnContext(player,InteractionHand.MAIN_HAND,hit);
        h.assertTrue(AllItems.WRENCH.useOn(use).consumesAction(),"Wrench accepted");h.assertValueEqual(h.getBlockState(P).getValue(RedstoneRequesterBlock.AXIS),Direction.Axis.Z,"Top face rotates horizontal axis");h.assertValueEqual(h.getBlockEntity(P,RedstoneRequesterBlockEntity.class).encodedRequest.stacks().getFirst().count,7,"Program survives rotation");
        player.setShiftKeyDown(true);AllItems.WRENCH.useOn(use);h.assertTrue(h.getBlockState(P).isAir(),"Sneak wrench removes block");int returned=0;for(int i=0;i<player.getInventory().getContainerSize();i++)if(player.getInventory().getItem(i).is(AllBlocks.REDSTONE_REQUESTER.asItem()))returned+=player.getInventory().getItem(i).getCount();h.assertValueEqual(returned,1,"Exactly one requester returned");h.succeed();
    }
}
