package com.simibubi.create.test;

import com.simibubi.create.AllMountedStorageTypes;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.NeoForgeMountedStorageImport;
import com.simibubi.create.foundation.item.MountedItemInventory;
import com.simibubi.create.impl.contraption.storage.FallbackMountedStorage;
import com.simibubi.create.impl.contraption.storage.MountedItemStorageFallbackProvider;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.DaylightDetectorBlockEntity;

/** Real persistent BE providers, genuine world capture/restore and native transaction cases; execution UNRUN. */
public final class FallbackMountedStorageGameTests {
    private static final BlockPos P = new BlockPos(3, 5, 3), T = P.south(3);
    private static ItemStack named(int count) { var result = new ItemStack(Items.DIAMOND, count); result.set(DataComponents.CUSTOM_NAME, Component.literal("Original fallback stock")); return result; }
    private static DaylightDetectorBlockEntity fixture(GameTestHelper h) { h.setBlock(P, Blocks.DAYLIGHT_DETECTOR); return h.getBlockEntity(P, DaylightDetectorBlockEntity.class); }
    private static BearingContraption assemble(GameTestHelper h) throws Exception { var actual = new BearingContraption(false, Direction.EAST); h.assertTrue(actual.assemble(h.getLevel(), h.absolutePos(P.west())), "Genuine native bearing capture accepted actual fallback block"); return actual; }
    private static MountedItemStorage storage(Contraption structure) { return structure.getStorage().getAllItemStorages().get(BlockPos.ZERO); }

    @GameTest public void actualPlainFabricBackendsAreEligibleAndSpecialHandlersRemainExcluded(GameTestHelper h) {
        h.assertTrue(FallbackMountedStorage.isValid(ContainerStorage.of(new SimpleContainer(3), null))
                && FallbackMountedStorage.isValid(new MountedItemInventory(3)), "Real standard native plain inventories are fallback candidates");
        h.setBlock(P, Blocks.BARREL); var barrel = h.getBlockEntity(P, BarrelBlockEntity.class);
        h.assertTrue(!FallbackMountedStorage.isValid(ContainerStorage.of(barrel, null))
                && !FallbackMountedStorage.isValid(ContainerStorage.of(new SimpleContainer(3) {}, null)),
            "Unrelated real BE inventory classes and custom SimpleContainer subclasses do not gain fallback eligibility"); h.succeed();
    }

    @GameTest public void realFallbackProviderRemainsBehindExplicitAndLaterAddonAssociations(GameTestHelper h) {
        h.assertTrue(MountedItemStorageType.REGISTRY.get(Blocks.DAYLIGHT_DETECTOR) == AllMountedStorageTypes.FALLBACK,
            "Actual stock registry resolves an otherwise-unassociated block to the genuine fallback type");
        var actual = SimpleRegistry.<Block, MountedItemStorageType<?>>create(); actual.registerProvider(block -> AllMountedStorageTypes.FALLBACK);
        var enabled = new AtomicBoolean(true); actual.registerProvider(block -> enabled.get() ? AllMountedStorageTypes.SIMPLE : null);
        h.assertTrue(actual.get(Blocks.DAYLIGHT_DETECTOR) == AllMountedStorageTypes.SIMPLE, "A genuine later addon provider takes priority over fallback");
        enabled.set(false); actual.invalidate(); h.assertTrue(actual.get(Blocks.DAYLIGHT_DETECTOR) == AllMountedStorageTypes.FALLBACK,
            "Real provider cache invalidation exposes fallback when the addon no longer claims the block");
        actual.register(Blocks.DAYLIGHT_DETECTOR, AllMountedStorageTypes.CHEST);
        h.assertTrue(actual.get(Blocks.DAYLIGHT_DETECTOR) == AllMountedStorageTypes.CHEST, "Explicit actual association wins over all providers"); h.succeed();
    }

