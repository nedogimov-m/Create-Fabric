package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.stockTicker.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;

public class StockCraftingGameTests {
    private static final Identifier STICK=Identifier.withDefaultNamespace("stick"), TABLE=Identifier.withDefaultNamespace("crafting_table"), OVERLAP=Identifier.fromNamespaceAndPath("create_test","crafting_overlap");
    private static BigItemStack item(Item item,int count){return new BigItemStack(new ItemStack(item),count);}
    private static InventorySummary summary(BigItemStack...items){var summary=new InventorySummary();for(var item:items)summary.add(item);return summary;}
    private static StockTickerCrafting.RecipePlan plan(GameTestHelper h,Identifier recipe,int count,BigItemStack...items){return StockTickerCrafting.plan(h.getLevel(),recipe,List.of(items),count,Set.of(),9);}
    @GameTest public void nativeShapedRecipeUsesSourceCenteredPositionsAndOutputMultiples(GameTestHelper h){
        var plan=plan(h,STICK,3,item(Items.OAK_PLANKS,6));h.assertValueEqual(plan.crafts(),3,"Three crafts");h.assertValueEqual(plan.result().getCount(),4,"Four sticks per craft");h.assertValueEqual(plan.materials().getFirst().count,6,"Two planks each");
        var grid=plan.patterns().getFirst().pattern().stacks();h.assertValueEqual(grid.size(),9,"Full source context");h.assertTrue(grid.get(1).stack.is(Items.OAK_PLANKS)&&grid.get(4).stack.is(Items.OAK_PLANKS),"One-wide recipe centered horizontally");h.assertTrue(grid.get(0).stack.isEmpty()&&grid.get(7).stack.isEmpty(),"Empty source positions");h.succeed();
    }
    @GameTest public void overlappingIngredientsShareFiniteStockAndUseAlternativeToAvoidFalseShortage(GameTestHelper h){
        var plan=plan(h,OVERLAP,2,item(Items.OAK_PLANKS,3),item(Items.BIRCH_PLANKS,1));h.assertValueEqual(plan.crafts(),2,"Shared oak supply can satisfy both recipes using one birch");
        h.assertValueEqual(plan.patterns().size(),2,"Different exact ingredient patterns split into batches");h.assertValueEqual(plan.materials().stream().mapToInt(entry->entry.count).sum(),4,"No double allocation");
        var order=new PackageOrderWithCrafts(new PackageOrder(plan.materials()),plan.patterns());h.assertTrue(order.orderedStacksMatchOrderedRecipes(),"Pattern totals equal ordered real materials");h.succeed();
    }
    @GameTest public void shortageNeverProducesPartialRecipeOrMutatesSummary(GameTestHelper h){
        var stock=summary(item(Items.OAK_PLANKS,1));var result=StockTickerCrafting.cart(h.getLevel(),stock,List.of(),List.of(new StockTickerCrafting.Selection(STICK,20)));
        h.assertTrue(result.order().isEmpty()&&result.selections().isEmpty(),"No incomplete craft");h.assertValueEqual(stock.getCountOf(new ItemStack(Items.OAK_PLANKS)),1,"Preview does not consume stock");h.succeed();
    }
    @GameTest public void componentVariantsRemainDistinctInMaterialsAndPatterns(GameTestHelper h){
        var first=new ItemStack(Items.OAK_PLANKS);first.set(DataComponents.CUSTOM_NAME,Component.literal("A"));var second=new ItemStack(Items.OAK_PLANKS);second.set(DataComponents.CUSTOM_NAME,Component.literal("B"));
        var result=plan(h,STICK,3,new BigItemStack(first,3),new BigItemStack(second,3));h.assertValueEqual(result.crafts(),3,"Both variants used");h.assertValueEqual(result.materials().size(),2,"Component-sensitive types retained");
        var order=new PackageOrderWithCrafts(new PackageOrder(result.materials()),result.patterns());h.assertTrue(order.orderedStacksMatchOrderedRecipes(),"Exact components retained through context");h.assertTrue(result.patterns().stream().flatMap(craft->craft.pattern().stacks().stream()).filter(entry->!entry.stack.isEmpty()).allMatch(entry->entry.stack.has(DataComponents.CUSTOM_NAME)),"No plain plank substitutes");h.succeed();
    }
    @GameTest public void sourceNineTypeLimitTrimsAlternativesBeforePlanning(GameTestHelper h){
        var items=List.of(Items.OAK_PLANKS,Items.SPRUCE_PLANKS,Items.BIRCH_PLANKS,Items.JUNGLE_PLANKS,Items.ACACIA_PLANKS,Items.DARK_OAK_PLANKS,Items.MANGROVE_PLANKS,Items.CHERRY_PLANKS,Items.PALE_OAK_PLANKS,Items.BAMBOO_PLANKS,Items.CRIMSON_PLANKS,Items.WARPED_PLANKS);
        var plan=StockTickerCrafting.plan(h.getLevel(),STICK,items.stream().map(item->item(item,64)).toList(),1000,Set.of(),9);
        h.assertTrue(plan.materials().size()<=9,"At most nine new item/component types");h.assertValueEqual(plan.crafts(),288,"Nine stacks provide 576 planks");h.succeed();
    }
    @GameTest public void infiniteStockIsPlannedWithBoundedArithmeticInsteadOfItemIteration(GameTestHelper h){
        long before=System.nanoTime();var plan=plan(h,STICK,BigItemStack.INF,item(Items.OAK_PLANKS,BigItemStack.INF));long elapsed=System.nanoTime()-before;
        h.assertValueEqual(plan.crafts(),250_000_000,"Output count fits source infinity representation");h.assertValueEqual(plan.materials().getFirst().count,500_000_000,"Exact aggregated ingredients");h.assertTrue(elapsed<2_000_000_000L,"Planning a billion units remains bounded");h.succeed();
    }
    @GameTest public void sharedMaterialAcrossDifferentRecipesCannotBeAllocatedTwice(GameTestHelper h){
        var cart=StockTickerCrafting.cart(h.getLevel(),summary(item(Items.OAK_PLANKS,6)),List.of(),List.of(new StockTickerCrafting.Selection(STICK,2),new StockTickerCrafting.Selection(TABLE,1)));
        h.assertValueEqual(cart.selections().size(),1,"Second recipe lacks four remaining planks");h.assertValueEqual(cart.order().stacks().getFirst().count,4,"Only actual accepted recipe ingredients");h.assertTrue(cart.order().orderedStacksMatchOrderedRecipes(),"Whole cart has valid context");h.succeed();
    }
    @GameTest public void manualExtrasRemainOrdinaryPackageOrderAndReserveTheirOwnStock(GameTestHelper h){
        var cart=StockTickerCrafting.cart(h.getLevel(),summary(item(Items.OAK_PLANKS,6)),List.of(item(Items.OAK_PLANKS,1)),List.of(new StockTickerCrafting.Selection(STICK,3)));
        h.assertValueEqual(cart.selections().getFirst().crafts(),2,"Manual plank reserves part of supply");h.assertValueEqual(cart.order().stacks().getFirst().count,5,"One extra and four recipe planks");h.assertTrue(cart.order().orderedCrafts().isEmpty(),"Extra raw items disable full crafting context as in source");h.succeed();
    }
    @GameTest public void catalogUsesCurrentNativeRecipesAndSkipsManualOnlyEntries(GameTestHelper h){
        var catalog=StockTickerCrafting.catalog(h.getLevel(),summary(item(Items.OAK_PLANKS,8),item(Items.STONE,64)));
        var sticks=catalog.stream().filter(choice->choice.recipe().equals(STICK)).findFirst().orElseThrow();h.assertValueEqual(sticks.available(),4,"Real stock determines craftable count");
        h.assertFalse(catalog.stream().anyMatch(choice->choice.recipe().getPath().endsWith("crafting_manual_only")),"Manual-only recipe not offered");h.assertTrue(catalog.stream().allMatch(choice->choice.grid().size()==9&&!choice.result().isEmpty()),"Every recipe has a real assembled result and nine positions");h.succeed();
    }
    @GameTest public void shapelessThreeIngredientPatternRetainsOriginalTopRowLayout(GameTestHelper h){
        var plan=plan(h,Identifier.fromNamespaceAndPath("create_test","crafting_three"),1,item(Items.APPLE,1),item(Items.DIAMOND,1),item(Items.GOLD_NUGGET,1));h.assertValueEqual(plan.crafts(),1,"Fixture craftable");var grid=plan.patterns().getFirst().pattern().stacks();
        h.assertTrue(!grid.get(0).stack.isEmpty()&&!grid.get(1).stack.isEmpty()&&!grid.get(2).stack.isEmpty()&&grid.get(3).stack.isEmpty(),"Source full shapeless row position");h.succeed();
    }
    @GameTest public void catalogFragmentsAndPreviewCodecKeepFullQuantitiesAndPatterns(GameTestHelper h){
        var plan=plan(h,STICK,2000,item(Items.OAK_PLANKS,4000));var choice=new StockTickerCrafting.Choice(STICK,plan.result(),plan.patterns().getFirst().pattern().stacks().stream().map(entry->entry.stack).toList(),plan.crafts());
        var fragments=StockTickerCraftingPackets.fragment(14,1,Collections.nCopies(33,choice));h.assertValueEqual(fragments.size(),2,"32 recipe choices per fragment");var packet=new StockTickerCraftingPackets.Preview(14,3,new PackageOrderWithCrafts(new PackageOrder(plan.materials()),plan.patterns()),List.of(new StockTickerCrafting.Selection(STICK,2000)),List.of(choice));
        var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());try{StockTickerCraftingPackets.Preview.CODEC.encode(buffer,packet);var decoded=StockTickerCraftingPackets.Preview.CODEC.decode(buffer);h.assertValueEqual(decoded.order().stacks().getFirst().count,4000,"Amounts not truncated to menu fields");h.assertTrue(decoded.order().orderedStacksMatchOrderedRecipes(),"Codec retains full crafting context");}finally{buffer.release();}h.succeed();
    }
    @GameTest public void staleMenusAndNegativeCraftCountsCannotCreateOrders(GameTestHelper h){
        var pos=new BlockPos(3,2,3);h.setBlock(pos,AllBlocks.STOCK_TICKER);var ticker=h.getBlockEntity(pos,StockTickerBlockEntity.class);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(ticker.getBlockPos()).add(0,0,1));var menu=new StockTickerMenu(14,player.getInventory(),new StockTickerMenu.Opening(ticker.getBlockPos(),ticker.getUpdateTag(h.getLevel().registryAccess()),false,false,false));
        h.assertTrue(StockTickerCraftingPackets.edit(menu,player,new StockTickerCraftingPackets.Edit(13,1,List.of(new StockTickerCrafting.Selection(STICK,1)),List.of(),false,"",false))==null,"Wrong menu");h.assertTrue(StockTickerCraftingPackets.edit(menu,player,new StockTickerCraftingPackets.Edit(14,1,List.of(new StockTickerCrafting.Selection(STICK,-1)),List.of(),false,"",false))==null,"Negative crafts");h.succeed();
    }
    @GameTest(maxTicks=120) public void realTickerOrderRepackagesIntoFullCraftAndDrivesNativeMechanicalGrid(GameTestHelper h){
        var source=new BlockPos(1,2,5);var tickerPos=new BlockPos(6,2,6);var repackPos=new BlockPos(6,2,3);var receiverPos=new BlockPos(4,3,2);var network=UUID.randomUUID();
        h.setBlock(source.north(),Blocks.BARREL);var stock=h.getBlockEntity(source.north(),net.minecraft.world.level.block.entity.BarrelBlockEntity.class);stock.setItem(0,new ItemStack(Items.OAK_PLANKS,6));
        h.setBlock(source,AllBlocks.PACKAGER.defaultBlockState().setValue(PackagerBlock.FACING,Direction.SOUTH));h.setBlock(source.above(),AllBlocks.STOCK_LINK);var link=h.getBlockEntity(source.above(),com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity.class);link.behaviour.setFrequency(network);link.initialize();
        h.setBlock(tickerPos,AllBlocks.STOCK_TICKER);var ticker=h.getBlockEntity(tickerPos,StockTickerBlockEntity.class);ticker.behaviour.setFrequency(network);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(ticker.getBlockPos()).add(0,0,1));var menu=new StockTickerMenu(14,player.getInventory(),new StockTickerMenu.Opening(ticker.getBlockPos(),ticker.getUpdateTag(h.getLevel().registryAccess()),false,false,false));
        var preview=StockTickerCraftingPackets.edit(menu,player,new StockTickerCraftingPackets.Edit(14,1,List.of(new StockTickerCrafting.Selection(STICK,3)),List.of(),false,"Craft",false));h.assertTrue(preview!=null&&preview.order().orderedStacksMatchOrderedRecipes(),"Server preview has exact craft context");h.assertValueEqual(stock.getItem(0).getCount(),6,"Preview retains source items");
        StockTickerCraftingPackets.edit(menu,player,new StockTickerCraftingPackets.Edit(14,2,List.of(new StockTickerCrafting.Selection(STICK,3)),List.of(),true,"Craft",false));h.assertTrue(stock.isEmpty(),"Real order owns all six planks");var sender=h.getBlockEntity(source,PackagerBlockEntity.class);for(int i=0;i<20;i++)PackagerBlockEntity.tick(h.getLevel(),sender.getBlockPos(),sender.getBlockState(),sender);
        var parcel=sender.heldBox.copy();try(var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){h.assertValueEqual(sender.inventory.extract(ItemVariant.of(parcel),1,tx),1L,"Move one real parcel");tx.commit();}
        h.setBlock(repackPos.north(),Blocks.BARREL);h.getBlockEntity(repackPos.north(),net.minecraft.world.level.block.entity.BarrelBlockEntity.class).setItem(0,parcel);h.setBlock(repackPos,AllBlocks.REPACKAGER.defaultBlockState().setValue(PackagerBlock.FACING,Direction.SOUTH));var repackager=h.getBlockEntity(repackPos,com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity.class);repackager.attemptToSend(null);
        h.assertValueEqual(repackager.queuedExitingPackages.stream().mapToInt(entry->entry.count).sum(),3,"Three complete craft parcels");
        var crafterState=AllBlocks.MECHANICAL_CRAFTER.defaultBlockState().setValue(com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock.HORIZONTAL_FACING,Direction.SOUTH);
        for(int y=2;y<=4;y++)for(int x=3;x<=5;x++)h.setBlock(new BlockPos(x,y,1),crafterState.setValue(com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock.POINTING,x<5?net.createmod.catnip.math.Pointing.RIGHT:y>2?net.createmod.catnip.math.Pointing.DOWN:net.createmod.catnip.math.Pointing.RIGHT));
        for(int y=2;y<=4;y++)for(int x=3;x<5;x++)com.simibubi.create.content.kinetics.crafter.ConnectedInputHandler.toggleConnection(h.getLevel(),h.absolutePos(new BlockPos(x,y,1)),h.absolutePos(new BlockPos(x+1,y,1)));
        for(int y=2;y<4;y++)com.simibubi.create.content.kinetics.crafter.ConnectedInputHandler.toggleConnection(h.getLevel(),h.absolutePos(new BlockPos(3,y,1)),h.absolutePos(new BlockPos(3,y+1,1)));
        h.setBlock(new BlockPos(2,2,1),AllBlocks.COGWHEEL.defaultBlockState().setValue(com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock.AXIS,Direction.Axis.Z));h.setBlock(new BlockPos(2,2,0),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING,Direction.SOUTH));h.getBlockEntity(new BlockPos(2,2,0),com.simibubi.create.content.kinetics.base.KineticBlockEntity.class).setGeneratedSpeed(256);h.setBlock(new BlockPos(6,2,1),AllBlocks.DEPOT);h.setBlock(receiverPos,AllBlocks.PACKAGER.defaultBlockState().setValue(PackagerBlock.FACING,Direction.SOUTH));
        var batch=repackager.queuedExitingPackages.getFirst();var craftBox=batch.stack.copy();if(--batch.count==0)repackager.queuedExitingPackages.removeFirst();
        h.runAfterDelay(2,()->h.assertTrue(h.getBlockEntity(receiverPos,PackagerBlockEntity.class).unwrapBox(craftBox,false),"Ordered native 3x3 delivery"));
        h.succeedWhen(()->{var depot=h.getBlockEntity(new BlockPos(6,2,1),com.simibubi.create.content.logistics.depot.DepotBlockEntity.class);h.assertTrue(depot.getHeldItem().is(Items.STICK)&&depot.getHeldItem().getCount()==4,"Actual mechanical crafting produced four sticks");});
    }
    @GameTest public void submissionRevalidatesStockEvenAfterEarlierPreviewInTheSameTick(GameTestHelper h){
        var source=new BlockPos(2,2,2);var pos=new BlockPos(5,2,5);var id=UUID.randomUUID();h.setBlock(source.north(),Blocks.BARREL);var barrel=h.getBlockEntity(source.north(),net.minecraft.world.level.block.entity.BarrelBlockEntity.class);barrel.setItem(0,new ItemStack(Items.OAK_PLANKS,6));h.setBlock(source,AllBlocks.PACKAGER.defaultBlockState().setValue(PackagerBlock.FACING,Direction.SOUTH));h.setBlock(source.above(),AllBlocks.STOCK_LINK);var link=h.getBlockEntity(source.above(),com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity.class);link.behaviour.setFrequency(id);link.initialize();h.setBlock(pos,AllBlocks.STOCK_TICKER);var ticker=h.getBlockEntity(pos,StockTickerBlockEntity.class);ticker.behaviour.setFrequency(id);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(ticker.getBlockPos()).add(0,0,1));var menu=new StockTickerMenu(14,player.getInventory(),new StockTickerMenu.Opening(ticker.getBlockPos(),ticker.getUpdateTag(h.getLevel().registryAccess()),false,false,false));
        var preview=StockTickerCraftingPackets.edit(menu,player,new StockTickerCraftingPackets.Edit(14,1,List.of(new StockTickerCrafting.Selection(STICK,3)),List.of(),false,"",false));h.assertValueEqual(preview.selections().getFirst().crafts(),3,"Earlier preview");barrel.setItem(0,new ItemStack(Items.OAK_PLANKS,2));
        var submitted=StockTickerCraftingPackets.edit(menu,player,new StockTickerCraftingPackets.Edit(14,2,List.of(new StockTickerCrafting.Selection(STICK,3)),List.of(),true,"Fresh",false));h.assertValueEqual(submitted.selections().getFirst().crafts(),1,"Final submission sees new source contents");h.assertTrue(barrel.isEmpty(),"Exactly two remaining planks extracted");h.succeed();
    }
    @GameTest public void typeBudgetNeverRemovesTheOnlyCandidateForARequiredSlot(GameTestHelper h){
        var result=StockTickerCrafting.plan(h.getLevel(),OVERLAP,List.of(item(Items.BIRCH_PLANKS,100),item(Items.OAK_PLANKS,2)),20,Set.of(),1);
        h.assertValueEqual(result.crafts(),1,"One-type recipe can use oak for both slots");h.assertValueEqual(result.materials().size(),1,"One material type");h.assertTrue(result.materials().getFirst().stack.is(Items.OAK_PLANKS),"Mandatory oak retained despite its smaller stock");h.succeed();
    }
    @GameTest public void editingMaterialCountRetainsPossibleRecipesAndUnassignedRawItems(GameTestHelper h){
        var stock=summary(item(Items.OAK_PLANKS,64));var requests=List.of(new StockTickerCrafting.Selection(STICK,2));
        var reduced=StockTickerCrafting.reconcile(h.getLevel(),stock,List.of(item(Items.OAK_PLANKS,3)),requests);
        h.assertValueEqual(reduced.selections().getFirst().crafts(),1,"Only impossible crafts removed");h.assertValueEqual(reduced.order().stacks().getFirst().count,3,"Edited material budget is preserved");h.assertValueEqual(reduced.manual().getFirst().count,1,"Fractional craft material remains an ordinary raw item");h.assertTrue(reduced.order().orderedCrafts().isEmpty(),"Raw leftover disables complete crafting context");
        var exact=StockTickerCrafting.reconcile(h.getLevel(),stock,List.of(item(Items.OAK_PLANKS,2)),reduced.selections());
        h.assertValueEqual(exact.selections().getFirst().crafts(),1,"Remaining recipe retained");h.assertTrue(exact.manual().isEmpty()&&exact.order().orderedStacksMatchOrderedRecipes(),"Removing the leftover restores valid crafting context");h.assertValueEqual(stock.getCountOf(new ItemStack(Items.OAK_PLANKS)),64,"Editing is only a preview");h.succeed();
    }
    @GameTest public void addingRawMaterialsDoesNotAutomaticallyIncreaseQueuedCrafts(GameTestHelper h){
        var cart=StockTickerCrafting.reconcile(h.getLevel(),summary(item(Items.OAK_PLANKS,64)),List.of(item(Items.OAK_PLANKS,8)),List.of(new StockTickerCrafting.Selection(STICK,1)));
        h.assertValueEqual(cart.selections().getFirst().crafts(),1,"Source only decreases queued recipes");h.assertValueEqual(cart.manual().getFirst().count,6,"Extra raw material is retained");h.assertValueEqual(cart.order().stacks().getFirst().count,8,"Requested eight planks retained");h.succeed();
    }
    @GameTest public void editedBudgetIsSharedAcrossDifferentRecipesInSelectionOrder(GameTestHelper h){
        var requests=List.of(new StockTickerCrafting.Selection(STICK,1),new StockTickerCrafting.Selection(TABLE,1));var stock=summary(item(Items.OAK_PLANKS,64));
        var reduced=StockTickerCrafting.reconcile(h.getLevel(),stock,List.of(item(Items.OAK_PLANKS,5)),requests);
        h.assertValueEqual(reduced.selections().size(),1,"Crafting table cannot reuse stick materials");h.assertValueEqual(reduced.manual().getFirst().count,3,"Unused planks retained");
        var exact=StockTickerCrafting.reconcile(h.getLevel(),stock,List.of(item(Items.OAK_PLANKS,6)),requests);
        h.assertValueEqual(exact.selections().size(),2,"Six planks cover both recipes");h.assertTrue(exact.order().orderedStacksMatchOrderedRecipes()&&exact.manual().isEmpty(),"Both recipe contexts exactly account for the budget");h.succeed();
    }
    @GameTest public void editedComponentsCannotBeReplacedWithOtherWarehouseVariants(GameTestHelper h){
        var named=new ItemStack(Items.OAK_PLANKS);named.set(DataComponents.CUSTOM_NAME,Component.literal("Exact budget"));
        var cart=StockTickerCrafting.reconcile(h.getLevel(),summary(new BigItemStack(named,1),item(Items.OAK_PLANKS,64)),List.of(new BigItemStack(named,2)),List.of(new StockTickerCrafting.Selection(STICK,1)));
        h.assertTrue(cart.selections().isEmpty(),"Plain warehouse planks cannot fill a named budget");h.assertValueEqual(cart.order().stacks().getFirst().count,1,"Budget clamped to exact component stock");h.assertTrue(ItemStack.isSameItemSameComponents(cart.order().stacks().getFirst().stack,named),"Named component is retained");h.succeed();
    }
    @GameTest public void stockRecoveryDoesNotRegrowRecipesReducedByEarlierMaterialEdits(GameTestHelper h){
        var reduced=StockTickerCrafting.reconcile(h.getLevel(),summary(item(Items.OAK_PLANKS,2)),List.of(item(Items.OAK_PLANKS,6)),List.of(new StockTickerCrafting.Selection(STICK,3)));
        var recovered=StockTickerCrafting.reconcile(h.getLevel(),summary(item(Items.OAK_PLANKS,64)),reduced.order().stacks(),reduced.selections());
        h.assertValueEqual(recovered.selections().getFirst().crafts(),1,"Restocking does not raise the queued count");h.assertValueEqual(recovered.order().stacks().getFirst().count,2,"Reduced exact material budget retained");h.assertTrue(recovered.order().orderedStacksMatchOrderedRecipes(),"Reduced context still valid");h.succeed();
    }
    @GameTest public void editedInfiniteBudgetKeepsRawRemainderWithoutPerItemIteration(GameTestHelper h){
        long before=System.nanoTime();var cart=StockTickerCrafting.reconcile(h.getLevel(),summary(item(Items.OAK_PLANKS,BigItemStack.INF)),List.of(item(Items.OAK_PLANKS,BigItemStack.INF)),List.of(new StockTickerCrafting.Selection(STICK,BigItemStack.INF)));
        h.assertValueEqual(cart.selections().getFirst().crafts(),250_000_000,"Output limited to INF");h.assertValueEqual(cart.manual().getFirst().count,500_000_000,"Unconsumed half-billion raw planks retained");h.assertTrue(System.nanoTime()-before<2_000_000_000L,"Edited INF planning remains bounded");h.succeed();
    }
    @GameTest public void materialEditCodecAndStalePreviewRevisionRetainBudgetAndRemainder(GameTestHelper h){
        var requests=List.of(new StockTickerCrafting.Selection(STICK,2));var materials=List.of(item(Items.OAK_PLANKS,3));var edit=new StockTickerCraftingPackets.Edit(14,7,requests,materials,false,"Budget",false,true,new StockTickerCrafting.Adjustment(STICK,-1));
        var cart=StockTickerCrafting.reconcile(h.getLevel(),summary(item(Items.OAK_PLANKS,64)),materials,requests);var preview=new StockTickerCraftingPackets.Preview(14,7,cart.order(),cart.selections(),cart.results(),cart.manual());
        var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());try{
            StockTickerCraftingPackets.Edit.CODEC.encode(buffer,edit);var decoded=StockTickerCraftingPackets.Edit.CODEC.decode(buffer);h.assertTrue(decoded.exactMaterials(),"Exact-material mode crosses network");h.assertValueEqual(decoded.manual().getFirst().count,3,"Full budget retained");h.assertValueEqual(decoded.adjustment().crafts(),-1,"Signed incremental recipe change retained");h.assertValueEqual(decoded.adjustment().recipe(),STICK,"Incremental recipe ID retained");
            StockTickerCraftingPackets.Preview.CODEC.encode(buffer,preview);var reply=StockTickerCraftingPackets.Preview.CODEC.decode(buffer);h.assertValueEqual(reply.manual().getFirst().count,1,"Unassigned remainder crosses network");
            var pos=new BlockPos(3,2,3);h.setBlock(pos,AllBlocks.STOCK_TICKER);var ticker=h.getBlockEntity(pos,StockTickerBlockEntity.class);var player=h.makeMockPlayer(GameType.SURVIVAL);var menu=new StockTickerMenu(14,player.getInventory(),new StockTickerMenu.Opening(ticker.getBlockPos(),ticker.getUpdateTag(h.getLevel().registryAccess()),false,false,false));menu.craftingRevision=8;menu.receive(reply);h.assertValueEqual(menu.craftingReply,-1,"Earlier recipe preview cannot overwrite a later material edit");
        }finally{buffer.release();}h.succeed();
    }
    @GameTest public void finalEditedBudgetUsesFreshStockAndDispatchesOnlyRemainingWholeCraft(GameTestHelper h){
        var source=new BlockPos(2,2,2);var pos=new BlockPos(5,2,5);var id=UUID.randomUUID();h.setBlock(source.north(),Blocks.BARREL);var barrel=h.getBlockEntity(source.north(),net.minecraft.world.level.block.entity.BarrelBlockEntity.class);barrel.setItem(0,new ItemStack(Items.OAK_PLANKS,6));h.setBlock(source,AllBlocks.PACKAGER.defaultBlockState().setValue(PackagerBlock.FACING,Direction.SOUTH));h.setBlock(source.above(),AllBlocks.STOCK_LINK);var link=h.getBlockEntity(source.above(),com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity.class);link.behaviour.setFrequency(id);link.initialize();h.setBlock(pos,AllBlocks.STOCK_TICKER);var ticker=h.getBlockEntity(pos,StockTickerBlockEntity.class);ticker.behaviour.setFrequency(id);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(ticker.getBlockPos()).add(0,0,1));var menu=new StockTickerMenu(14,player.getInventory(),new StockTickerMenu.Opening(ticker.getBlockPos(),ticker.getUpdateTag(h.getLevel().registryAccess()),false,false,false));
        var requests=List.of(new StockTickerCrafting.Selection(STICK,3));var materials=List.of(item(Items.OAK_PLANKS,6));
        var preview=StockTickerCraftingPackets.edit(menu,player,new StockTickerCraftingPackets.Edit(14,1,requests,materials,false,"Edited",false,true));h.assertValueEqual(preview.selections().getFirst().crafts(),3,"Material budget is not added on top of recipe ingredients");barrel.setItem(0,new ItemStack(Items.OAK_PLANKS,2));
        var submitted=StockTickerCraftingPackets.edit(menu,player,new StockTickerCraftingPackets.Edit(14,2,requests,materials,true,"Edited",false,true));
        h.assertValueEqual(submitted.selections().getFirst().crafts(),1,"Final budget reads current stock in same tick");h.assertTrue(submitted.order().orderedStacksMatchOrderedRecipes()&&barrel.isEmpty(),"One whole craft is dispatched");
        var parcel=h.getBlockEntity(source,PackagerBlockEntity.class).heldBox;var context=PackageItem.getOrderContext(parcel);h.assertTrue(context!=null&&context.orderedStacksMatchOrderedRecipes()&&context.orderedCrafts().getFirst().count()==1&&PackageItem.getAddress(parcel).equals("Edited"),"Real parcel preserves adjusted recipe and address");h.succeed();
    }
    @GameTest public void recipeQuantityChangesPreserveAlreadyChosenIngredientComponents(GameTestHelper h){
        var named=new ItemStack(Items.OAK_PLANKS);named.set(DataComponents.CUSTOM_NAME,Component.literal("Keep this ingredient"));var stock=summary(new BigItemStack(named,1),item(Items.BIRCH_PLANKS,64));var materials=List.of(new BigItemStack(named,1),item(Items.BIRCH_PLANKS,1));
        var added=StockTickerCrafting.adjust(h.getLevel(),stock,materials,List.of(new StockTickerCrafting.Selection(STICK,1)),new StockTickerCrafting.Adjustment(STICK,1));
        h.assertValueEqual(added.selections().getFirst().crafts(),2,"One craft added");h.assertValueEqual(added.order().stacks().stream().filter(entry->ItemStack.isSameItemSameComponents(entry.stack,named)).findFirst().orElseThrow().count,1,"Original named ingredient never replaced by the larger birch supply");h.assertValueEqual(added.order().stacks().stream().filter(entry->entry.stack.is(Items.BIRCH_PLANKS)).findFirst().orElseThrow().count,3,"Only two extra birch planks added");h.assertTrue(added.order().orderedStacksMatchOrderedRecipes(),"Both exact patterns retained");
        var removed=StockTickerCrafting.adjust(h.getLevel(),stock,added.order().stacks(),added.selections(),new StockTickerCrafting.Adjustment(STICK,-1));h.assertValueEqual(removed.selections().getFirst().crafts(),1,"One craft removed");h.assertValueEqual(removed.order().stacks().stream().mapToInt(entry->entry.count).sum(),2,"Only one recipe's ingredients removed");h.assertTrue(removed.order().orderedStacksMatchOrderedRecipes(),"Reduced context is complete");h.succeed();
    }
    @GameTest public void addingRecipeCannotExceedExistingNineMaterialTypes(GameTestHelper h){
        var raw=List.of(Items.DIAMOND,Items.APPLE,Items.GOLD_NUGGET,Items.IRON_INGOT,Items.COPPER_INGOT,Items.COAL,Items.REDSTONE,Items.EMERALD,Items.LAPIS_LAZULI).stream().map(item->item(item,1)).toList();var stock=summary(raw.toArray(BigItemStack[]::new));stock.add(item(Items.OAK_PLANKS,64));
        var denied=StockTickerCrafting.adjust(h.getLevel(),stock,raw,List.of(),new StockTickerCrafting.Adjustment(STICK,1));h.assertTrue(denied.selections().isEmpty(),"Tenth material type is not admitted");h.assertValueEqual(denied.order().stacks().size(),9,"All existing raw lines are retained");h.succeed();
    }
    @GameTest public void incrementalInfiniteRecipeEditsCapTotalOutputsWithoutLeavingAccidentalRawExtras(GameTestHelper h){
        var stock=summary(item(Items.OAK_PLANKS,BigItemStack.INF));long before=System.nanoTime();var cart=StockTickerCrafting.adjust(h.getLevel(),stock,List.of(item(Items.OAK_PLANKS,499_999_998)),List.of(new StockTickerCrafting.Selection(STICK,249_999_999)),new StockTickerCrafting.Adjustment(STICK,BigItemStack.INF));
        h.assertValueEqual(cart.selections().getFirst().crafts(),250_000_000,"Total outputs capped at INF");h.assertValueEqual(cart.order().stacks().getFirst().count,500_000_000,"Only the last possible craft added");h.assertTrue(cart.manual().isEmpty()&&cart.order().orderedStacksMatchOrderedRecipes(),"No surplus from an overflowing incremental request");h.assertTrue(System.nanoTime()-before<2_000_000_000L,"Incremental INF calculation remains bounded");h.succeed();
    }
}
