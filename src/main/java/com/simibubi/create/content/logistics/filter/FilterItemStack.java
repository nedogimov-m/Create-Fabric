package com.simibubi.create.content.logistics.filter;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.foundation.fluid.FluidStack;
import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Shared sample/list filtering for item machines and fluids, including nested filters. */
public final class FilterItemStack {
    private final ItemStack filter;
    private final List<FilterItemStack> children;
    private FluidStack resolvedFluid;
    private FilterItemStack(ItemStack filter){
        this.filter=filter.copy();children=filter.getItem() instanceof ListFilterItem?ListFilterItem.getFilterItems(filter).stream().filter(item->!item.isEmpty()).map(FilterItemStack::of).toList():List.of();
    }
    public static FilterItemStack of(ItemStack filter){return new FilterItemStack(filter);}
    public static FilterItemStack empty(){return of(ItemStack.EMPTY);}
    public boolean isEmpty(){return filter.isEmpty();}
    public boolean isFilterItem(){return filter.getItem() instanceof ListFilterItem||filter.getItem() instanceof AttributeFilterItem||filter.getItem() instanceof PackageFilterItem;}
    public ItemStack item(){return filter.copy();}
    public FluidStack fluid(Level level){if(level==null)return GenericItemEmptying.emptyItem(null,filter,true).getFirst();if(resolvedFluid==null)resolvedFluid=GenericItemEmptying.emptyItem(level,filter,true).getFirst();return resolvedFluid.copy();}
    public boolean test(Level level,ItemStack stack){return test(level,stack,false);}
    public boolean test(Level level,ItemStack stack,boolean matchData){
        if(isEmpty())return true;
        if(filter.getItem() instanceof PackageFilterItem){String address=com.simibubi.create.content.logistics.box.PackageItem.getAddress(filter);return address.isBlank()&&(matchData?com.simibubi.create.foundation.item.ItemSampleEquality.same(filter,stack):ItemStack.isSameItem(filter,stack))||com.simibubi.create.content.logistics.box.PackageItem.isPackage(stack)&&com.simibubi.create.content.logistics.box.PackageItem.matchAddress(stack,address);}
        if(filter.getItem() instanceof AttributeFilterItem){java.util.List<com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute.ItemAttributeEntry> attributes=filter.getOrDefault(AllDataComponents.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES,List.of());if(attributes.isEmpty())return matchData?com.simibubi.create.foundation.item.ItemSampleEquality.same(filter,stack):ItemStack.isSameItem(filter,stack);var mode=filter.getOrDefault(AllDataComponents.ATTRIBUTE_FILTER_WHITELIST_MODE,AttributeFilterWhitelistMode.WHITELIST_DISJ);for(var entry:attributes){boolean matches=entry.attribute().appliesTo(stack,level)!=entry.inverted();if(mode==AttributeFilterWhitelistMode.WHITELIST_DISJ&&matches)return true;if(mode==AttributeFilterWhitelistMode.BLACKLIST&&matches||mode==AttributeFilterWhitelistMode.WHITELIST_CONJ&&!matches)return false;}return mode!=AttributeFilterWhitelistMode.WHITELIST_DISJ;}
        if(isFilterItem()){boolean respect=filter.getOrDefault(AllDataComponents.FILTER_ITEMS_RESPECT_NBT,false),deny=filter.getOrDefault(AllDataComponents.FILTER_ITEMS_BLACKLIST,false);return children.stream().anyMatch(child->child.test(level,stack,respect))!=deny;}
        return matchData?com.simibubi.create.foundation.item.ItemSampleEquality.same(filter,stack):ItemStack.isSameItem(filter,stack);
    }
    public boolean test(Level level,FluidStack stack){return test(level,stack,true);}
    public boolean test(Level level,FluidStack stack,boolean matchData){
        if(isEmpty())return true;if(filter.getItem() instanceof AttributeFilterItem||filter.getItem() instanceof PackageFilterItem)return false;if(isFilterItem()){boolean respect=filter.getOrDefault(AllDataComponents.FILTER_ITEMS_RESPECT_NBT,false),deny=filter.getOrDefault(AllDataComponents.FILTER_ITEMS_BLACKLIST,false);return children.stream().anyMatch(child->child.test(level,stack,respect))!=deny;}
        var sample=fluid(level);return !sample.isEmpty()&&!stack.isEmpty()&&(matchData?sample.variant().equals(stack.variant()):sample.variant().getFluid().isSame(stack.variant().getFluid()));
    }
}
