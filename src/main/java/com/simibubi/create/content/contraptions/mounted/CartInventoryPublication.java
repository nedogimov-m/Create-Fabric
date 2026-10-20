package com.simibubi.create.content.contraptions.mounted;

import java.util.Optional;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Genuine native inventory endpoint. Planning never inserts a preview or claims publication success. */
public final class CartInventoryPublication {
    public static Optional<Plan> plan(ServerPlayer player,ItemStack proposed){
        if(player instanceof FakePlayer||player.isRemoved()||player.level().getServer().getPlayerList().getPlayer(player.getUUID())!=player||proposed.isEmpty()||proposed.getCount()!=1)return Optional.empty();
        var inventory=player.getInventory();var slots=inventory.getNonEquipmentItems();
        for(int index=0;index<slots.size();index++){var current=slots.get(index);if(!current.isEmpty()&&ItemStack.isSameItemSameComponents(current,proposed)&&current.getCount()<Math.min(inventory.getMaxStackSize(),current.getMaxStackSize()))return Optional.of(new Plan(player,inventory,slots,index,current,PackedCartData.detachedItemCopy(current),PackedCartData.detachedItemCopy(proposed)));}
        for(int index=0;index<slots.size();index++)if(slots.get(index).isEmpty())return Optional.of(new Plan(player,inventory,slots,index,slots.get(index),ItemStack.EMPTY,PackedCartData.detachedItemCopy(proposed)));
        return Optional.empty();
    }
    public static final class Plan {
        private final ServerPlayer player;private final Inventory inventory;private final NonNullList<ItemStack> slots;private final int slot;private final ItemStack previous,expected,proposed;
        private Plan(ServerPlayer player,Inventory inventory,NonNullList<ItemStack> slots,int slot,ItemStack previous,ItemStack expected,ItemStack proposed){this.player=player;this.inventory=inventory;this.slots=slots;this.slot=slot;this.previous=previous;this.expected=expected;this.proposed=proposed;}
        public boolean validate(){return player.isAlive()&&!player.isRemoved()&&player.level().getServer().getPlayerList().getPlayer(player.getUUID())==player&&player.getInventory()==inventory&&inventory.getNonEquipmentItems()==slots&&slots.get(slot)==previous&&ItemStack.matches(previous,expected);}
        /** Same callback-free commit as source revocation: actual stack is transferred to the actual native slot. */
        public void installActual(ItemStack actual){
            if(!validate()||actual.isEmpty()||actual.getCount()!=1||!ItemStack.matches(actual,proposed))throw new IllegalStateException("Native cart-item inventory endpoint changed before commit");
            if(previous.isEmpty())slots.set(slot,actual);else{previous.grow(1);actual.setCount(0);}
        }
        /** Network/menu callbacks may follow only after source pointers/receipt ownership have changed. */
        public void notifyNative(){inventory.setChanged();player.connection.send(inventory.createInventoryUpdatePacket(slot));}
        public int nativeSlot(){return slot;}
        public ServerPlayer actualPlayer(){return player;}
    }
    private CartInventoryPublication(){}
}
