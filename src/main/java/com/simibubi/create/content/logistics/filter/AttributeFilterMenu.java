package com.simibubi.create.content.logistics.filter;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute.ItemAttributeEntry;
import java.util.*;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

public final class AttributeFilterMenu extends AbstractContainerMenu {
    public static final ExtendedMenuType<AttributeFilterMenu,FilterMenu.Opening> TYPE=Registry.register(BuiltInRegistries.MENU,Create.asResource("attribute_filter"),new ExtendedMenuType<>((id,inventory,data)->new AttributeFilterMenu(id,inventory,data.filter(),data.slot()),FilterMenu.Opening.CODEC));
    private final Inventory inventory;
    private final ItemStack filter;
    private final int ownerSlot;
    private final SimpleContainer ghosts=new SimpleContainer(2);
    private final ContainerData options=new SimpleContainerData(1);
    private ItemStack lastReference=ItemStack.EMPTY;
    private List<ItemAttribute> available=List.of();
    private List<ItemAttributeEntry> selected;
    private boolean syncPending=true;
    private int revision;
    private Object recipeGeneration;
    public AttributeFilterMenu(int id,Inventory inventory,ItemStack filter,int ownerSlot){
        super(TYPE,id);this.inventory=inventory;this.filter=filter;this.ownerSlot=ownerSlot;selected=new ArrayList<>(filter.getOrDefault(AllDataComponents.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES,List.of()));options.set(0,filter.getOrDefault(AllDataComponents.ATTRIBUTE_FILTER_WHITELIST_MODE,AttributeFilterWhitelistMode.WHITELIST_DISJ).ordinal());addDataSlots(options);addStandardInventorySlots(inventory,51,107);
        ghosts.setItem(1,new ItemStack(Items.NAME_TAG));
        for(int i=0;i<2;i++)addSlot(new Slot(ghosts,i,16,27+i*35){@Override public boolean mayPlace(ItemStack stack){return false;}@Override public boolean mayPickup(Player player){return false;}@Override public boolean isFake(){return true;}});
    }
    public static void register(){AttributeFilterNetworking.register();}
    public AttributeFilterWhitelistMode mode(){return AttributeFilterWhitelistMode.values()[Math.clamp(options.get(0),0,2)];}
    public List<ItemAttribute> available(){return available;}
    public List<ItemAttributeEntry> selected(){return List.copyOf(selected);}
    public int revision(){return revision;}
    public void receive(AttributeFilterNetworking.Sync state){available=List.copyOf(state.available());selected=new ArrayList<>(state.selected());revision=state.revision();}
    @Override public boolean stillValid(Player player){return player.getInventory()==inventory&&inventory.getSelectedSlot()==ownerSlot&&inventory.getSelectedItem()==filter;}
    private void save(){if(inventory.player.level().isClientSide()||!stillValid(inventory.player))return;if(selected.isEmpty()&&mode()==AttributeFilterWhitelistMode.WHITELIST_DISJ){filter.remove(AllDataComponents.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES);filter.remove(AllDataComponents.ATTRIBUTE_FILTER_WHITELIST_MODE);}else{filter.set(AllDataComponents.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES,List.copyOf(selected));filter.set(AllDataComponents.ATTRIBUTE_FILTER_WHITELIST_MODE,mode());}inventory.setChanged();syncPending=true;}
    private void refresh(){
        if(!(inventory.player.level() instanceof net.minecraft.server.level.ServerLevel server))return;var generation=server.getServer().getRecipeManager().getRecipes();if(recipeGeneration==generation&&ItemStack.matches(lastReference,ghosts.getItem(0)))return;recipeGeneration=generation;lastReference=ghosts.getItem(0).copy();available=lastReference.isEmpty()?List.of():List.copyOf(ItemAttribute.getAllAttributes(lastReference,inventory.player.level()));revision++;syncPending=true;
    }
    private static boolean same(ItemAttribute first,ItemAttribute second,Player player){return ItemAttribute.saveStatic(first,player.registryAccess()).equals(ItemAttribute.saveStatic(second,player.registryAccess()));}
    public boolean addAttribute(Player player,int index,boolean inverted){
        return addAttribute(player,index,inverted,revision);
    }
    public boolean addAttribute(Player player,int index,boolean inverted,int expectedRevision){if(!stillValid(player)||player.level().isClientSide())return false;refresh();if(expectedRevision!=revision||index<0||index>=available.size())return false;var attribute=available.get(index);if(selected.stream().anyMatch(entry->same(entry.attribute(),attribute,player)))return false;selected.add(new ItemAttributeEntry(attribute,inverted));save();broadcastChanges();return true;}
    @Override public boolean clickMenuButton(Player player,int button){
        if(!stillValid(player)||button<0||button>3)return false;if(button==3)selected.clear();else options.set(0,button);save();broadcastChanges();return true;
    }
    @Override public void broadcastChanges(){super.broadcastChanges();refresh();if(syncPending&&inventory.player instanceof ServerPlayer server&&server.containerMenu==this){ServerPlayNetworking.send(server,new AttributeFilterNetworking.Sync(containerId,available,List.copyOf(selected),revision));syncPending=false;}}
    @Override public void clicked(int index,int button,ContainerInput input,Player player){
        if(!stillValid(player)&&!player.level().isClientSide())return;if(index==ownerSlot+27||index==37||input==ContainerInput.SWAP&&button==ownerSlot)return;
        if(index==36){if(input==ContainerInput.PICKUP||input==ContainerInput.QUICK_MOVE){ghosts.setItem(0,getCarried().copyWithCount(1));broadcastChanges();}return;}super.clicked(index,button,input,player);
    }
    @Override public ItemStack quickMoveStack(Player player,int index){if(index<0||index>36||index==ownerSlot+27)return ItemStack.EMPTY;if(index==36)ghosts.setItem(0,ItemStack.EMPTY);else ghosts.setItem(0,getSlot(index).getItem().copyWithCount(1));broadcastChanges();return ItemStack.EMPTY;}
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot){return slot.container==inventory&&slot.getContainerSlot()!=ownerSlot;}
    @Override public boolean canDragTo(Slot slot){return slot.container==inventory&&slot.getContainerSlot()!=ownerSlot;}
    @Override public void removed(Player player){save();super.removed(player);}
}
