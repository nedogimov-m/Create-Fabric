package com.simibubi.create.content.equipment.toolbox;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ToolboxSlot extends Slot {
    private final ToolboxMenu menu;
    private final ToolboxInventory inventory;
    private final boolean visible;
    public ToolboxSlot(ToolboxMenu menu,ToolboxInventory inventory,int index,int x,int y,boolean visible){super(inventory.asContainer(),index,x,y);this.menu=menu;this.inventory=inventory;this.visible=visible;}
    @Override public boolean isActive(){return visible&&!menu.renderPass;}
    @Override public boolean mayPlace(ItemStack stack){return inventory.isItemValid(getContainerSlot(),stack);}
    @Override public int getMaxStackSize(ItemStack stack){if(!mayPlace(stack))return 0;ItemStack current=getItem();if(!current.isEmpty()&&!ItemStack.isSameItemSameComponents(current,stack))return current.getCount();return stack.getMaxStackSize();}
}
