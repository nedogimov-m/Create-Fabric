package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.content.logistics.depot.SharedDepotBlockMethods;
import com.simibubi.create.content.logistics.depot.storage.DepotMountedStorage;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Proposed real world/storage/processing/mounted tests. All runtime checks remain UNRUN until root executes them. */
public final class SourceDepotGameTests {
    private static final String STRUCTURE="create_test:reservoir";
    private static final BlockPos P=new BlockPos(5,5,5),RECEIPT=P.east(3);
    private static ItemStack stock(int amount){var stack=new ItemStack(Items.DIAMOND,amount);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Original depot source stock"));return stack;}
    private static DepotBlockEntity depot(GameTestHelper h){h.setBlock(P,AllBlocks.DEPOT);return h.getBlockEntity(P,DepotBlockEntity.class);}
    private static Storage<ItemVariant> actual(GameTestHelper h){var storage=ItemStorage.SIDED.find(h.getLevel(),h.absolutePos(P),Direction.UP);h.assertTrue(storage!=null,"Actual registered world capability exists");return storage;}
    private static void supply(GameTestHelper h,Storage<ItemVariant> storage,int amount){try(var tx=Transaction.openOuter()){h.assertValueEqual(storage.insert(ItemVariant.of(stock(1)),amount,tx),(long)amount,"Finite original source batch enters a genuine owner");tx.commit();}}
    private static long count(Storage<ItemVariant> storage,ItemStack stack){long total=0;for(var view:storage)if(view.getResource().equals(ItemVariant.of(stack)))total+=view.getAmount();return total;}
    private static int world(GameTestHelper h,ItemStack stack){return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(P)).inflate(2),entity->ItemStack.isSameItemSameComponents(entity.getItem(),stack)).stream().mapToInt(entity->entity.getItem().getCount()).sum();}
    private static void fillOutputs(DepotBlockEntity depot){try(var tx=Transaction.openOuter()){for(var slot:depot.inventory.outputs)slot.insert(ItemVariant.of(Items.STONE),64,tx);tx.commit();}}

    @GameTest(structure=STRUCTURE) public void occupiedOrdinaryDepotRejectsASecondCompatibleBatchOnTheActualCapability(GameTestHelper h){
        var depot=depot(h);var storage=actual(h);supply(h,storage,7);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(storage.insert(ItemVariant.of(stock(1)),3,tx),0L,"Source ordinary depot does not merge a second same-component batch");tx.commit();}
        h.assertTrue(ItemStack.matches(depot.getHeldItem(),stock(7)),"All seven original components and counts remain");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void sourceOccupiedGateAlsoControlsTheActualDirectBeltBehaviour(GameTestHelper h){
        depot(h);var storage=actual(h);supply(h,storage,7);var input=BlockEntityBehaviour.get(h.getLevel(),h.absolutePos(P),DirectBeltInputBehaviour.TYPE);
        h.assertTrue(input!=null&&input.isOccupied(Direction.EAST),"Actual source provider advertises its real occupied state");
        h.assertTrue(ItemStack.matches(input.handleInsertion(stock(3),Direction.EAST,false),stock(3)),"Direct input returns the entire unpaid second batch");h.assertValueEqual(count(storage,stock(1)),7L,"No route bypasses the genuine ordinary policy");h.succeed();
    }
    @GameTest(structure=STRUCTURE,maxTicks=30) public void outputStockNeverPromotesItselfIntoTheSourceHeldSlot(GameTestHelper h){
        var depot=depot(h);try(var tx=Transaction.openOuter()){depot.inventory.outputs.get(7).insert(ItemVariant.of(stock(1)),7,tx);tx.commit();}
        h.runAfterDelay(12,()->{h.assertTrue(depot.getHeldItem().isEmpty(),"Source has no automatic output promotion");h.assertValueEqual(depot.inventory.outputs.get(7).getAmount(),7L,"All original output stock remains in its eighth slot");h.assertValueEqual(count(actual(h),stock(1)),7L,"Actual extraction capability still owns the complete output");h.succeed();});
    }
    @GameTest(structure=STRUCTURE) public void evenAnOccupiedEighthOutputBlocksOrdinaryInsertionUntilARealChestReceivesIt(GameTestHelper h){
        var depot=depot(h);try(var tx=Transaction.openOuter()){depot.inventory.outputs.get(7).insert(ItemVariant.of(stock(1)),7,tx);tx.commit();}
        var source=actual(h);try(var tx=Transaction.openOuter()){h.assertValueEqual(source.insert(ItemVariant.of(Items.APPLE),3,tx),0L,"Output-only stock occupies the source depot");}
        h.setBlock(RECEIPT,Blocks.CHEST);var receipt=ItemStorage.SIDED.find(h.getLevel(),h.absolutePos(RECEIPT),Direction.UP);
        try(var outer=Transaction.openOuter()){try(var inner=outer.openNested()){h.assertValueEqual(receipt.insert(ItemVariant.of(stock(1)),7,inner),7L,"Actual chest accepts tentative components");h.assertValueEqual(source.extract(ItemVariant.of(stock(1)),7,inner),7L,"Original source is tentatively debited");inner.commit();}}
        h.assertValueEqual(count(source,stock(1)),7L,"Outer abort restores source");h.assertValueEqual(count(receipt,stock(1)),0L,"Outer abort restores actual receipt");
        try(var tx=Transaction.openOuter()){receipt.insert(ItemVariant.of(stock(1)),7,tx);source.extract(ItemVariant.of(stock(1)),7,tx);tx.commit();}
        h.assertValueEqual(count(receipt,stock(1)),7L,"Original seven diamonds remain in their real new owner");
        try(var tx=Transaction.openOuter()){h.assertValueEqual(source.insert(ItemVariant.of(Items.APPLE),3,tx),3L,"Actual free depot now accepts the next distinct batch");tx.commit();}
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void nestedProcessingAbortRestoresMainAllEightOutputsAndDoesNotEmitWorldStock(GameTestHelper h){
        var depot=depot(h);var storage=actual(h);supply(h,storage,3);fillOutputs(depot);int before=world(h,new ItemStack(Items.EMERALD));
        try(var outer=Transaction.openOuter()){try(var inner=outer.openNested()){h.assertTrue(depot.depotBehaviour.applyRecipeOutputs(1,List.of(new ItemStack(Items.EMERALD)),false,inner),"Actual source processing API debits one main item");inner.commit();}h.assertValueEqual(depot.getHeldItem().getCount(),2,"Tentative real input changes");h.assertValueEqual(world(h,new ItemStack(Items.EMERALD)),before,"No overflow material before outer commit");}
        h.assertTrue(ItemStack.matches(depot.getHeldItem(),stock(3)),"Nested abort restores original exact three-item main");h.assertValueEqual(count(storage,new ItemStack(Items.STONE)),512L,"All eight actual64-item buffers survive abort");h.assertValueEqual(world(h,new ItemStack(Items.EMERALD)),before,"Aborted recipe cannot create a world output");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void committedSourceOverflowDebitsMainAndEmitsOnlyTheTrueRemainderOnce(GameTestHelper h){
        var depot=depot(h);var storage=actual(h);supply(h,storage,3);fillOutputs(depot);
        try(var tx=Transaction.openOuter()){h.assertTrue(depot.depotBehaviour.applyRecipeOutputs(1,List.of(new ItemStack(Items.EMERALD)),false,tx),"Source processing executes over its genuine held stock");tx.commit();}
        h.assertTrue(ItemStack.matches(depot.getHeldItem(),stock(2)),"Exactly one original main item is consumed");h.assertValueEqual(count(storage,new ItemStack(Items.STONE)),512L,"Existing eight full outputs are untouched");h.assertValueEqual(count(storage,new ItemStack(Items.EMERALD))+world(h,new ItemStack(Items.EMERALD)),1L,"Exactly the one genuine recipe result exists in world/storage owners");
        h.assertValueEqual(world(h,new ItemStack(Items.EMERALD)),1,"Source overflows to a real ItemEntity");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void firstConvertedResultReallyOwnsHeldWhenTheOriginalInputIsConsumed(GameTestHelper h){
        var depot=depot(h);supply(h,actual(h),1);fillOutputs(depot);
        try(var tx=Transaction.openOuter()){h.assertTrue(depot.depotBehaviour.applyRecipeOutputs(1,List.of(new ItemStack(Items.EMERALD,2),new ItemStack(Items.GOLD_INGOT)),false,tx),"Actual source processing API runs");tx.commit();}
        h.assertTrue(depot.getHeldItem().is(Items.EMERALD)&&depot.getHeldItem().getCount()==2,"First converted stack is genuine main owner even when buffers are full");h.assertValueEqual(world(h,new ItemStack(Items.GOLD_INGOT)),1,"Only the additional true result overflows");h.assertValueEqual(world(h,new ItemStack(Items.EMERALD)),0,"The actual held result has no duplicate world copy");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void explicitSourceHeldReplacementPreservesLegitimateOverstackCountsAndRollsBack(GameTestHelper h){
        var depot=depot(h);depot.depotBehaviour.setHeldItem(new TransportedItemStack(stock(99),.5f));
        h.assertTrue(ItemStack.matches(depot.getHeldItem(),stock(99)),"Source direct held setter preserves the entire actual stack");
        try(var tx=Transaction.openOuter()){depot.depotBehaviour.setHeldItem(new TransportedItemStack(new ItemStack(Items.APPLE,3),.5f),tx);}
        h.assertTrue(ItemStack.matches(depot.getHeldItem(),stock(99)),"Aborted replacement restores all original overstacked components");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void nativeInventoryCountsStayAuthoritativeWhenSavedTransportMetadataHasAnotherCount(GameTestHelper h){
        var depot=depot(h);supply(h,actual(h),7);var tag=depot.saveWithFullMetadata(h.getLevel().registryAccess());
        tag.getCompoundOrEmpty("HeldItem").store("Item",ItemStack.CODEC,stock(2));
        var restored=(DepotBlockEntity)BlockEntity.loadStatic(depot.getBlockPos(),depot.getBlockState(),tag,h.getLevel().registryAccess());
        h.assertTrue(restored!=null&&ItemStack.matches(restored.getHeldItem(),stock(7)),"Transport metadata cannot hydrate an extra two-item owner over actual Input0");h.assertValueEqual(restored.inventory.outputs.size(),8,"Existing8 output slots survive native loading");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void originalHeldAndOutputBufferShapeLoadsWithoutDoubleHydratingNativeInventory(GameTestHelper h){
        var depot=depot(h);supply(h,actual(h),7);var tag=depot.saveWithFullMetadata(h.getLevel().registryAccess());tag.remove("Inventory");
        var buffer=new CompoundTag();var items=new ListTag();var output=(CompoundTag)ItemStack.CODEC.encodeStart(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),new ItemStack(Items.GOLD_INGOT,3)).getOrThrow();output.putInt("Slot",7);items.add(output);buffer.put("Items",items);tag.put("OutputBuffer",buffer);
        var restored=(DepotBlockEntity)BlockEntity.loadStatic(depot.getBlockPos(),depot.getBlockState(),tag,h.getLevel().registryAccess());
        h.assertTrue(restored!=null&&ItemStack.matches(restored.getHeldItem(),stock(7)),"Actual source-shaped held stack hydrates once when native Inventory is absent");h.assertValueEqual(restored.inventory.outputs.get(7).getAmount(),3L,"Original source eighth processing slot loads");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void comparatorUsesActualHeldAndProcessingOutputsInsteadOfOnlyMain(GameTestHelper h){
        var depot=depot(h);try(var tx=Transaction.openOuter()){depot.inventory.outputs.get(7).insert(ItemVariant.of(Items.GOLD_INGOT),32,tx);tx.commit();}
        h.assertValueEqual(SharedDepotBlockMethods.getComparatorInputOverride(depot.getBlockState(),h.getLevel(),h.absolutePos(P)),8,"Source32/64 output-only stock drives comparator8");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void realTopInteractionReturnsPriorStockAndTransfersTheWholeOriginalHand(GameTestHelper h){
        var depot=depot(h);supply(h,actual(h),7);
        try(var session=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(P)).add(0,0,2))){
            var player=session.player();var carrots=new ItemStack(Items.CARROT,3);carrots.set(DataComponents.CUSTOM_NAME,Component.literal("Original offered carrots"));player.setItemInHand(InteractionHand.MAIN_HAND,carrots);
            var side=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)).add(0,0,.499),Direction.SOUTH,h.absolutePos(P),false);
            player.gameMode.useItemOn(player,h.getLevel(),player.getMainHandItem(),InteractionHand.MAIN_HAND,side);
            h.assertTrue(ItemStack.matches(depot.getHeldItem(),stock(7))&&ItemStack.matches(player.getMainHandItem(),carrots),"Side click changes neither owner");
            var top=new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)).add(0,5/16f,0),Direction.UP,h.absolutePos(P),false);
            player.gameMode.useItemOn(player,h.getLevel(),player.getMainHandItem(),InteractionHand.MAIN_HAND,top);
            h.assertTrue(ItemStack.matches(depot.getHeldItem(),carrots.copyWithCount(3)),"Entire original hand and its components moved into actual source depot");
            long returned=0;for(int index=0;index<player.getInventory().getContainerSize();index++){var stack=player.getInventory().getItem(index);if(ItemStack.isSameItemSameComponents(stack,stock(1)))returned+=stack.getCount();}
            h.assertValueEqual(returned,7L,"All original held diamonds returned to the actual survival inventory");h.assertTrue(player.getMainHandItem().isEmpty(),"Hand was genuinely debited");
        }h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void removedWorldCapabilityAndAlreadyHeldViewsCannotReinsertOrExtractAnotherCopy(GameTestHelper h){
        depot(h);var stale=actual(h);supply(h,stale,7);var view=stale.iterator().next();h.setBlock(P,Blocks.AIR);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(stale.extract(ItemVariant.of(stock(1)),7,tx),0L,"Removed capability cannot export stock");h.assertValueEqual(stale.insert(ItemVariant.of(stock(1)),3,tx),0L,"Removed capability cannot acquire stock");h.assertValueEqual(view.extract(ItemVariant.of(stock(1)),7,tx),0L,"A previously held iterator view also expires");tx.commit();}
        h.assertValueEqual(view.getAmount(),0L,"Held view does not expose removed owner contents");h.assertValueEqual(world(h,stock(1)),7,"Actual destruction drops exactly original seven named diamonds");h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void genuineMountedCaptureReturnRetainsEightOutputsAndExpiresBothPreviousOwners(GameTestHelper h)throws Exception{
        var depot=depot(h);var source=actual(h);supply(h,source,7);
        try(var tx=Transaction.openOuter()){for(var slot:depot.inventory.outputs)slot.insert(ItemVariant.of(Items.GOLD_INGOT),2,tx);tx.commit();}
        var structure=new BearingContraption(false,Direction.EAST);h.assertTrue(structure.assemble(h.getLevel(),h.absolutePos(P.west())),"Actual source assembly succeeds");
        var captured=(DepotMountedStorage)structure.getStorage().getAllItemStorages().get(BlockPos.ZERO);
        h.assertValueEqual(count(captured,stock(1)),0L,"Unowned capture cannot expose another stock copy");
        h.assertTrue(structure.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO),"Actual world removal hands ownership to contraption");
        var mounted=(DepotMountedStorage)structure.getStorage().getAllItemStorages().get(BlockPos.ZERO);
        h.assertTrue(depot.isRemoved(),"Original live block entity genuinely left the world");h.assertValueEqual(count(mounted,stock(1)),7L,"Mounted owner holds the original source stack");
        try(var tx=Transaction.openOuter()){h.assertValueEqual(source.extract(ItemVariant.of(stock(1)),7,tx),0L,"Old world capability expired after removal");}
        BlockPos returned=P.south(3);h.assertTrue(structure.tryAddBlocksToWorld(h.getLevel(),new StructureTransform(h.absolutePos(returned),0,0,0)),"Actual source disassembly returns world stock");
        var restored=h.getBlockEntity(returned,DepotBlockEntity.class);h.assertTrue(restored!=depot&&ItemStack.matches(restored.getHeldItem(),stock(7)),"Fresh world block entity owns exactly the original named stack");
        h.assertValueEqual(restored.inventory.outputs.stream().mapToLong(slot->slot.getAmount()).sum(),16L,"All eight distinct saved processing slots retain their two genuine gold ingots");
        try(var tx=Transaction.openOuter()){h.assertValueEqual(mounted.extract(ItemVariant.of(stock(1)),7,tx),0L,"Old mounted owner expires after the real return");}
        h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void sourceTopSwapConservesActualMatchingMainHandAndPreviouslyHeldComponents(GameTestHelper h){
        var depot=depot(h);supply(h,actual(h),7);
        try(var session=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(P)).add(0,0,2))){var player=session.player();player.setItemInHand(InteractionHand.MAIN_HAND,stock(3));
            var top=new BlockHitResult(Vec3.atLowerCornerOf(h.absolutePos(P)).add(.5,13/16d,.5),Direction.UP,h.absolutePos(P),false);
            player.gameMode.useItemOn(player,h.getLevel(),player.getMainHandItem(),InteractionHand.MAIN_HAND,top);
            long owned=depot.getHeldItem().getCount();for(int slot=0;slot<player.getInventory().getContainerSize();slot++){var item=player.getInventory().getItem(slot);if(ItemStack.isSameItemSameComponents(item,stock(1)))owned+=item.getCount();}
            h.assertValueEqual(owned,10L,"Actual native merge during inventory return preserves all original7+3 matching components");h.assertTrue(player.getMainHandItem().isEmpty(),"Actual source top swap debits whole final hand");h.assertTrue(ItemStack.isSameItemSameComponents(depot.getHeldItem(),stock(1)),"Original exact source components stay in the real held owner");
        }h.succeed();
    }

}
