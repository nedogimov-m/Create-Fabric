package com.simibubi.create.content.equipment.toolbox;

import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.base.*;
import net.minecraft.world.entity.player.Player;

/** Actual player main slots 9..35; incompatible equipped items never spill into another hotbar link. */
public final class ItemReturnInvWrapper extends CombinedStorage<ItemVariant,SingleSlotStorage<ItemVariant>> {
    public ItemReturnInvWrapper(Player player){super(mainSlots(player));}
    private static List<SingleSlotStorage<ItemVariant>> mainSlots(Player player){var storage=PlayerInventoryStorage.of(player);var slots=new ArrayList<SingleSlotStorage<ItemVariant>>(27);for(int slot=9;slot<36;slot++)slots.add(storage.getSlot(slot));return List.copyOf(slots);}
}
