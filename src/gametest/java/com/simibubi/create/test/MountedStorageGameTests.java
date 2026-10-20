package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.api.contraption.storage.item.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.fluids.tank.*;
import com.simibubi.create.content.fluids.tank.storage.FluidTankMountedStorage;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.depot.storage.DepotMountedStorage;
import com.simibubi.create.content.logistics.vault.*;
import com.simibubi.create.foundation.fluid.FluidStack;
import com.simibubi.create.foundation.item.MountedItemInventory;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.material.Fluids;

public class MountedStorageGameTests {
    private static final BlockPos P=new BlockPos(3,5,3),T=P.south(3);
    private static final long BUCKET=FluidConstants.BUCKET;
    private static ItemStack named(){var item=new ItemStack(Items.DIAMOND,7);item.set(DataComponents.CUSTOM_NAME,Component.literal("Mounted components"));return item;}
    private static BearingContraption assemble(GameTestHelper h)throws AssemblyException{var structure=new BearingContraption(false,Direction.EAST);h.assertTrue(structure.assemble(h.getLevel(),h.absolutePos(P.west())),"Assembly succeeds");return structure;}
    private static void remove(GameTestHelper h,Contraption structure)throws AssemblyException{h.assertTrue(structure.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO),"Structure takes ownership");}
    private static BearingContraption barrel(GameTestHelper h)throws AssemblyException{h.setBlock(P,Blocks.BARREL);h.getBlockEntity(P,BarrelBlockEntity.class).setItem(4,named());var structure=assemble(h);remove(h,structure);return structure;}
    private static void restore(GameTestHelper h,Contraption structure,int x,int y,int z)throws AssemblyException{h.assertTrue(structure.tryAddBlocksToWorld(h.getLevel(),new StructureTransform(h.absolutePos(T),x,y,z)),"Disassembly succeeds");}
    private static MountedItemStorage item(Contraption structure){return structure.getStorage().getAllItemStorages().get(BlockPos.ZERO);}
    private static long count(Storage<ItemVariant> storage){long total=0;for(var view:storage)total+=view.getAmount();return total;}
    private static void extract(Storage<ItemVariant> storage,ItemStack stack,long amount){try(var tx=Transaction.openOuter()){if(storage.extract(ItemVariant.of(stack),amount,tx)!=amount)throw new IllegalStateException("Expected extraction");tx.commit();}}
    private static void checked(Checked action){try{action.run();}catch(RuntimeException error){throw error;}catch(Exception error){throw new RuntimeException(error);}}
    @FunctionalInterface private interface Checked{void run()throws Exception;}
    private static FluidTankBlockEntity tank(GameTestHelper h,BlockPos pos){return h.getBlockEntity(pos,FluidTankBlockEntity.class);}
    private static void tanks(GameTestHelper h){for(var pos:List.of(P,P.east(),P.south(),P.east().south()))h.setBlock(pos,AllBlocks.FLUID_TANK);}
    private static long totalFluid(GameTestHelper h,List<BlockPos> cells){long amount=0;for(var pos:cells){var tank=tank(h,pos);if(tank.isController())amount+=tank.getTankInventory().getFluidAmount();}return amount;}

    @GameTest public void captureDoesNotExposeAnotherOwnerAndRefreshInvalidatesOldMountedHandles(GameTestHelper h)throws Exception{
        h.setBlock(P,Blocks.BARREL);h.getBlockEntity(P,BarrelBlockEntity.class).setItem(4,named());var structure=assemble(h);var captured=item(structure);var slot=captured.getSlot(4);
        h.assertValueEqual(count(captured),0L,"World retains exclusive inventory before removal");try(var tx=Transaction.openOuter()){h.assertValueEqual(captured.extract(ItemVariant.of(named()),7,tx),0L,"Capture cannot extract");}
        h.getBlockEntity(P,BarrelBlockEntity.class).setItem(4,named().copyWithCount(11));remove(h,structure);
        h.assertValueEqual(count(captured),0L,"Old assembly snapshot remains unavailable");h.assertValueEqual(slot.getAmount(),0L,"Previously held slot remains unavailable");h.assertValueEqual(item(structure).getStackInSlot(4).getCount(),11,"Fresh latest contents owned");h.succeed();
    }
    @GameTest public void abortedAndNestedItemTransactionsPreserveExactComponents(GameTestHelper h)throws Exception{
        var structure=barrel(h);var storage=item(structure);var variant=ItemVariant.of(named());
        try(var outer=Transaction.openOuter()){storage.extract(variant,2,outer);try(var inner=outer.openNested()){storage.insert(ItemVariant.of(Items.GOLD_INGOT),4,inner);inner.commit();}}
        h.assertValueEqual(count(storage),7L,"Outer abort restores nested insertion/extraction");h.assertTrue(ItemStack.isSameItemSameComponents(storage.getStackInSlot(4),named()),"Item components survive rollback");
        try(var tx=Transaction.openOuter()){storage.getSlot(4).extract(variant,3,tx);tx.commit();}h.assertValueEqual(storage.getStackInSlot(4).getCount(),4,"Slot extraction commits");h.succeed();
    }
    @GameTest public void changedMountedContentsOverrideOldBlockNbtAfterSaveLoad(GameTestHelper h)throws Exception{
        var structure=barrel(h);var storage=item(structure);extract(storage,named(),3);storage.setStackInSlot(8,new ItemStack(Items.GOLD_INGOT,5));
        var restored=Contraption.fromNBT(h.getLevel(),structure.writeNBT(h.getLevel().registryAccess(),false),false);h.assertValueEqual(item(restored).getStackInSlot(4).getCount(),4,"New owner reads mutable data");restore(h,restored,0,90,0);
        var barrel=h.getBlockEntity(T,BarrelBlockEntity.class);h.assertValueEqual(barrel.getItem(4).getCount(),4,"Old seven-item BE snapshot overwritten");h.assertTrue(ItemStack.isSameItemSameComponents(barrel.getItem(4),named()),"Components preserved");h.assertValueEqual(barrel.getItem(8).getCount(),5,"Newly inserted mounted item survives");h.succeed();
    }
    @GameTest public void disassemblyExpiresStorageAggregateAndHeldSlotViews(GameTestHelper h)throws Exception{
        var structure=barrel(h);var manager=structure.getStorage();var aggregate=manager.getAllItems();var storage=item(structure);var slot=storage.getSlot(4);var view=aggregate.iterator().next();restore(h,structure,0,0,0);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(aggregate.extract(ItemVariant.of(named()),7,tx),0L,"Old aggregate cannot duplicate returned inventory");h.assertValueEqual(slot.extract(ItemVariant.of(named()),7,tx),0L,"Held slot cannot duplicate");h.assertValueEqual(slot.insert(ItemVariant.of(Items.DIAMOND),3,tx),0L,"Held slot cannot mutate removed owner");tx.commit();}
        h.assertValueEqual(count(aggregate),0L,"Old aggregate contents unavailable");h.assertValueEqual(view.getAmount(),0L,"Held view unavailable");h.assertValueEqual(h.getBlockEntity(T,BarrelBlockEntity.class).getItem(4).getCount(),7,"World is sole owner");h.succeed();
    }
    @GameTest public void doubleChestMountsEachHalfOnceAndKeepsSixRowMenuOrder(GameTestHelper h)throws Exception{
        h.setBlock(P,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.LEFT).setValue(ChestBlock.FACING,Direction.NORTH));
        h.setBlock(P.east(),Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.RIGHT).setValue(ChestBlock.FACING,Direction.NORTH));
        h.getBlockEntity(P,ChestBlockEntity.class).setItem(8,named());h.getBlockEntity(P.east(),ChestBlockEntity.class).setItem(5,new ItemStack(Items.APPLE,6));var structure=assemble(h);remove(h,structure);var manager=structure.getStorage();
        h.assertValueEqual(manager.getAllItemStorages().size(),2,"Two component storages");h.assertValueEqual(count(manager.getAllItems()),13L,"Double chest is never copied twice");
        var player=h.makeMockPlayer(GameType.SURVIVAL);var provider=manager.createMenuProvider(structure,BlockPos.ZERO,p->true,p->{});var menu=(ChestMenu)provider.createMenu(3,player.getInventory(),player);
        h.assertValueEqual(menu.getRowCount(),6,"Combined vanilla menu");h.assertTrue(menu.getSlot(5).getItem().is(Items.APPLE),"Right half is first");h.assertTrue(ItemStack.isSameItemSameComponents(menu.getSlot(27+8).getItem(),named()),"Left half follows");
        menu.quickMoveStack(player,5);restore(h,structure,0,90,0);h.assertValueEqual(h.getBlockEntity(T.north(),ChestBlockEntity.class).getItem(5).getCount(),0,"Menu change follows correct transformed half");h.assertValueEqual(h.getBlockEntity(T,ChestBlockEntity.class).getItem(8).getCount(),7,"Other half remains independent");h.succeed();
    }
    @GameTest public void vanillaQuickMoveAndInPlaceMenuStackMutationsWriteMountedSlots(GameTestHelper h)throws Exception{
        var structure=barrel(h);var player=h.makeMockPlayer(GameType.SURVIVAL);var closed=new AtomicBoolean();var provider=structure.getStorage().createMenuProvider(structure,BlockPos.ZERO,p->true,p->closed.set(true));var menu=(ChestMenu)provider.createMenu(4,player.getInventory(),player);
        menu.getSlot(4).getItem().shrink(2);menu.getSlot(4).setChanged();h.assertValueEqual(item(structure).getStackInSlot(4).getCount(),5,"Vanilla in-place mutations are persisted");menu.quickMoveStack(player,4);h.assertTrue(item(structure).getStackInSlot(4).isEmpty(),"Shift click empties owned slot");h.assertValueEqual(player.getInventory().countItem(Items.DIAMOND),5,"Real items reach player");
        item(structure).setStackInSlot(4,new ItemStack(Items.APPLE,3));h.assertTrue(menu.getSlot(4).getItem().is(Items.APPLE),"Actor mutation visible in open menu");menu.removed(player);h.assertTrue(closed.get(),"Native close callback");restore(h,structure,0,0,0);h.assertTrue(h.getBlockEntity(T,BarrelBlockEntity.class).getItem(4).is(Items.APPLE),"Edited menu content restored");h.succeed();
    }
    @GameTest public void menuValidityUsesLivePredicateAndOwnership(GameTestHelper h)throws Exception{
        var structure=barrel(h);var player=h.makeMockPlayer(GameType.SURVIVAL);var near=new AtomicBoolean(true);var provider=structure.getStorage().createMenuProvider(structure,BlockPos.ZERO,p->near.get(),p->{});var menu=(ChestMenu)provider.createMenu(7,player.getInventory(),player);
        h.assertTrue(menu.stillValid(player),"Live predicate permits access");near.set(false);h.assertFalse(menu.stillValid(player),"Caller tracks moving distance");near.set(true);restore(h,structure,0,0,0);h.assertFalse(menu.stillValid(player),"Disassembly invalidates menu");menu.getSlot(4).set(new ItemStack(Items.GOLD_INGOT,50));h.assertTrue(h.getBlockEntity(T,BarrelBlockEntity.class).getItem(4).is(Items.DIAMOND),"Stale menu cannot change returned world storage");h.succeed();
    }
    @GameTest public void dispenserStorageIsInternalButItsNativeMenuIsUsable(GameTestHelper h)throws Exception{
        h.setBlock(P,Blocks.DISPENSER);h.getBlockEntity(P,DispenserBlockEntity.class).setItem(2,new ItemStack(Items.ARROW,9));var structure=assemble(h);remove(h,structure);var manager=structure.getStorage();h.assertValueEqual(count(manager.getAllItems()),0L,"Internal dispenser slots excluded from actor inventory");h.assertTrue(manager.getFuelItems()==null,"Internal slots are not fuel");h.assertValueEqual(count(item(structure)),9L,"Movement behavior can access its own ammunition");
        var player=h.makeMockPlayer(GameType.SURVIVAL);var provider=manager.createMenuProvider(structure,BlockPos.ZERO,p->true,p->{});h.assertTrue(provider.createMenu(9,player.getInventory(),player) instanceof DispenserMenu,"Vanilla 3x3 menu");extract(item(structure),new ItemStack(Items.ARROW),2);restore(h,structure,0,0,0);h.assertValueEqual(h.getBlockEntity(T,DispenserBlockEntity.class).getItem(2).getCount(),7,"Internal contents written back");h.succeed();
    }
    @GameTest public void vaultItemsAreExposedButCannotProvideFuelOrOpenMenu(GameTestHelper h)throws Exception{
        h.setBlock(P,AllBlocks.ITEM_VAULT);var vault=h.getBlockEntity(P,ItemVaultBlockEntity.class);try(var tx=Transaction.openOuter()){vault.getInventoryOfBlock().insert(ItemVariant.of(Items.COAL),8,tx);tx.commit();}var structure=assemble(h);remove(h,structure);var manager=structure.getStorage();h.assertValueEqual(count(manager.getAllItems()),8L,"Vault participates in shared item storage");h.assertTrue(manager.getFuelItems()==null,"Original vault fuel blacklist");h.assertTrue(manager.createMenuProvider(structure,BlockPos.ZERO,p->true,p->{})==null,"Vault cannot open a menu");extract(manager.getAllItems(),new ItemStack(Items.COAL),3);restore(h,structure,0,90,0);h.assertValueEqual(h.getBlockEntity(T,ItemVaultBlockEntity.class).inventory.inputs.getFirst().stack().getCount(),5,"Per-component vault mutation restored");h.succeed();
    }
    @GameTest public void externalStorageIsCombinedButNotSavedAndExpiresWithOwner(GameTestHelper h)throws Exception{
        var structure=barrel(h);var outside=new MountedItemInventory(9);outside.setStack(0,new ItemStack(Items.APPLE,5));var manager=structure.getStorage();manager.attachExternal(outside);var aggregate=manager.getAllItems();h.assertValueEqual(count(aggregate),12L,"External store included");var saved=Contraption.fromNBT(h.getLevel(),structure.writeNBT(h.getLevel().registryAccess(),false),false);h.assertValueEqual(count(saved.getStorage().getAllItems()),7L,"External inventory not copied to saved contraption");restore(h,structure,0,0,0);h.assertValueEqual(count(aggregate),0L,"Combined external handle expires");h.assertValueEqual(count(outside),5L,"External owner retains its own contents");h.succeed();
    }
    @GameTest public void creativeCrateKeepsInfiniteSupplyWithoutRetainingWorldFilter(GameTestHelper h)throws Exception{
        h.setBlock(P,AllBlocks.CREATIVE_CRATE);var crate=h.getBlockEntity(P,com.simibubi.create.content.logistics.crate.CreativeCrateBlockEntity.class);crate.filtering.setFilter(named());var structure=assemble(h);remove(h,structure);var storage=item(structure);crate.filtering.setFilter(new ItemStack(Items.APPLE));
        try(var tx=Transaction.openOuter()){h.assertValueEqual(storage.extract(ItemVariant.of(named()),100,tx),64L,"One supplied stack per source extraction");h.assertValueEqual(storage.extract(ItemVariant.of(named()),100,tx),64L,"Supply remains infinite");h.assertValueEqual(storage.insert(ItemVariant.of(Items.COBBLESTONE),999,tx),999L,"Creative void accepts arbitrary input");tx.commit();}
        restore(h,structure,0,0,0);var restored=h.getBlockEntity(T,com.simibubi.create.content.logistics.crate.CreativeCrateBlockEntity.class);h.assertTrue(ItemStack.isSameItemSameComponents(restored.filtering.getFilter(),named()),"Independent supplied item restored");h.succeed();
    }
    @GameTest public void finiteTankTransactionsAndHeldViewsOnlyDirtyCommittedChanges(GameTestHelper h)throws Exception{
        h.setBlock(P,AllBlocks.FLUID_TANK);tank(h,P).getTankInventory().fill(new FluidStack(FluidVariant.of(Fluids.WATER),5*BUCKET));var structure=assemble(h);remove(h,structure);var storage=(FluidTankMountedStorage)structure.getStorage().getFluids().storages.get(BlockPos.ZERO);var view=storage.iterator().next();
        try(var tx=Transaction.openOuter()){view.extract(FluidVariant.of(Fluids.WATER),BUCKET,tx);}h.assertValueEqual(storage.getFluid().getAmount(),5*BUCKET,"Abort restores fluid");h.assertFalse(storage.isDirty(),"Abort cannot publish a fluid update");
        try(var outer=Transaction.openOuter()){try(var inner=outer.openNested()){view.extract(FluidVariant.of(Fluids.WATER),BUCKET,inner);inner.commit();}}h.assertFalse(storage.isDirty(),"Nested commit followed by outer abort remains clean");
        try(var tx=Transaction.openOuter()){view.extract(FluidVariant.of(Fluids.WATER),2*BUCKET,tx);tx.commit();}h.assertTrue(storage.isDirty(),"View extraction marks committed state dirty");var delta=structure.getStorage().collectSync(h.getLevel().registryAccess());h.assertValueEqual(delta.getListOrEmpty("fluids").size(),1,"Tank is synchronized");h.assertTrue(structure.getStorage().collectSync(h.getLevel().registryAccess()).isEmpty(),"No repeated unchanged delta");restore(h,structure,0,0,0);h.assertValueEqual(tank(h,T).getTankInventory().getFluidAmount(),3*BUCKET,"Changed fluid restored");h.assertValueEqual(view.getAmount(),0L,"Fluid view expires after restoration");h.succeed();
    }
    @GameTest(maxTicks=35) public void yawTankRelocationUsesMountedFluidInsteadOfOldControllerSnapshot(GameTestHelper h){tanks(h);h.runAfterDelay(5,()->checked(()->{
        tank(h,P).getControllerBE().getTankInventory().fill(new FluidStack(FluidVariant.of(Fluids.WATER),20*BUCKET));var structure=assemble(h);remove(h,structure);var fluids=structure.getStorage().getFluids();h.assertValueEqual(fluids.storages.size(),1,"Only original tank controller mounted");try(var tx=Transaction.openOuter()){h.assertValueEqual(fluids.extract(FluidVariant.of(Fluids.WATER),5*BUCKET,tx),5*BUCKET,"Actor drains fluid");tx.commit();}
        var restored=Contraption.fromNBT(h.getLevel(),structure.writeNBT(h.getLevel().registryAccess(),false),false);restore(h,restored,0,90,0);h.assertTrue(tank(h,T.north()).isController(),"Controller relocates to new minimum");h.assertValueEqual(totalFluid(h,List.of(T,T.north(),T.east(),T.east().north())),15*BUCKET,"Mounted fluid follows relocated controller");h.succeed();
    }));}
    @GameTest(maxTicks=35) public void sidewaysTankSplittingDistributesLatestMountedFluidWithoutDuplication(GameTestHelper h){tanks(h);h.runAfterDelay(5,()->checked(()->{
        tank(h,P).getControllerBE().getTankInventory().fill(new FluidStack(FluidVariant.of(Fluids.WATER),20*BUCKET));var structure=assemble(h);remove(h,structure);try(var tx=Transaction.openOuter()){structure.getStorage().getFluids().extract(FluidVariant.of(Fluids.WATER),3*BUCKET,tx);tx.commit();}restore(h,structure,0,0,90);h.assertValueEqual(totalFluid(h,List.of(T,T.above(),T.south(),T.south().above())),17*BUCKET,"Latest total survives splitting");
    }));h.runAfterDelay(11,()->{h.assertValueEqual(totalFluid(h,List.of(T,T.above(),T.south(),T.south().above())),17*BUCKET,"Connectivity reforms without duplicating mounted amount");h.succeed();});}
    @GameTest public void creativeTankNeverConsumesSupplyOrChangesItThroughInsert(GameTestHelper h)throws Exception{
        h.setBlock(P,AllBlocks.CREATIVE_FLUID_TANK);var source=h.getBlockEntity(P,CreativeFluidTankBlockEntity.class);((CreativeFluidTankBlockEntity.CreativeSmartFluidTank)source.getTankInventory()).setContainedFluid(new FluidStack(FluidVariant.of(Fluids.WATER),BUCKET));var structure=assemble(h);remove(h,structure);var storage=structure.getStorage().getFluids();try(var tx=Transaction.openOuter()){for(int i=0;i<3;i++)h.assertValueEqual(storage.extract(FluidVariant.of(Fluids.WATER),3*BUCKET,tx),3*BUCKET,"Creative fluid remains available");h.assertValueEqual(storage.insert(FluidVariant.of(Fluids.LAVA),10*BUCKET,tx),10*BUCKET,"Creative void input");tx.commit();}
        var saved=Contraption.fromNBT(h.getLevel(),structure.writeNBT(h.getLevel().registryAccess(),false),false);restore(h,saved,0,0,0);h.assertValueEqual(tank(h,T).getTankInventory().getResource(),FluidVariant.of(Fluids.WATER),"Supplied fluid remains fixed");h.assertValueEqual(tank(h,T).getTankInventory().getFluidAmount(),FluidTankBlockEntity.CAPACITY,"Creative tank returns full supply");h.succeed();
    }
    @GameTest public void depotSyncContainsVisibleHeldItemButLeavesOrdinaryInventoriesPrivate(GameTestHelper h)throws Exception{
        h.setBlock(P,AllBlocks.DEPOT);var depot=h.getBlockEntity(P,DepotBlockEntity.class);try(var tx=Transaction.openOuter()){depot.inventory.inputs.getFirst().insert(ItemVariant.of(named()),7,tx);depot.inventory.outputs.getFirst().insert(ItemVariant.of(Items.GOLD_INGOT),2,tx);tx.commit();}var structure=assemble(h);remove(h,structure);var storage=(DepotMountedStorage)item(structure);
        try(var tx=Transaction.openOuter()){storage.extract(ItemVariant.of(named()),2,tx);}h.assertFalse(storage.isDirty(),"Aborted visible changes do not sync");extract(storage,named(),3);h.assertTrue(storage.isDirty(),"Committed held-item mutation is dirty");var spawn=structure.writeNBT(h.getLevel().registryAccess(),true);h.assertValueEqual(spawn.getListOrEmpty("items").size(),1,"Visible depot storage is included in spawn data");h.assertTrue(structure.getStorage().collectSync(h.getLevel().registryAccess()).getListOrEmpty("items").size()==1,"Visible depot delta emitted");restore(h,structure,0,0,0);var restored=h.getBlockEntity(T,DepotBlockEntity.class);h.assertValueEqual(restored.getHeldItem().getCount(),4,"Mounted held slot written back");h.assertValueEqual(restored.inventory.outputs.getFirst().stack().getCount(),2,"Separate processing output retained");h.succeed();
    }
    @GameTest public void malformedStorageDecodeCannotEraseExistingManagerContents(GameTestHelper h)throws Exception{
        var structure=barrel(h);var manager=structure.getStorage();var invalid=structure.writeNBT(h.getLevel().registryAccess(),false).copy();var entry=(CompoundTag)invalid.getListOrEmpty("items").getFirst();entry.getCompoundOrEmpty("storage").putString("type","missing:unknown_storage");boolean refused=false;try{manager.read(invalid,h.getLevel().registryAccess(),false,structure);}catch(IllegalStateException|IllegalArgumentException expected){refused=true;}h.assertTrue(refused,"Unknown type fails decoding");h.assertValueEqual(count(manager.getAllItems()),7L,"Decode failure leaves original owner intact");h.succeed();
    }
    @GameTest public void duplicateMountedPositionIsRejected(GameTestHelper h)throws Exception{
        var structure=barrel(h);var invalid=structure.writeNBT(h.getLevel().registryAccess(),false).copy();var list=invalid.getListOrEmpty("items");list.add(list.getFirst().copy());boolean refused=false;try{Contraption.fromNBT(h.getLevel(),invalid,false);}catch(IllegalArgumentException expected){refused=true;}h.assertTrue(refused,"Duplicate cannot overwrite storage");h.assertValueEqual(count(structure.getStorage().getAllItems()),7L,"Original owner intact");h.succeed();
    }
    @GameTest public void sparseCodecRejectsOutOfBoundsSlotRatherThanDiscardingItems(GameTestHelper h)throws Exception{
        var structure=barrel(h);var invalid=structure.writeNBT(h.getLevel().registryAccess(),false).copy();var entry=(CompoundTag)invalid.getListOrEmpty("items").getFirst();entry.getCompoundOrEmpty("storage").getCompoundOrEmpty("value").putInt("size",3);boolean refused=false;try{Contraption.fromNBT(h.getLevel(),invalid,false);}catch(IllegalStateException|IllegalArgumentException expected){refused=true;}h.assertTrue(refused,"Item at slot four cannot fit declared three slots");h.succeed();
    }
    @GameTest public void mountedSerializationAndDirectSlotReplacementRejectOpenTransactions(GameTestHelper h)throws Exception{
        var structure=barrel(h);try(var tx=Transaction.openOuter()){item(structure).extract(ItemVariant.of(named()),2,tx);boolean refusedSave=false,refusedReplace=false;try{structure.writeNBT(h.getLevel().registryAccess(),false);}catch(IllegalStateException expected){refusedSave=true;}try{item(structure).setStackInSlot(4,new ItemStack(Items.APPLE));}catch(IllegalStateException expected){refusedReplace=true;}h.assertTrue(refusedSave&&refusedReplace,"Uncommitted state cannot escape through snapshot or replacement");}h.assertValueEqual(count(item(structure)),7L,"Transaction rolled back");h.succeed();
    }
    @GameTest public void nativeRegistryStreamCodecsPreserveSlotComponentsAndFluidUnits(GameTestHelper h)throws Exception{
        var structure=barrel(h);var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{
            MountedItemStorage.STREAM_CODEC.encode(buffer,item(structure));var decoded=MountedItemStorage.STREAM_CODEC.decode(buffer);
            h.assertTrue(decoded.type==AllMountedStorageTypes.SIMPLE,"Registered item type survives native stream");h.assertValueEqual(decoded.getSlotCount(),27,"Empty slots are retained");h.assertTrue(ItemStack.matches(decoded.getStackInSlot(4),named()),"Item components/count/position retained");h.assertValueEqual(buffer.readableBytes(),0,"Item stream consumed exactly");
            var tank=new FluidTankMountedStorage(8*BUCKET,new FluidStack(FluidVariant.of(Fluids.WATER),2*BUCKET+17));
            com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage.STREAM_CODEC.encode(buffer,tank);var fluid=(FluidTankMountedStorage)com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage.STREAM_CODEC.decode(buffer);
            h.assertValueEqual(fluid.getCapacity(),8*BUCKET,"Native capacity units retained");h.assertValueEqual(fluid.getFluid().getAmount(),2*BUCKET+17,"Sub-millibucket Fabric precision retained");h.assertValueEqual(fluid.getFluid().variant(),FluidVariant.of(Fluids.WATER),"Native fluid variant retained");h.assertValueEqual(buffer.readableBytes(),0,"Fluid stream consumed exactly");
        }finally{buffer.release();}h.succeed();
    }
    @GameTest public void malformedMountedFluidCannotClampAwayOwnedAmount(GameTestHelper h)throws Exception{
        h.setBlock(P,AllBlocks.FLUID_TANK);tank(h,P).getTankInventory().fill(new FluidStack(FluidVariant.of(Fluids.WATER),5*BUCKET));var structure=assemble(h);remove(h,structure);var invalid=structure.writeNBT(h.getLevel().registryAccess(),false).copy();var entry=(CompoundTag)invalid.getListOrEmpty("fluids").getFirst();entry.getCompoundOrEmpty("storage").getCompoundOrEmpty("value").putLong("capacity",BUCKET);
        boolean refused=false;try{structure.getStorage().read(invalid,h.getLevel().registryAccess(),false,structure);}catch(IllegalStateException|IllegalArgumentException expected){refused=true;}h.assertTrue(refused,"Invalid five-bucket payload with one-bucket capacity is rejected");h.assertValueEqual(((FluidTankMountedStorage)structure.getStorage().getFluids().storages.get(BlockPos.ZERO)).getFluid().getAmount(),5*BUCKET,"Existing owned amount is not silently clamped or replaced");h.succeed();
    }
}
