package com.simibubi.create.content.logistics.filter;

import com.simibubi.create.*;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** Native synchronized ghost menu. Ghost samples can never be extracted as real items. */
public final class FilterMenu extends AbstractContainerMenu {
    public record Opening(ItemStack filter,int slot){
        public static final StreamCodec<RegistryFriendlyByteBuf,Opening> CODEC=StreamCodec.composite(ItemStack.STREAM_CODEC,Opening::filter,ByteBufCodecs.VAR_INT,Opening::slot,Opening::new);
    }
    public static final ExtendedMenuType<FilterMenu,Opening> TYPE=Registry.register(BuiltInRegistries.MENU,Create.asResource("filter"),new ExtendedMenuType<>((id,inventory,data)->new FilterMenu(id,inventory,data.filter,data.slot),Opening.CODEC));
    private final Inventory inventory;
    private final ItemStack filter;
    private final int ownerSlot;
    private final SimpleContainer ghosts=new SimpleContainer(ListFilterItem.SLOTS);
    private final ContainerData options=new SimpleContainerData(2);
    public FilterMenu(int id,Inventory inventory,ItemStack filter,int ownerSlot){
        super(TYPE,id);this.inventory=inventory;this.filter=filter;this.ownerSlot=ownerSlot;
        var items=ListFilterItem.getFilterItems(filter);for(int i=0;i<items.size();i++)ghosts.setItem(i,items.get(i));options.set(0,filter.getOrDefault(AllDataComponents.FILTER_ITEMS_BLACKLIST,false)?1:0);options.set(1,filter.getOrDefault(AllDataComponents.FILTER_ITEMS_RESPECT_NBT,false)?1:0);addDataSlots(options);
        addStandardInventorySlots(inventory,38,121);
        for(int i=0;i<ListFilterItem.SLOTS;i++)addSlot(new Slot(ghosts,i,23+i%9*18,25+i/9*18){
            @Override public boolean mayPlace(ItemStack stack){return false;}
            @Override public boolean mayPickup(Player player){return false;}
            @Override public boolean isFake(){return true;}
        });
    }
    public static void register(){}
    public boolean blacklist(){return options.get(0)!=0;}
    public boolean respectData(){return options.get(1)!=0;}
    @Override public boolean stillValid(Player player){return player.getInventory()==inventory&&inventory.getSelectedSlot()==ownerSlot&&inventory.getSelectedItem()==filter;}
    private void save(){
        if(inventory.player.level().isClientSide()||!stillValid(inventory.player))return;
        if(ghosts.isEmpty())filter.remove(AllDataComponents.FILTER_ITEMS);else filter.set(AllDataComponents.FILTER_ITEMS,ItemContainerContents.fromItems(ghosts.getItems()));
        if(blacklist()||respectData()||!ghosts.isEmpty()){filter.set(AllDataComponents.FILTER_ITEMS_BLACKLIST,blacklist());filter.set(AllDataComponents.FILTER_ITEMS_RESPECT_NBT,respectData());}else{filter.remove(AllDataComponents.FILTER_ITEMS_BLACKLIST);filter.remove(AllDataComponents.FILTER_ITEMS_RESPECT_NBT);}inventory.setChanged();
    }
    @Override public boolean clickMenuButton(Player player,int button){if(!stillValid(player)||button<0||button>4)return false;if(button==4)ghosts.clearContent();else options.set(button/2,button%2);save();broadcastChanges();return true;}
    @Override public void clicked(int index,int button,ContainerInput input,Player player){
        if(!stillValid(player)&&!player.level().isClientSide())return;
        if(index==ownerSlot+27||input==ContainerInput.SWAP&&button==ownerSlot)return;
        if(index>=36&&index<slots.size()){
            if(input==ContainerInput.PICKUP||input==ContainerInput.QUICK_MOVE){ghosts.setItem(index-36,getCarried().copyWithCount(1));save();broadcastChanges();}return;
        }
        super.clicked(index,button,input,player);
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size()||index==ownerSlot+27)return ItemStack.EMPTY;
        if(index>=36){ghosts.setItem(index-36,ItemStack.EMPTY);save();return ItemStack.EMPTY;}
        var sample=getSlot(index).getItem();if(sample.isEmpty())return ItemStack.EMPTY;
        for(var entry:ghosts.getItems())if(com.simibubi.create.foundation.item.ItemSampleEquality.same(entry,sample))return ItemStack.EMPTY;
        for(int i=0;i<ghosts.getContainerSize();i++)if(ghosts.getItem(i).isEmpty()){ghosts.setItem(i,sample.copyWithCount(1));save();break;}return ItemStack.EMPTY;
    }
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot){return slot.container==inventory&&slot.getContainerSlot()!=ownerSlot;}
    @Override public boolean canDragTo(Slot slot){return slot.container==inventory&&slot.getContainerSlot()!=ownerSlot;}
    @Override public void removed(Player player){save();super.removed(player);}
}
