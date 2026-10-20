package com.simibubi.create.content.logistics.tableCloth;

import com.simibubi.create.content.logistics.filter.*;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class TableClothFilteringBehaviour extends FilteringBehaviour {
    private final TableClothBlockEntity cloth;
    public TableClothFilteringBehaviour(TableClothBlockEntity cloth){super(cloth);this.cloth=cloth;count=1;upTo=false;withPredicate(stack->!(stack.getItem() instanceof ListFilterItem)&&!(stack.getItem() instanceof AttributeFilterItem)&&!(stack.getItem() instanceof PackageFilterItem)&&!(stack.getItem() instanceof ShoppingListItem));}
    @Override public int getMaxStackSize(){return 100;}
    @Override public boolean isActive(){return cloth.isShop();}
    @Override public boolean isCountVisible(){return isActive()&&!getFilter().isEmpty();}
    @Override public boolean mayInteract(Player player){return cloth.owner==null||cloth.owner.equals(player.getUUID());}
    @Override public boolean setFilter(ItemStack stack){int previous=count;boolean changed=super.setFilter(stack);count=previous;return changed;}
    @Override public void setAmount(int amount,boolean upTo){super.setAmount(amount,false);}
    @Override public boolean isSafeNBT(){return false;}
}
