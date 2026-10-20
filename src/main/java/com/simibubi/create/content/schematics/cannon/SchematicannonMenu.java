package com.simibubi.create.content.schematics.cannon;

import com.simibubi.create.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.menu.v1.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.util.ProblemReporter;

public final class SchematicannonMenu extends AbstractContainerMenu {
    public static final ExtendedMenuType<SchematicannonMenu,com.simibubi.create.content.schematics.table.SchematicTableMenu.Opening> TYPE=Registry.register(BuiltInRegistries.MENU,Create.asResource("schematicannon"),new ExtendedMenuType<>(SchematicannonMenu::new,com.simibubi.create.content.schematics.table.SchematicTableMenu.Opening.CODEC));
    public final SchematicannonBlockEntity owner;
    private final Inventory playerInventory;
    public SchematicannonMenu(int id,Inventory inventory,com.simibubi.create.content.schematics.table.SchematicTableMenu.Opening opening){
        super(TYPE,id);playerInventory=inventory;
        if(inventory.player.level().isClientSide()){var actual=com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(inventory.player.level(),opening.pos());owner=actual instanceof SchematicannonBlockEntity cannon?cannon:new SchematicannonBlockEntity(opening.pos(),AllBlocks.SCHEMATICANNON.defaultBlockState());owner.setLevel(inventory.player.level());owner.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,inventory.player.registryAccess(),opening.state()));}
        else{var actual=com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(inventory.player.level(),opening.pos());if(!(actual instanceof SchematicannonBlockEntity cannon))throw new IllegalArgumentException("Actual native schematicannon owner changed");owner=cannon;}
        int[] xs={15,171,134,174,15},ys={65,65,19,19,19};for(int index=0;index<5;index++){final int slot=index;addSlot(new Slot(owner.inventory,slot,xs[slot],ys[slot]){@Override public boolean mayPlace(ItemStack stack){return owner.inventory.canPlaceItem(slot,stack);}@Override public int getMaxStackSize(){return owner.inventory.slotLimit(slot);}});}addStandardInventorySlots(inventory,37,161);
        addDataSlot(data(()->owner.state.ordinal(),value->{if(value>=0&&value<SchematicannonBlockEntity.State.values().length)owner.state=SchematicannonBlockEntity.State.values()[value];}));addDataSlot(data(()->(int)(owner.schematicProgress*10000),value->owner.schematicProgress=Math.clamp(value/10000f,0,1)));addDataSlot(data(()->(int)(owner.bookPrintingProgress*10000),value->owner.bookPrintingProgress=Math.clamp(value/10000f,0,1)));
    }
    private static DataSlot data(java.util.function.IntSupplier read,java.util.function.IntConsumer write){return new DataSlot(){@Override public int get(){return read.getAsInt();}@Override public void set(int value){write.accept(value);}};}
    @Override public boolean stillValid(Player player){return player.getInventory()==playerInventory&&owner.canPlayerUse(player);}
    @Override public void clicked(int index,int button,ContainerInput input,Player player){if(!player.level().isClientSide()&&!stillValid(player))return;super.clicked(index,button,input,player);}
    @Override public ItemStack quickMoveStack(Player player,int index){if(index<0||index>=slots.size()||!stillValid(player))return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var original=slot.getItem().copy();var moving=original.copy();if(index<5){if(!moveItemStackTo(moving,5,slots.size(),true))return ItemStack.EMPTY;}else if(!moveItemStackTo(moving,0,1,false)&&!moveItemStackTo(moving,2,3,false)&&!moveItemStackTo(moving,4,5,false))return ItemStack.EMPTY;slot.setByPlayer(moving);return original;}
    @Override public boolean clickMenuButton(Player player,int button){if(player.level().isClientSide()||!stillValid(player))return false;if(button>=0&&button<3){owner.setState(SchematicannonBlockEntity.State.values()[button]);return true;}if(button>=10&&button<14){owner.replaceMode=button-10;owner.notifyUpdate();return true;}if(button==20){owner.skipMissing=!owner.skipMissing;owner.notifyUpdate();return true;}if(button==21){owner.replaceBlockEntities=!owner.replaceBlockEntities;owner.notifyUpdate();return true;}return false;}
    public static ExtendedMenuType<SchematicannonMenu,com.simibubi.create.content.schematics.table.SchematicTableMenu.Opening> register(){return TYPE;}
}
