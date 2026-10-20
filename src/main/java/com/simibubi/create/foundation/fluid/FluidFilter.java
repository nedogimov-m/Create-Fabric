package com.simibubi.create.foundation.fluid;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import net.minecraft.world.level.storage.*;

/** Direct fluid samples retain exact data matching; configured lists supply their own comparison mode. */
public final class FluidFilter {
    private ItemStack sample=ItemStack.EMPTY;
    private FluidVariant fluid=FluidVariant.blank();
    private boolean resolvedInLevel;
    private com.simibubi.create.content.logistics.filter.FilterItemStack configured=com.simibubi.create.content.logistics.filter.FilterItemStack.empty();
    public ItemStack getFilter(){return sample.copy();}
    public FluidVariant fluid(){return fluid;}
    public static FluidVariant resolve(ItemStack stack){
        if(stack.isEmpty())return FluidVariant.blank();
        var storage=ContainerItemContext.withConstant(stack.copyWithCount(1)).find(FluidStorage.ITEM);
        if(storage!=null)for(var view:storage)if(!view.isResourceBlank()&&view.getAmount()>0)return view.getResource();
        return FluidVariant.blank();
    }
    public boolean setFilter(ItemStack stack){
        var resolved=resolve(stack);
        sample=stack.isEmpty()?ItemStack.EMPTY:stack.copyWithCount(1);fluid=resolved;resolvedInLevel=false;configured=com.simibubi.create.content.logistics.filter.FilterItemStack.of(sample);return true;
    }
    public boolean test(FluidStack stack){return configured.isFilterItem()?configured.test(null,stack):sample.isEmpty()||!stack.isEmpty()&&fluid.equals(stack.variant());}
    public boolean test(Level level,FluidStack stack){
        if(configured.isFilterItem())return configured.test(level,stack);
        if(!resolvedInLevel&&level!=null){fluid=GenericItemEmptying.emptyItem(level,sample,true).getFirst().variant();resolvedInLevel=true;}
        return test(stack);
    }
    public void save(ValueOutput out){if(!sample.isEmpty())out.store("Filter",ItemStack.CODEC,sample);out.putInt("FilterAmount",64);out.putBoolean("UpTo",true);}
    public void load(ValueInput in){setFilter(in.read("Filter",ItemStack.CODEC).orElse(ItemStack.EMPTY));}
}
