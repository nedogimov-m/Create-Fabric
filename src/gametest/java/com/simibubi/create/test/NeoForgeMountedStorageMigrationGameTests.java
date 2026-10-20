package com.simibubi.create.test;

import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.content.contraptions.NeoForgeMountedStorageImport;
import com.simibubi.create.content.contraptions.minecart.TrainCargoManager;
import com.simibubi.create.content.fluids.tank.storage.FluidTankMountedStorage;
import com.simibubi.create.foundation.item.MountedItemInventory;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/** Genuine original storage schemas and real native storage/transactions; execution UNRUN. */
public final class NeoForgeMountedStorageMigrationGameTests {
    private static CompoundTag item(int count) {
        var result = new CompoundTag(); result.putString("id", "minecraft:diamond"); result.putInt("count", count);
        var components = new CompoundTag(); var custom = new CompoundTag(); custom.putString("owner", "Original cargo components");
        components.put("minecraft:custom_data", custom); result.put("components", components); return result;
    }
    private static CompoundTag slots(int size, int slot, int count) { var result = new CompoundTag(); var values = new CompoundTag(); values.put(Integer.toString(slot), item(count)); result.put("items", values); result.putInt("size", size); return result; }
    private static CompoundTag storage(String type, CompoundTag value) { var result = new CompoundTag(); result.putString("type", type); result.put("value", value); return result; }
    private static CompoundTag fluid(int capacity, int amount) {
        var result = new CompoundTag(); result.putString("type", "create:fluid_tank"); result.putInt("capacity", capacity);
        var value = new CompoundTag(); value.putString("id", "minecraft:water"); value.putInt("amount", amount);
        var components = new CompoundTag(); var custom = new CompoundTag(); custom.putString("owner", "Original fluid components");
        components.put("minecraft:custom_data", custom); value.put("components", components); result.put("fluid", value); return result;
    }
    private static CompoundTag row(int x, CompoundTag storage) { var result = new CompoundTag(); var pos = new CompoundTag(); pos.putInt("X", x); pos.putInt("Y", 0); pos.putInt("Z", 0); result.put("pos", pos); result.put("storage", storage); return result; }
    private static CompoundTag cargo(CompoundTag... items) { var result = new CompoundTag(); result.putInt("TicksSinceLastExchange", 87); var values = new ListTag(); for (var item : items) values.add(item); result.put("items", values); return result; }

    @GameTest public void originalFiniteMountedItemTypesKeepSparseSlotsCountsAndComponents(GameTestHelper h) {
        var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for (String type : List.of("simple", "chest", "dispenser", "vault")) {
            var source = storage("create:" + type, slots(40, 39, 7)); var untouched = source.copy();
            var data = NeoForgeMountedStorageImport.itemStorage(source, 3955, h.getLevel().registryAccess()); var actual = MountedItemStorage.CODEC.parse(ops, data).getOrThrow();
            h.assertTrue(actual.getSlotCount() == 40 && actual.getStackInSlot(0).isEmpty() && actual.getStackInSlot(39).is(Items.DIAMOND)
                    && actual.getStackInSlot(39).getCount() == 7 && actual.getStackInSlot(39).get(DataComponents.CUSTOM_DATA).copyTag().getString("owner").orElseThrow().equals("Original cargo components")
                    && source.equals(untouched), "Actual source " + type + " retains its original last slot/size/count/components without source mutation");
        }
        h.succeed();
    }

    @GameTest public void originalDepotAndCreativeTemplateRetainActualTypedValues(GameTestHelper h) {
        for (String type : List.of("depot", "creative_crate")) {
            var source = storage("create:" + type, item(7)); var data = NeoForgeMountedStorageImport.itemStorage(source, 3955, h.getLevel().registryAccess());
            var value = data.getCompoundOrEmpty("value");
            h.assertTrue(data.getString("type").orElseThrow().equals("create:" + type) && value.getIntOr("count", 1) == 7
                    && value.getCompoundOrEmpty("components").getCompoundOrEmpty("minecraft:custom_data").getString("owner").orElseThrow().equals("Original cargo components"),
                "Source depot value or explicit real creative template keeps its exact saved type/count/components");
        }
        h.succeed();
    }