    @GameTest public void genuineLoadedBlacklistTagDisablesOnlyTheFallbackProvider(GameTestHelper h) {
        h.assertTrue(Blocks.NOTE_BLOCK.builtInRegistryHolder().is(MountedItemStorageFallbackProvider.BLACKLIST)
                && MountedItemStorageType.REGISTRY.get(Blocks.NOTE_BLOCK) == null,
            "Actual loaded native data-pack blacklist is respected by the source fallback provider");
        h.assertTrue(MountedItemStorageType.REGISTRY.get(Blocks.DAYLIGHT_DETECTOR) == AllMountedStorageTypes.FALLBACK,
            "Unlisted actual blocks retain genuine fallback eligibility"); h.succeed();
    }

    @GameTest public void genuineCaptureRefreshSaveLoadAndWorldRestoreKeepOriginalFallbackStock(GameTestHelper h) throws Exception {
        var owner = fixture(h); var plain = FallbackStorageTestFixtures.simple(owner, 3, 2, named(7)); var structure = assemble(h); var early = storage(structure);
        h.assertTrue(early instanceof FallbackMountedStorage && early.type == AllMountedStorageTypes.FALLBACK && early.getStackInSlot(2).isEmpty(),
            "Actual world provider remains the sole stock owner while a bearing capture is only a proposal");
        plain.setItem(2, named(11)); owner.setChanged();
        h.assertTrue(structure.tryRemoveBlocksFromWorld(h.getLevel(), BlockPos.ZERO), "Genuine world removal transferred latest actual fallback stock");
        var live = storage(structure); h.assertTrue(live.getStackInSlot(2).getCount() == 11 && early.getStackInSlot(2).isEmpty(),
            "Fresh accepted capture owns all eleven originals and old mounted proposal handles remain expired");
        var receiverPos = P.north(3); h.setBlock(receiverPos, Blocks.BARREL); var receiver = h.getBlockEntity(receiverPos, BarrelBlockEntity.class);
        var receipt = ContainerStorage.of(receiver, null);
        try (var tx = Transaction.openOuter()) { h.assertTrue(live.extract(ItemVariant.of(named(1)), 3, tx) == 3
            && receipt.insert(ItemVariant.of(named(1)), 3, tx) == 3, "Actual mounted fallback transferred three originals into a real world barrel"); tx.commit(); }
        live.setStackInSlot(0, new ItemStack(Items.APPLE, 2));
        var restored = Contraption.fromNBT(h.getLevel(), structure.writeNBT(h.getLevel().registryAccess(), false), false);
        h.assertTrue(storage(restored) instanceof FallbackMountedStorage && storage(restored).getStackInSlot(2).getCount() == 8,
            "Actual persistent fallback type/codec retains mutable contents through native contraption save decode");
        var held = storage(restored).getSlot(2);
        h.assertTrue(restored.tryAddBlocksToWorld(h.getLevel(), new StructureTransform(h.absolutePos(T), 0, 90, 0)), "Genuine transformed world restoration completed");
        var actual = h.getBlockEntity(T, DaylightDetectorBlockEntity.class); var inventory = ((AttachmentTarget) actual).getAttached(FallbackStorageTestFixtures.SIMPLE);
        h.assertTrue(inventory != null && inventory.getItem(2).getCount() == 8 && inventory.getItem(0).getCount() == 2
                && ItemStack.isSameItemSameComponents(inventory.getItem(2), named(1)) && receiver.getItem(0).getCount() == 3 && held.getAmount() == 0,
            "Real restored persistent native BE owns changed original stock once; old captured BE NBT cannot restore eleven items and old mounted handles expire"); h.succeed();
    }

