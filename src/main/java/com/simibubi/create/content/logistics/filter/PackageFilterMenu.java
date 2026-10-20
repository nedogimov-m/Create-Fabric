package com.simibubi.create.content.logistics.filter;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.box.PackageItem;
import net.fabricmc.fabric.api.menu.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** The server edits only the exact filter stack and selected slot that opened the menu. */
public final class PackageFilterMenu extends AbstractContainerMenu {
    public static final int MAX_ADDRESS_LENGTH=25;
    public record Opening(ItemStack filter,int slot){public static final StreamCodec<RegistryFriendlyByteBuf,Opening> CODEC=StreamCodec.composite(ItemStack.STREAM_CODEC,Opening::filter,ByteBufCodecs.VAR_INT,Opening::slot,Opening::new);}
    public record Update(int menu,String address) implements CustomPacketPayload {
        public static final Type<Update> TYPE=new Type<>(Create.asResource("package_filter_address"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Update> CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,Update::menu,ByteBufCodecs.stringUtf8(MAX_ADDRESS_LENGTH),Update::address,Update::new);
        @Override public Type<Update> type(){return TYPE;}
    }
    public static final ExtendedMenuType<PackageFilterMenu,Opening> TYPE=Registry.register(BuiltInRegistries.MENU,Create.asResource("package_filter"),new ExtendedMenuType<>((id,inventory,data)->new PackageFilterMenu(id,inventory,data.filter,data.slot),Opening.CODEC));
    private final Inventory inventory;private final ItemStack filter;private final int ownerSlot;private String address;
    public PackageFilterMenu(int id,Inventory inventory,ItemStack filter,int slot){super(TYPE,id);this.inventory=inventory;this.filter=filter;ownerSlot=slot;address=PackageItem.getAddress(filter);addStandardInventorySlots(inventory,40,101);}
    public String address(){return address;}
    @Override public boolean stillValid(Player player){return player.getInventory()==inventory&&inventory.getSelectedSlot()==ownerSlot&&inventory.getSelectedItem()==filter&&filter.is(AllItems.PACKAGE_FILTER);}
    public boolean updateAddress(Player player,String value){
        if(!stillValid(player)||value.length()>MAX_ADDRESS_LENGTH)return false;address=value;
        if(!player.level().isClientSide()){if(value.isBlank())filter.remove(AllDataComponents.PACKAGE_ADDRESS);else filter.set(AllDataComponents.PACKAGE_ADDRESS,value);inventory.setChanged();broadcastChanges();}return true;
    }
    @Override public boolean clickMenuButton(Player player,int button){return button==0&&updateAddress(player,"");}
    @Override public void clicked(int index,int button,ContainerInput input,Player player){if(!stillValid(player)&&!player.level().isClientSide())return;if(index==ownerSlot+27||input==ContainerInput.SWAP&&button==ownerSlot)return;super.clicked(index,button,input,player);}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot){return slot.container==inventory&&slot.getContainerSlot()!=ownerSlot;}
    @Override public boolean canDragTo(Slot slot){return slot.container==inventory&&slot.getContainerSlot()!=ownerSlot;}
    @Override public void removed(Player player){super.removed(player);if(player instanceof net.minecraft.server.level.ServerPlayer)player.inventoryMenu.broadcastFullState();}
    public static void register(){PayloadTypeRegistry.serverboundPlay().register(Update.TYPE,Update.CODEC);ServerPlayNetworking.registerGlobalReceiver(Update.TYPE,(packet,context)->{if(context.player().containerMenu instanceof PackageFilterMenu menu&&menu.containerId==packet.menu)menu.updateAddress(context.player(),packet.address);});}
}
