package com.simibubi.create.test;

import com.mojang.serialization.Codec;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.item.MountedItemInventory;
import java.util.ArrayList;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTypes;

/** Actual persistent native BE attachments expose real Fabric plain inventories, like a native addon. */
public final class FallbackStorageTestFixtures implements ModInitializer {
    private static final Codec<SimpleContainer> CODEC = ItemStack.OPTIONAL_CODEC.listOf().xmap(
        values -> new SimpleContainer(values.toArray(ItemStack[]::new)), container -> {
            var result = new ArrayList<ItemStack>();
            for (int slot = 0; slot < container.getContainerSize(); slot++) result.add(container.getItem(slot).copy());
            return result;
        });
    static final AttachmentType<SimpleContainer> SIMPLE = AttachmentRegistry.<SimpleContainer>builder().persistent(CODEC)
        .buildAndRegister(Create.asResource("test_fallback_simple_container"));
    static final AttachmentType<MountedItemInventory> MOUNTED = AttachmentRegistry.<MountedItemInventory>builder().persistent(MountedItemInventory.CODEC)
        .buildAndRegister(Create.asResource("test_fallback_mounted_inventory"));

    @Override public void onInitialize() {
        ItemStorage.SIDED.registerForBlockEntity((be, side) -> {
            var owner = (AttachmentTarget) be;
            var mounted = owner.getAttached(MOUNTED); if (mounted != null) return mounted;
            var simple = owner.getAttached(SIMPLE); return simple == null ? null : ContainerStorage.of(simple, side);
        }, BlockEntityTypes.DAYLIGHT_DETECTOR);
    }
    static SimpleContainer simple(BlockEntity owner, int size, int slot, ItemStack actual) {
        var container = new SimpleContainer(size); container.setItem(slot, actual);
        ((AttachmentTarget) owner).setAttached(SIMPLE, container); owner.setChanged(); return container;
    }
    static MountedItemInventory mounted(BlockEntity owner, int size, int slot, ItemStack actual) {
        var inventory = new MountedItemInventory(size); inventory.setStack(slot, actual);
        ((AttachmentTarget) owner).setAttached(MOUNTED, inventory); owner.setChanged(); return inventory;
    }
}
