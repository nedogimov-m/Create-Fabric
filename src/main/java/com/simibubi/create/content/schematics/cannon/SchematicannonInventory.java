package com.simibubi.create.content.schematics.cannon;

import com.simibubi.create.*;
import com.simibubi.create.content.schematics.SchematicInventory;
import net.minecraft.world.item.*;

/** The original five native slots, shared with actual Fabric item storage and the menu. */
public final class SchematicannonInventory extends SchematicInventory {
    public SchematicannonInventory(SchematicannonBlockEntity owner){super(5,(slot,item)->switch(slot){case 0->item.is(AllItems.SCHEMATIC);case 2->item.is(AllBlocks.CLIPBOARD.asItem())||item.is(Items.BOOK)||item.is(Items.WRITTEN_BOOK);case 4->item.is(Items.GUNPOWDER);default->false;},slot->slot==0?1:64,owner::notifyUpdate,owner::canPlayerUse);}
    public ItemStack getStackInSlot(int slot){return getItem(slot);}
    public void setStackInSlot(int slot,ItemStack item){setItem(slot,item);}
    public boolean isItemValid(int slot,ItemStack item){return canPlaceItem(slot,item);}
    public int getSlotLimit(int slot){return slotLimit(slot);}
}
