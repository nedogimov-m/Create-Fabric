package com.simibubi.create.content.kinetics.deployer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Real non-hand player slots retain their addresses until a real server fake player owns them. */
public final class DeployerInventory {
    public record Entry(int slot, ItemStack item) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("Slot").forGetter(Entry::slot),
            ItemStack.CODEC.fieldOf("Item").forGetter(Entry::item)).apply(i, Entry::new));
    }
    public static final Codec<List<Entry>> CODEC = Entry.CODEC.listOf();

    /** The original 1.21 inventory serializer uses these semantic equipment addresses. */
    public static int originalSlot(int slot) {
        if (slot >= 0 && slot < 36) return slot;
        EquipmentSlot equipment = switch (slot) {
            case 100 -> EquipmentSlot.FEET;
            case 101 -> EquipmentSlot.LEGS;
            case 102 -> EquipmentSlot.CHEST;
            case 103 -> EquipmentSlot.HEAD;
            case 150 -> EquipmentSlot.OFFHAND;
            default -> throw new IllegalArgumentException("Invalid original player inventory slot " + slot);
        };
        for (var entry : Inventory.EQUIPMENT_SLOT_MAPPING.int2ObjectEntrySet())
            if (entry.getValue() == equipment) return entry.getIntKey();
        throw new IllegalArgumentException("Native player inventory lacks actual equipment slot " + equipment);
    }

    public static List<Entry> checked(List<Entry> entries, int selected) {
        if (selected < 0 || selected >= Inventory.SELECTION_SIZE)
            throw new IllegalArgumentException("Invalid saved selected Deployer slot");
        var seen = new HashSet<Integer>();
        for (var entry : entries) {
            int slot = entry.slot();
            if (slot == selected || slot < 0 || slot >= 36 && !Inventory.EQUIPMENT_SLOT_MAPPING.containsKey(slot)
                || !seen.add(slot) || entry.item().isEmpty())
                throw new IllegalArgumentException("Invalid or duplicate actual non-hand Deployer inventory slot " + slot);
        }
        return List.copyOf(entries);
    }

    static List<Entry> capture(DeployerFakePlayer player) {
        var result = new ArrayList<Entry>();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot == inventory.getSelectedSlot()) continue;
            var stack = inventory.getItem(slot);
            if (!stack.isEmpty()) result.add(new Entry(slot, stack.copy()));
        }
        return checked(result, inventory.getSelectedSlot());
    }

    static void hydrate(DeployerFakePlayer player, List<Entry> entries, int selected, ItemStack held) {
        checked(entries, selected);
        var inventory = player.getInventory();
        inventory.setSelectedSlot(selected);
        for (var entry : entries) inventory.setItem(entry.slot(), entry.item().copy());
        inventory.setItem(selected, held.copy());
    }

    static void clear(DeployerFakePlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) inventory.setItem(slot, ItemStack.EMPTY);
    }
    private DeployerInventory() {}
}
