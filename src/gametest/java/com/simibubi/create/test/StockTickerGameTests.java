package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.actors.seat.*;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.*;
import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.packagerLink.*;
import com.simibubi.create.content.logistics.redstoneRequester.AutoRequestData;
import com.simibubi.create.content.logistics.stockTicker.*;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

public class StockTickerGameTests {
    private static final BlockPos T=new BlockPos(5,2,5), S=new BlockPos(2,2,2);
    private static StockTickerBlockEntity ticker(GameTestHelper h,UUID network) { h.setBlock(T,AllBlocks.STOCK_TICKER);var ticker=h.getBlockEntity(T,StockTickerBlockEntity.class);ticker.behaviour.setFrequency(network);ticker.tick();return ticker; }
    private static StockTickerMenu menu(GameTestHelper h,StockTickerBlockEntity ticker,Player player,boolean categories) { player.setPos(Vec3.atCenterOf(ticker.getBlockPos()).add(0,0,1));return new StockTickerMenu(14,player.getInventory(),new StockTickerMenu.Opening(ticker.getBlockPos(),ticker.getUpdateTag(h.getLevel().registryAccess()),categories,true,false)); }
    private static BarrelBlockEntity station(GameTestHelper h,UUID network,int amount,UUID owner) {
        h.setBlock(S.north(),Blocks.BARREL);var barrel=h.getBlockEntity(S.north(),BarrelBlockEntity.class);barrel.setItem(0,new ItemStack(Items.APPLE,amount));
        h.setBlock(S,AllBlocks.PACKAGER.defaultBlockState().setValue(PackagerBlock.FACING,Direction.SOUTH));h.setBlock(S.above(),AllBlocks.STOCK_LINK);var link=h.getBlockEntity(S.above(),PackagerLinkBlockEntity.class);link.placedBy=owner;link.behaviour.setFrequency(network);link.initialize();return barrel;
    }
    private static PackageOrderWithCrafts order(Item item,int amount) { return PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(item),amount))); }
    private static StockTickerMenu.Action action(StockTickerMenu menu,int action,int index) { return new StockTickerMenu.Action(menu.containerId,action,index,"",List.of()); }
    private static int inventoryCount(Player player,Item item) { int count=0;for(int i=0;i<player.getInventory().getContainerSize();i++)if(player.getInventory().getItem(i).is(item))count+=player.getInventory().getItem(i).getCount();return count; }

    @GameTest public void actualOrderExtractsStockAndPersistsAddressEvenForEmptyOrder(GameTestHelper h) {
        var id=UUID.randomUUID();var barrel=station(h,id,20,null);var ticker=ticker(h,id);var player=h.makeMockPlayer(GameType.SURVIVAL);var menu=menu(h,ticker,player,false);
        h.assertValueEqual(ticker.getAccurateSummary().getCountOf(new ItemStack(Items.APPLE)),20,"Stock is real");
        h.assertTrue(menu.request(player,new StockTickerMenu.Request(14,order(Items.APPLE,7),"Ticker",false)),"Order accepted");h.assertValueEqual(barrel.getItem(0).getCount(),13,"Actual source extracted");
        h.assertValueEqual(PackageItem.getAddress(h.getBlockEntity(S,PackagerBlockEntity.class).heldBox),"Ticker","Package destination");
        menu.request(player,new StockTickerMenu.Request(14,PackageOrderWithCrafts.empty(),"Remember",false));h.assertValueEqual(ticker.previouslyUsedAddress,"Remember","Empty close request remembers address");h.succeed();
    }
    @GameTest public void tickerDoesNotBecomeAnAdditionalGlobalSupplyLink(GameTestHelper h) {
        var id=UUID.randomUUID();station(h,id,8,null);var ticker=ticker(h,id);ticker.tick();
        h.assertValueEqual(ticker.getRecentSummary().getCountOf(new ItemStack(Items.APPLE)),8,"No duplicate stock");h.assertValueEqual(ticker.activeLinks,1,"Only contributing warehouse counted");
        h.assertValueEqual(GlobalLogisticsManager.get(h.getLevel()).getUnloadedLinkCount(id),0,"Ticker is not an unloaded source");h.succeed();
    }
    @GameTest public void fullAndEmptyBurnersAndOccupiedSeatsHaveCorrectKeeperSemantics(GameTestHelper h) {
        var ticker=ticker(h,UUID.randomUUID());h.assertFalse(ticker.isKeeperPresent(),"No keeper");h.setBlock(T.east(),AllBlocks.BLAZE_BURNER);h.assertFalse(ticker.isKeeperPresent(),"Empty cage cannot be a keeper");
        h.setBlock(T.east(),AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.SMOULDERING));h.assertTrue(ticker.isKeeperPresent(),"Filled burner keeper");h.setBlock(T.east(),Blocks.AIR);
        h.setBlock(T.west().below(),AllBlocks.SEATS.get(DyeColor.BLUE));var cow=h.spawnWithNoFreeWill(EntityTypes.COW,T.west().below());SeatBlock.sitDown(h.getLevel(),h.absolutePos(T.west().below()),cow);
        h.assertTrue(ticker.isKeeperPresent(),"Occupied adjacent seat one block lower");h.assertValueEqual(StockTickerInteractionHandler.getStockTickerPosition(cow),ticker.getBlockPos(),"Keeper interaction target");
        cow.stopRiding();h.assertFalse(ticker.isKeeperPresent(),"Empty vehicle is not a keeper");h.succeed();
    }
    @GameTest public void ambiguousSeatedKeeperCannotSelectEitherTicker(GameTestHelper h) {
        ticker(h,UUID.randomUUID());var seat=T.west();h.setBlock(seat,AllBlocks.SEATS.get(DyeColor.RED));var cow=h.spawnWithNoFreeWill(EntityTypes.COW,seat);SeatBlock.sitDown(h.getLevel(),h.absolutePos(seat),cow);
        h.assertValueEqual(StockTickerInteractionHandler.getStockTickerPosition(cow),h.absolutePos(T),"One adjacent ticker");h.setBlock(seat.north().above(),AllBlocks.STOCK_TICKER);
        h.assertTrue(StockTickerInteractionHandler.getStockTickerPosition(cow)==null,"Two adjacent tickers are ambiguous");h.succeed();
    }
    @GameTest public void categoriesConsumeActualFilterAndRefundExactlyOnce(GameTestHelper h) {
        var ticker=ticker(h,UUID.randomUUID());var player=h.makeMockPlayer(GameType.SURVIVAL);var menu=menu(h,ticker,player,true);h.assertTrue(menu.beginEditing(-1),"New category edit");
        var filter=FilterGameTests.filter(false,false,new ItemStack(Items.APPLE));menu.setCarried(filter);menu.clicked(0,0,ContainerInput.PICKUP,player);h.assertTrue(menu.getCarried().isEmpty(),"Filter transferred from cursor");
        h.assertTrue(menu.applyAction(player,new StockTickerMenu.Action(14,StockTickerMenu.FINISH,0,"Fruit",List.of())),"Category committed");h.assertValueEqual(ticker.categories.size(),1,"One category");h.assertValueEqual(ticker.categories.getFirst().getHoverName().getString(),"Fruit","Custom category name");
        h.assertTrue(menu.applyAction(player,action(menu,StockTickerMenu.DELETE,0)),"Delete returns actual filter");h.assertFalse(menu.applyAction(player,action(menu,StockTickerMenu.DELETE,0)),"Cannot refund deleted category");h.assertValueEqual(inventoryCount(player,AllItems.FILTER),1,"Exactly one filter refunded");h.succeed();
    }
    @GameTest public void existingCategoryEditSwapsRealItemsAndCloseReturnsEscrowToBlock(GameTestHelper h) {
        var ticker=ticker(h,UUID.randomUUID());ticker.categories.add(FilterGameTests.filter(false,false,new ItemStack(Items.APPLE)));var player=h.makeMockPlayer(GameType.SURVIVAL);var menu=menu(h,ticker,player,true);menu.beginEditing(0);
        h.assertTrue(ticker.categories.getFirst().isEmpty(),"Original removed into temporary inventory");menu.setCarried(FilterGameTests.filter(false,false,new ItemStack(Items.DIAMOND)));menu.clicked(0,0,ContainerInput.PICKUP,player);
        h.assertTrue(com.simibubi.create.content.logistics.filter.FilterItemStack.of(menu.getCarried()).test(h.getLevel(),new ItemStack(Items.APPLE)),"Old filter is now cursor-owned");menu.removed(player);
        h.assertTrue(com.simibubi.create.content.logistics.filter.FilterItemStack.of(ticker.categories.getFirst()).test(h.getLevel(),new ItemStack(Items.DIAMOND)),"New filter saved when closing");h.assertTrue(menu.proxy.isEmpty(),"Escrow cleared");h.succeed();
    }
    @GameTest public void categoryEditorExcludesConcurrentEditorWithoutDuplicatingEscrow(GameTestHelper h) {
        var ticker=ticker(h,UUID.randomUUID());ticker.categories.add(new ItemStack(AllItems.FILTER));var first=menu(h,ticker,h.makeMockPlayer(GameType.SURVIVAL),true);var second=menu(h,ticker,h.makeMockPlayer(GameType.SURVIVAL),true);
        h.assertTrue(first.beginEditing(0),"First editor holds actual filter");h.assertFalse(second.beginEditing(0),"Concurrent editor rejected");h.assertTrue(second.proxy.isEmpty(),"No second copy");first.removed(h.makeMockPlayer(GameType.SURVIVAL));h.assertTrue(second.beginEditing(0),"Next editor can take ownership after close");h.succeed();
    }
    @GameTest public void saveWhileEditorIsOpenIncludesExactlyOneFilter(GameTestHelper h) {
        var ticker=ticker(h,UUID.randomUUID());ticker.categories.add(FilterGameTests.filter(false,false,new ItemStack(Items.APPLE)));var player=h.makeMockPlayer(GameType.SURVIVAL);var menu=menu(h,ticker,player,true);menu.beginEditing(0);
        var saved=ticker.getUpdateTag(h.getLevel().registryAccess());var restored=new StockTickerBlockEntity(ticker.getBlockPos(),ticker.getBlockState());restored.setLevel(h.getLevel());restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),saved));
        h.assertValueEqual(restored.categories.size(),1,"One persisted category");h.assertTrue(restored.categories.getFirst().is(AllItems.FILTER),"Escrow item is saved");menu.removed(player);h.assertValueEqual(ticker.categories.size(),1,"Original close does not duplicate");h.succeed();
    }
    @GameTest public void breakingTickerWhileEditingDropsOneFilterAndPayments(GameTestHelper h) {
        var ticker=ticker(h,UUID.randomUUID());ticker.categories.add(new ItemStack(AllItems.FILTER));try(var tx=Transaction.openOuter()){ticker.receivedPayments.automation.insert(ItemVariant.of(Items.DIAMOND),3,tx);tx.commit();}
        var player=h.makeMockPlayer(GameType.SURVIVAL);var menu=menu(h,ticker,player,true);menu.beginEditing(0);h.setBlock(T,Blocks.AIR);menu.removed(player);
        int filters=0,diamonds=0;for(var entity:h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(T)).inflate(2))){if(entity.getItem().is(AllItems.FILTER))filters+=entity.getItem().getCount();if(entity.getItem().is(Items.DIAMOND))diamonds+=entity.getItem().getCount();}
        h.assertValueEqual(filters,1,"One filter drop");h.assertValueEqual(diamonds,3,"Real payment inventory drops");h.assertTrue(menu.proxy.isEmpty(),"No ghost duplicate remains");h.succeed();
    }
    @GameTest public void categoryOrderAndHiddenIndicesFollowFiltersWhenReorderedOrRemoved(GameTestHelper h) {
        var ticker=ticker(h,UUID.randomUUID());ticker.categories.add(FilterGameTests.filter(false,false,new ItemStack(Items.APPLE)));ticker.categories.add(FilterGameTests.filter(false,false,new ItemStack(Items.DIAMOND)));
        var player=h.makeMockPlayer(GameType.SURVIVAL);ticker.hiddenCategoriesByPlayer.put(player.getUUID(),List.of(-1,0));var menu=menu(h,ticker,player,true);menu.applyAction(player,action(menu,StockTickerMenu.DOWN,0));
        h.assertTrue(ticker.hiddenCategoriesByPlayer.get(player.getUUID()).contains(1),"Hidden filter moved with item");menu.applyAction(player,action(menu,StockTickerMenu.DELETE,0));h.assertValueEqual(ticker.hiddenCategoriesByPlayer.get(player.getUUID()),List.of(-1,0),"Indices shift after deletion");h.succeed();
    }
    @GameTest public void categoryMatchingUsesFirstMatchAndComponentSensitiveFilters(GameTestHelper h) {
        var ticker=ticker(h,UUID.randomUUID());var named=new ItemStack(Items.APPLE);named.set(DataComponents.CUSTOM_NAME,Component.literal("Special"));ticker.categories.add(FilterGameTests.filter(false,true,named));ticker.categories.add(FilterGameTests.filter(false,false,new ItemStack(Items.APPLE)));
        var groups=ticker.categorize(List.of(new BigItemStack(named,4),new BigItemStack(new ItemStack(Items.APPLE),7),new BigItemStack(new ItemStack(Items.DIAMOND),2)));
        h.assertValueEqual(groups.get(0).getFirst().count,4,"Exact component category");h.assertValueEqual(groups.get(1).getFirst().count,7,"Already assigned named stack excluded from next category");h.assertValueEqual(groups.get(2).getFirst().count,2,"Uncategorized remainder");h.succeed();
    }
    @GameTest public void transactionalPaymentsRollbackPersistAndAreRetrievedOnce(GameTestHelper h) {
        var ticker=ticker(h,UUID.randomUUID());var automation=ItemStorage.SIDED.find(h.getLevel(),ticker.getBlockPos(),Direction.NORTH);h.assertTrue(automation!=null,"Fabric sided payments available");
        try(var tx=Transaction.openOuter()){automation.insert(ItemVariant.of(Items.DIAMOND),3,tx);}h.assertFalse(ticker.hasPayments(),"Preview rolled back");try(var tx=Transaction.openOuter()){automation.insert(ItemVariant.of(Items.DIAMOND),3,tx);tx.commit();}
        var player=h.makeMockPlayer(GameType.SURVIVAL);ticker.retrievePayments(player);ticker.retrievePayments(player);h.assertValueEqual(inventoryCount(player,Items.DIAMOND),3,"Each payment retrieved once");h.assertFalse(ticker.hasPayments(),"Inventory empty after retrieval");h.succeed();
    }
    @GameTest public void requesterProgrammingUsesTickerNetworkAndPreservesLargeCounts(GameTestHelper h) {
        var ticker=ticker(h,UUID.randomUUID());var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.REDSTONE_REQUESTER));var menu=menu(h,ticker,player,false);
        h.assertTrue(menu.request(player,new StockTickerMenu.Request(14,order(Items.APPLE,1000),"Programmed",true)),"Encoded requester accepted");var data=player.getMainHandItem().get(AllDataComponents.AUTO_REQUEST_DATA);
        h.assertValueEqual(data.encodedRequest().stacks().getFirst().count,1000,"Large order retained");h.assertValueEqual(LogisticallyLinkedBlockItem.networkFromStack(player.getMainHandItem()),ticker.behaviour.freqId,"Network assigned");h.assertValueEqual(data.encodedTargetAddress(),"Programmed","Address assigned");h.succeed();
    }
    @GameTest public void staleMenusRangeAndMalformedOrdersCannotExtractItems(GameTestHelper h) {
        var id=UUID.randomUUID();var barrel=station(h,id,20,null);var ticker=ticker(h,id);var player=h.makeMockPlayer(GameType.SURVIVAL);var menu=menu(h,ticker,player,false);
        h.assertFalse(menu.request(player,new StockTickerMenu.Request(13,order(Items.APPLE,2),"Bad",false)),"Wrong menu rejected");h.assertFalse(menu.request(player,new StockTickerMenu.Request(14,order(Items.APPLE,-2),"Bad",false)),"Negative quantity rejected");
        player.setPos(Vec3.atCenterOf(ticker.getBlockPos()).add(30,0,0));h.assertFalse(menu.request(player,new StockTickerMenu.Request(14,order(Items.APPLE,2),"Far",false)),"Out of range rejected");h.assertValueEqual(barrel.getItem(0).getCount(),20,"No extraction");h.succeed();
    }
    @GameTest public void stockFragmentsPreserveLargeCountsComponentsAndEmptyCompletion(GameTestHelper h) {
        var ticker=ticker(h,UUID.randomUUID());var menu=menu(h,ticker,h.makeMockPlayer(GameType.SURVIVAL),false);var entries=new ArrayList<BigItemStack>();
        for(int i=0;i<101;i++){var sample=new ItemStack(Items.APPLE);sample.set(DataComponents.CUSTOM_NAME,Component.literal("Item "+i));entries.add(new BigItemStack(sample,i==0?BigItemStack.INF:i+1));}
        var packets=StockTickerMenu.fragment(14,1,entries);h.assertValueEqual(packets.size(),2,"100 entries per fragment");menu.receive(packets.get(1));h.assertTrue(menu.stock.isEmpty(),"Missing first fragment rejected");
        for(var packet:packets){var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());try{StockTickerMenu.Snapshot.CODEC.encode(buffer,packet);menu.receive(StockTickerMenu.Snapshot.CODEC.decode(buffer));}finally{buffer.release();}}
        h.assertValueEqual(menu.stock.size(),101,"Completed snapshot published");h.assertValueEqual(menu.stock.getFirst().count,BigItemStack.INF,"Full infinite count");h.assertValueEqual(menu.stock.getLast().stack.getHoverName().getString(),"Item 100","Item components retained");
        menu.receive(StockTickerMenu.fragment(14,2,List.of()).getFirst());h.assertTrue(menu.stock.isEmpty(),"Empty last packet clears stale stock");h.succeed();
    }
    @GameTest public void persistenceKeepsNetworkCategoriesHiddenPreferencesAddressAndPayments(GameTestHelper h) {
        var network=UUID.randomUUID();var ticker=ticker(h,network);var id=UUID.randomUUID();ticker.categories.add(FilterGameTests.filter(false,false,new ItemStack(Items.APPLE)));ticker.hiddenCategoriesByPlayer.put(id,List.of(-1,0));ticker.previouslyUsedAddress="Saved";
        try(var tx=Transaction.openOuter()){ticker.receivedPayments.automation.insert(ItemVariant.of(Items.EMERALD),9,tx);tx.commit();}
        var saved=ticker.saveWithFullMetadata(h.getLevel().registryAccess());ticker.onChunkUnloaded();h.getLevel().removeBlockEntity(ticker.getBlockPos());
        var restored=(StockTickerBlockEntity)BlockEntity.loadStatic(ticker.getBlockPos(),ticker.getBlockState(),saved,h.getLevel().registryAccess());h.getLevel().setBlockEntity(restored);
        h.assertTrue(ticker.isRemoved()&&h.getLevel().getBlockEntity(ticker.getBlockPos())==restored,"Native loader publishes one restored ticker after behaviour hydration");
        h.assertValueEqual(restored.behaviour.freqId,network,"Frequency saved");h.assertValueEqual(restored.previouslyUsedAddress,"Saved","Address saved");h.assertValueEqual(restored.hiddenCategoriesByPlayer.get(id),List.of(-1,0),"Hidden preferences saved");h.assertValueEqual(restored.categories.size(),1,"One category saved");h.assertTrue(com.simibubi.create.content.logistics.filter.FilterItemStack.of(restored.categories.getFirst()).test(h.getLevel(),new ItemStack(Items.APPLE)),"Saved category keeps its real predicate");h.assertValueEqual(restored.receivedPayments.inputs.getFirst().getAmount(),9L,"Payments saved");
        var player=h.makeMockPlayer(GameType.SURVIVAL);restored.retrievePayments(player);restored.retrievePayments(player);h.assertValueEqual(inventoryCount(player,Items.EMERALD),9,"Restored payments are retrieved exactly once");h.assertFalse(restored.hasPayments(),"Restored payment owner is empty after retrieval");h.succeed();
    }
    @GameTest public void lockedNetworkRejectsOpenMenusAndUnauthorizedLockChanges(GameTestHelper h) {
        var id=UUID.randomUUID();var owner=h.makeMockPlayer(GameType.SURVIVAL);var other=h.makeMockPlayer(GameType.SURVIVAL);var barrel=station(h,id,20,owner.getUUID());var ticker=ticker(h,id);var manager=GlobalLogisticsManager.get(h.getLevel());manager.logisticsNetworks.get(id).owner=owner.getUUID();
        var ownerMenu=menu(h,ticker,owner,false);var otherMenu=menu(h,ticker,other,false);h.assertFalse(otherMenu.applyAction(other,action(otherMenu,StockTickerMenu.LOCK,1)),"Only owner locks network");h.assertTrue(ownerMenu.applyAction(owner,action(ownerMenu,StockTickerMenu.LOCK,1)),"Owner can lock");
        h.assertFalse(otherMenu.stillValid(other),"An already open non-owner menu loses access");h.assertFalse(otherMenu.request(other,new StockTickerMenu.Request(14,order(Items.APPLE,2),"Locked",false)),"No unauthorized withdrawal");h.assertValueEqual(barrel.getItem(0).getCount(),20,"Stock retained");h.succeed();
    }
    @GameTest public void stockTickerBlockItemUsesItsOwnTypedFrequencyData(GameTestHelper h) {
        var id=UUID.randomUUID();var stack=new ItemStack(AllBlocks.STOCK_TICKER);LogisticallyLinkedBlockItem.assignFrequency(stack,null,id);
        h.assertValueEqual(stack.get(DataComponents.BLOCK_ENTITY_DATA).type(),AllBlockEntityTypes.STOCK_TICKER,"Correct native block entity type");h.assertValueEqual(LogisticallyLinkedBlockItem.networkFromStack(stack),id,"Frequency retained");h.succeed();
    }
    @GameTest public void overflowingOrdersAreRejectedAndValidCraftingContextIsRetained(GameTestHelper h) {
        h.assertFalse(StockTickerMenu.validOrder(PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.APPLE),BigItemStack.INF),new BigItemStack(new ItemStack(Items.APPLE),BigItemStack.INF)))),"Consolidated count cannot overflow");
        var id=UUID.randomUUID();station(h,id,20,null);var ticker=ticker(h,id);var player=h.makeMockPlayer(GameType.SURVIVAL);var menu=menu(h,ticker,player,false);var pattern=new ArrayList<BigItemStack>();for(int i=0;i<9;i++)pattern.add(new BigItemStack(i==4?new ItemStack(Items.APPLE):ItemStack.EMPTY,i==4?1:0));
        var order=new PackageOrderWithCrafts(order(Items.APPLE,3).orderedStacks(),List.of(new PackageOrderWithCrafts.CraftingEntry(new PackageOrder(pattern),3)));
        h.assertTrue(menu.request(player,new StockTickerMenu.Request(14,order,"Craft",false)),"Ordered craft dispatched");h.assertValueEqual(PackageItem.getOrderContext(h.getBlockEntity(S,PackagerBlockEntity.class).heldBox),order,"Native package retains exact craft context");h.succeed();
    }
}