    @GameTest public void originalToolboxKeepsEightFiltersAndFullThirtyTwoSlotInventory(GameTestHelper h) {
        var value = new CompoundTag(); value.put("items", slots(32, 31, 7)); var filters = new ListTag();
        for (int index = 0; index < 8; index++) filters.add(index == 7 ? item(1) : new CompoundTag()); value.put("filters", filters);
        var data = NeoForgeMountedStorageImport.itemStorage(storage("create:toolbox", value), 3955, h.getLevel().registryAccess());
        var actual = MountedItemStorage.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), data).getOrThrow();
        int count = 0; for (int slot = 0; slot < actual.getSlotCount(); slot++) count += actual.getStackInSlot(slot).getCount();
        h.assertTrue(actual.getSlotCount() == 32 && count == 7 && data.getCompoundOrEmpty("value").getListOrEmpty("filters").size() == 8,
            "Native toolbox retains all original compartment filters and seven actual stock items through its genuine codec settling");
        h.succeed();
    }

    @GameTest public void actualFluidMillibucketsAndComponentsBecomeExactNativeDroplets(GameTestHelper h) {
        var source = fluid(4000, 1250); var unchanged = source.copy(); var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var data = NeoForgeMountedStorageImport.fluidStorage(source, h.getLevel().registryAccess()); var actual = (FluidTankMountedStorage) MountedFluidStorage.CODEC.parse(ops, data).getOrThrow();
        var components = DataComponentPatch.CODEC.parse(ops, source.getCompoundOrEmpty("fluid").getCompoundOrEmpty("components")).getOrThrow();
        h.assertTrue(actual.getCapacity() == 4000L * 81 && actual.getFluid().getAmount() == 1250L * 81
                && actual.getFluid().variant().equals(FluidVariant.of(Fluids.WATER, components)) && source.equals(unchanged),
            "Real source amount/capacity and component identity migrate with exact81 droplets per mB and no clamping/source mutation");
        h.succeed();
    }

    @GameTest public void largestOriginalIntFluidCapacityUsesNativeLongWithoutOverflow(GameTestHelper h) {
        var source = fluid(Integer.MAX_VALUE, Integer.MAX_VALUE); var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var actual = (FluidTankMountedStorage) MountedFluidStorage.CODEC.parse(ops, NeoForgeMountedStorageImport.fluidStorage(source, h.getLevel().registryAccess())).getOrThrow();
        h.assertTrue(actual.getCapacity() == (long) Integer.MAX_VALUE * 81 && actual.getFluid().getAmount() == actual.getCapacity(),
            "Actual source maximum int mB storage preserves all original fluid in long native units"); h.succeed();
    }

    @GameTest public void explicitOriginalCreativeFluidKeepsItsGenuineCapacityAndVariant(GameTestHelper h) {
        var source = fluid(4000, 4000); var value = source.copy(); value.remove("type"); var old = storage("create:creative_fluid_tank", value);
        var data = NeoForgeMountedStorageImport.fluidStorage(old, h.getLevel().registryAccess()); var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        MountedFluidStorage.CODEC.parse(ops, data).getOrThrow();
        h.assertTrue(data.getLongOr("capacity", -1) == 4000L * 81 && !FluidVariant.CODEC.parse(ops, data.get("variant")).getOrThrow().isBlank(),
            "A real original creative tank retains its explicit native supply/capacity rather than manufacturing a creative flag for ordinary stock");
        h.succeed();
    }

    @GameTest public void unknownFluidTypeOvercapacityAndUnreviewedMountedSchemasFailExplicitly(GameTestHelper h) {
        var unknown = fluid(4000, 1250); unknown.getCompoundOrEmpty("fluid").putString("id", "missing:fluid");
        var excess = fluid(1000, 1250); var future = fluid(4000, 1250); future.putInt("FutureVersion", 9);
        for (var source : List.of(unknown, excess, future)) {
            var original = source.copy(); boolean rejected = false;
            try { NeoForgeMountedStorageImport.fluidStorage(source, h.getLevel().registryAccess()); } catch (RuntimeException expected) { rejected = true; }
            h.assertTrue(rejected && original.equals(source), "Unknown source fluid/schema or excess amount cannot silently become blank/clamped native cargo");
        }
        var unsupported = storage("other:unreviewed", slots(1, 0, 7)); var original = unsupported.copy(); boolean rejected = false;
        try { NeoForgeMountedStorageImport.itemStorage(unsupported, 3955, h.getLevel().registryAccess()); } catch (IllegalArgumentException expected) { rejected = true; }
        h.assertTrue(rejected && unsupported.equals(original), "Unreviewed original storage ownership cannot be aliased to a different native storage type or emptied"); h.succeed();
    }

    @GameTest public void duplicateCargoPositionsAndUnknownDataVersionCannotEstablishReplacementStock(GameTestHelper h) {
        var entry = row(0, storage("create:simple", slots(1, 0, 7))); var source = cargo(entry, entry.copy()); var original = source.copy(); boolean rejected = false;
        try { NeoForgeMountedStorageImport.cargo(source, 3955, h.getLevel().registryAccess()); } catch (IllegalArgumentException expected) { rejected = true; }
        h.assertTrue(rejected && original.equals(source), "Two source rows claiming one local cargo position fail before native manager publication");
        rejected = false; try { NeoForgeMountedStorageImport.cargo(cargo(entry), 999999, h.getLevel().registryAccess()); } catch (IllegalArgumentException expected) { rejected = true; }
        h.assertTrue(rejected, "Unknown actual source data version cannot authorize a mounted cargo conversion"); h.succeed();
    }

    @GameTest public void migratedActualCargoUsesRealNativeTransactionsAndIdleTracking(GameTestHelper h) throws Exception {
        var source = cargo(row(1, storage("create:chest", slots(27, 0, 7)))); var original = source.copy();
        var converted = NeoForgeMountedStorageImport.cargo(source, 3955, h.getLevel().registryAccess()); var proposal = new TrainCargoManager(); proposal.read(converted, h.getLevel().registryAccess(), false, null);
        h.assertTrue(!proposal.ownsContents()&&proposal.getAllItemStorages().get(new BlockPos(1,0,0)).getStackInSlot(0).isEmpty(),"Pure decoded cargo proposal has no invented physical owner or extraction authority");
        var train=com.simibubi.create.content.trains.entity.NeoForgeRailwayMigrationGameTests.actualMigratedCargoOwner(h,source);
        var actual=train.carriages.getFirst().storage;
        var receiver = new MountedItemInventory(1); var view = actual.getAllItemStorages().get(new BlockPos(1,0,0));
        h.assertTrue(actual.ownsContents()&&view.getSlotCount()==27&&view.getStackInSlot(0).getCount()==7&&view.getStackInSlot(0).get(DataComponents.CUSTOM_DATA).copyTag().getString("owner").orElseThrow().equals("Original cargo components"),"Genuine native Train/Carriage source/body validation alone establishes the one original seven-item27-slot cargo owner");
        var variant = ItemVariant.of(view.getStackInSlot(0));
        try (var tx = Transaction.openOuter()) { h.assertTrue(view.extract(variant, 3, tx) == 3 && receiver.insert(variant, 3, tx) == 3, "Real native provisional transfer moved three original items"); }
        h.assertTrue(view.getStackInSlot(0).getCount() == 7 && receiver.getStack(0).isEmpty() && actual.getTicksSinceLastExchange() == 87,
            "Aborted actual native transaction restores both original stocks and source idle timer");
        try (var tx = Transaction.openOuter()) { h.assertTrue(view.extract(variant, 3, tx) == 3 && receiver.insert(variant, 3, tx) == 3, "Real native transfer moved original stock"); tx.commit(); }
        h.assertTrue(view.getStackInSlot(0).getCount() == 4 && receiver.getStack(0).getCount() == 3 && actual.getTicksSinceLastExchange() == 0 && source.equals(original),
            "Only committed real native exchange resets idle; exactly seven original items remain across actual storage owners"); h.succeed();
    }
}
