package com.simibubi.create.content.redstone.link.controller;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Twelve native synchronized ghost slots; no sample can become a physical item. */
public final class LinkedControllerMenu extends AbstractContainerMenu{
    public record Opening(ItemStack controller,int ownerSlot){
        public static final StreamCodec<RegistryFriendlyByteBuf,Opening> CODEC=StreamCodec.composite(ItemStack.STREAM_CODEC,Opening::controller,ByteBufCodecs.VAR_INT,Opening::ownerSlot,Opening::new);
    }
    public static final ExtendedMenuType<LinkedControllerMenu,Opening> TYPE=Registry.register(BuiltInRegistries.MENU,Create.asResource("linked_controller"),new ExtendedMenuType<>((id,inventory,data)->new LinkedControllerMenu(id,inventory,data.controller,data.ownerSlot),Opening.CODEC));
    private final Inventory inventory;private final ItemStack controller;private final int ownerSlot;
    private final SimpleContainer ghosts=new SimpleContainer(LinkedControllerItem.SLOTS);
    public LinkedControllerMenu(int id,Inventory inventory,ItemStack controller,int ownerSlot){
        super(TYPE,id);this.inventory=inventory;this.controller=controller;this.ownerSlot=ownerSlot;
        var samples=LinkedControllerItem.getFrequencyItems(controller);for(int slot=0;slot<samples.size();slot++)ghosts.setItem(slot,samples.get(slot));
        addStandardInventorySlots(inventory,8,131);
        for(int slot=0;slot<12;slot++){int column=slot/2,row=slot%2;addSlot(new Slot(ghosts,slot,12+column*24+(column>3?11:0),34+row*18){
            @Override public boolean mayPlace(ItemStack stack){return false;}@Override public boolean mayPickup(Player player){return false;}@Override public boolean isFake(){return true;}
        });}
    }
    public static void register(){}
    @Override public boolean stillValid(Player player){return inventory==player.getInventory()&&inventory.getSelectedSlot()==ownerSlot
        &&(player.level().isClientSide()?inventory.getSelectedItem().getItem()instanceof LinkedControllerItem:inventory.getSelectedItem()==controller);}
    private void save(){if(!inventory.player.level().isClientSide()&&stillValid(inventory.player)){LinkedControllerItem.setFrequencyItems(controller,ghosts.getItems());inventory.setChanged();}}
    @Override public boolean clickMenuButton(Player player,int button){if(button!=0||!stillValid(player))return false;ghosts.clearContent();save();broadcastChanges();return true;}
    @Override public void clicked(int index,int button,ContainerInput input,Player player){
        if(!player.level().isClientSide()&&!stillValid(player))return;
        if(index==27+ownerSlot||input==ContainerInput.SWAP&&button==ownerSlot)return;
        if(index>=36&&index<48){if(input==ContainerInput.PICKUP||input==ContainerInput.QUICK_MOVE){ghosts.setItem(index-36,getCarried().isEmpty()?ItemStack.EMPTY:getCarried().copyWithCount(1));save();broadcastChanges();}return;}
        super.clicked(index,button,input,player);
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(!stillValid(player)||index<0||index>=48||index==27+ownerSlot)return ItemStack.EMPTY;
        if(index>=36)ghosts.setItem(index-36,ItemStack.EMPTY);
        else{var sample=getSlot(index).getItem();if(!sample.isEmpty())for(int slot=0;slot<12;slot++)if(ghosts.getItem(slot).isEmpty()){ghosts.setItem(slot,sample.copyWithCount(1));break;}}
        save();broadcastChanges();return ItemStack.EMPTY;
    }
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot){return slot.container==inventory&&slot.getContainerSlot()!=ownerSlot;}
    @Override public boolean canDragTo(Slot slot){return slot.container==inventory&&slot.getContainerSlot()!=ownerSlot;}
    @Override public void removed(Player player){save();super.removed(player);}
}
