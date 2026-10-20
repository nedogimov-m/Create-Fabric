package com.simibubi.create.content.equipment.toolbox;

import com.simibubi.create.Create;
import java.util.UUID;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Real loaded world owner and vanilla menu slots, including all source hidden compartment slots. */
public final class ToolboxMenu extends AbstractContainerMenu {
    public record Opening(BlockPos pos,UUID owner,ToolboxInventory stock){public static final StreamCodec<RegistryFriendlyByteBuf,Opening> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,Opening::pos,UUIDUtil.STREAM_CODEC,Opening::owner,ToolboxInventory.STREAM_CODEC,Opening::stock,Opening::new);}
    public static final ExtendedMenuType<ToolboxMenu,Opening> TYPE=Registry.register(BuiltInRegistries.MENU,Create.asResource("toolbox"),new ExtendedMenuType<>(ToolboxMenu::client,Opening.CODEC));
    public final ToolboxBlockEntity contentHolder;
    private final UUID owner;
    public boolean renderPass;
    private static ToolboxMenu client(int id,Inventory inventory,Opening opening){var level=inventory.player.level();if(!level.isLoaded(opening.pos)||!(level.getBlockEntity(opening.pos) instanceof ToolboxBlockEntity box))throw new IllegalStateException("Toolbox menu owner is not loaded");if(box.getUniqueId()!=null&&!box.getUniqueId().equals(opening.owner))throw new IllegalStateException("Toolbox menu owner changed");box.setUniqueId(opening.owner);box.readInventory(opening.stock);return new ToolboxMenu(id,inventory,box);}
    public ToolboxMenu(int id,Inventory inventory,ToolboxBlockEntity box){super(TYPE,id);contentHolder=box;owner=box.getUniqueId();int x=79,y=37;int[] xs={x,x+33,x+66,x+72,x+66,x+33,x,x-6},ys={y,y-6,y,y+33,y+66,y+72,y+66,y+33};for(int c=0;c<8;c++)for(int i=0;i<4;i++)addSlot(new ToolboxSlot(this,box.inventory,c*4+i,i==0?xs[c]:-10000,i==0?ys[c]:-10000,i==0));for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,8+18*col,165+18*row));for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,8+18*col,223));box.startOpen(inventory.player);}
    @Override public boolean stillValid(Player player){var level=contentHolder.getLevel();return level==player.level()&&!contentHolder.isRemoved()&&level.isLoaded(contentHolder.getBlockPos())&&level.getBlockEntity(contentHolder.getBlockPos())==contentHolder&&java.util.Objects.equals(owner,contentHolder.getUniqueId())&&player.distanceToSqr(contentHolder.getBlockPos().getX()+.5,contentHolder.getBlockPos().getY()+.5,contentHolder.getBlockPos().getZ()+.5)<=64;}
    @Override public void clicked(int index,int button,ContainerInput input,Player player){if(!player.level().isClientSide()&&!stillValid(player))return;if(index>=0&&index<32){ItemStack clicked=getSlot(index).getItem(),carried=getCarried();if(input==ContainerInput.PICKUP&&!carried.isEmpty()&&!clicked.isEmpty()&&ToolboxInventory.canItemsShareCompartment(clicked,carried)&&index%4!=3){clicked(index-index%4+3,button,input,player);return;}if(input==ContainerInput.PICKUP&&carried.isEmpty()&&clicked.isEmpty()&&!player.level().isClientSide())contentHolder.inventory.clearFilter(index/4);}super.clicked(index,button,input,player);}
    @Override public ItemStack quickMoveStack(Player player,int index){if(index<0||index>=slots.size()||!stillValid(player))return ItemStack.EMPTY;Slot source=getSlot(index);if(!source.hasItem())return ItemStack.EMPTY;ItemStack actual=source.getItem(),original=actual.copy();if(!moveItemStackTo(actual,index<32?32:0,index<32?slots.size():32,index<32))return ItemStack.EMPTY;if(actual.isEmpty())source.setByPlayer(ItemStack.EMPTY);else source.setChanged();source.onTake(player,actual);return original;}
    @Override public boolean canDragTo(Slot slot){return slot.container!=contentHolder.inventory.asContainer()&&super.canDragTo(slot);}
    @Override public void removed(Player player){super.removed(player);contentHolder.stopOpen(player);}
    public ItemStack getFilter(int compartment){return contentHolder.inventory.getFilter(compartment);}
    public int totalCountInCompartment(int compartment){return contentHolder.inventory.totalCount(compartment);}
    public static ExtendedMenuType<ToolboxMenu,Opening> register(){return TYPE;}
}