    @GameTest public void actualMountedInventoryProviderAlsoRestoresItsExactOriginalComponents(GameTestHelper h) throws Exception {
        var owner = fixture(h); FallbackStorageTestFixtures.mounted(owner, 3, 1, named(7)); var structure = assemble(h);
        h.assertTrue(structure.tryRemoveBlocksFromWorld(h.getLevel(), BlockPos.ZERO), "Real plain MountedItemInventory provider transferred its actual stock");
        h.assertTrue(storage(structure) instanceof FallbackMountedStorage && storage(structure).getStackInSlot(1).getCount() == 7,
            "Native fallback type owns the isolated captured actual inventory");
        h.assertTrue(structure.tryAddBlocksToWorld(h.getLevel(), new StructureTransform(h.absolutePos(T), 0, 0, 0)), "Actual native provider restoration succeeded");
        var restored = ((AttachmentTarget) h.getBlockEntity(T, DaylightDetectorBlockEntity.class)).getAttached(FallbackStorageTestFixtures.MOUNTED);
        h.assertTrue(restored != null && restored.getStack(1).getCount() == 7 && ItemStack.isSameItemSameComponents(restored.getStack(1), named(1)),
            "Actual restored native provider contains all original counts/components"); h.succeed();
    }

    @GameTest public void changedActualProviderSizeCannotSilentlyConsumeMountedFallbackStock(GameTestHelper h) {
        var owner = fixture(h); FallbackStorageTestFixtures.simple(owner, 2, 1, named(7)); var source = new MountedItemInventory(3); source.setStack(2, named(9));
        var fallback = new FallbackMountedStorage(source); boolean rejected = false;
        try { fallback.unmount(h.getLevel(), h.getBlockState(P), h.absolutePos(P), owner); } catch (IllegalStateException expected) { rejected = true; }
        var actual = ((AttachmentTarget) owner).getAttached(FallbackStorageTestFixtures.SIMPLE);
        h.assertTrue(rejected && fallback.getStackInSlot(2).getCount() == 9 && actual.getItem(1).getCount() == 7,
            "Mismatched genuine native destination size fails before either actual stock owner is overwritten or discarded"); h.succeed();
    }

    @GameTest public void removedNativeBlockOwnerCannotReceiveAStaleFallbackWrite(GameTestHelper h) {
        var owner = fixture(h); var actual = FallbackStorageTestFixtures.simple(owner, 3, 1, named(7)); var contents = new MountedItemInventory(3); contents.setStack(1, named(9));
        var source = new FallbackMountedStorage(contents); h.setBlock(P, Blocks.AIR); boolean rejected = false;
        try { source.unmount(h.getLevel(), Blocks.DAYLIGHT_DETECTOR.defaultBlockState(), h.absolutePos(P), owner); } catch (IllegalStateException expected) { rejected = true; }
        h.assertTrue(rejected && source.getStackInSlot(1).getCount() == 9 && actual.getItem(1).getCount() == 7,
            "A stale removed BE pointer cannot be treated as the actual registered destination of a native fallback transfer"); h.succeed();
    }

    @GameTest public void originalFallbackSaveUsesItsActualRegisteredNativeTypeWithoutAlias(GameTestHelper h) {
        var source = new CompoundTag(); source.putString("type", "create:fallback"); var slots = new CompoundTag(); slots.putInt("size", 3);
        var items = new CompoundTag(); var item = new CompoundTag(); item.putString("id", "minecraft:diamond"); item.putInt("count", 7); items.put("2", item); slots.put("items", items); source.put("value", slots);
        var data = NeoForgeMountedStorageImport.itemStorage(source, 3955, h.getLevel().registryAccess());
        var actual = MountedItemStorage.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), data).getOrThrow();
        h.assertTrue(actual instanceof FallbackMountedStorage && actual.type == AllMountedStorageTypes.FALLBACK && actual.getSlotCount() == 3
                && actual.getStackInSlot(2).is(Items.DIAMOND) && actual.getStackInSlot(2).getCount() == 7 && data.getString("type").orElseThrow().equals("create:fallback"),
            "Real original fallback schema retains its actual native type/slots/material instead of a simple/empty/fictional loader alias"); h.succeed();
    }
}
