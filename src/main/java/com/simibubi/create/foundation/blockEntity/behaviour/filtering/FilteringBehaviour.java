package com.simibubi.create.foundation.blockEntity.behaviour.filtering;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.fluid.FluidStack;
import java.util.function.*;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.*;

/** Source filter predicate and amount semantics shared by logistics and processing blocks. */
public class FilteringBehaviour extends BlockEntityBehaviour {
    @Override public com.simibubi.create.content.schematics.requirement.ItemRequirement getRequiredItems(){return filter.isFilterItem()?new com.simibubi.create.content.schematics.requirement.ItemRequirement(com.simibubi.create.content.schematics.requirement.ItemRequirement.ItemUseType.CONSUME,getFilter().copy()):com.simibubi.create.content.schematics.requirement.ItemRequirement.NONE;}
    public static final BehaviourType<FilteringBehaviour> TYPE=new BehaviourType<>();
    protected FilterItemStack filter=FilterItemStack.empty();
    private net.minecraft.network.chat.Component label=net.minecraft.network.chat.Component.translatable("create.logistics.filter");
    private Predicate<ItemStack> predicate=stack->true;
    private Consumer<ItemStack> callback=stack->{};
    private Supplier<Boolean> active=()->true,showCount=()->false;
    private boolean recipeFilter,fluidFilter;
    public int count=64;
    public boolean upTo=true;
    public FilteringBehaviour(BlockEntity entity){super(entity);}
    public FilteringBehaviour setLabel(net.minecraft.network.chat.Component label){this.label=java.util.Objects.requireNonNull(label);return this;}
    public net.minecraft.network.chat.Component getLabel(){return label;}
    public FilteringBehaviour withCallback(Consumer<ItemStack> callback){this.callback=callback;return this;}
    public FilteringBehaviour withPredicate(Predicate<ItemStack> predicate){this.predicate=predicate;return this;}
    public FilteringBehaviour onlyActiveWhen(Supplier<Boolean> active){this.active=active;return this;}
    public FilteringBehaviour showCountWhen(Supplier<Boolean> showCount){this.showCount=showCount;return this;}
    public FilteringBehaviour showCount(){return showCountWhen(()->true);}
    public FilteringBehaviour forRecipes(){recipeFilter=true;return this;}
    public FilteringBehaviour forFluids(){fluidFilter=true;return this;}
    public boolean isRecipeFilter(){return recipeFilter;}
    public boolean isFluidFilter(){return fluidFilter;}
    public boolean isActive(){return active.get();}
    public boolean mayInteract(Player player){return true;}
    public boolean isCountVisible(){return isActive()&&showCount.get()&&getMaxStackSize()>1;}
    public ItemStack getFilter(){return filter.item();}
    public ItemStack getFilter(Direction side){return getFilter();}
    public int getMaxStackSize(){return getFilter().isEmpty()?64:getFilter().getMaxStackSize();}
    public int getAmount(){return count;}
    public boolean anyAmount(){return count==0;}
    public boolean setFilter(Direction side,ItemStack sample){return setFilter(sample);}
    public boolean canSetFilter(ItemStack sample){return sample.isEmpty()||predicate.test(sample);}
    public boolean setFilter(ItemStack sample){if(!sample.isEmpty()&&!predicate.test(sample))return false;filter=FilterItemStack.of(sample);if(!upTo)count=Math.min(count,getMaxStackSize());callback.accept(getFilter());changed();return true;}
    public void setAmount(int count,boolean upTo){this.count=Math.clamp(count,1,getMaxStackSize());this.upTo=upTo;callback.accept(getFilter());changed();}
    public boolean test(ItemStack stack){return !isActive()||filter.test(getWorld(),stack);}
    public boolean test(FluidStack stack){return !isActive()||filter.test(getWorld(),stack);}
    public boolean applyPlayerSample(Player player,net.minecraft.world.InteractionHand hand){
        var held=player.getItemInHand(hand);if(held.is(com.simibubi.create.AllItems.WRENCH)||!held.isEmpty()&&!predicate.test(held))return false;
        var previous=getFilter();var next=held.copyWithCount(1);if(!setFilter(next))return false;
        if(filter.isFilterItem()&&!player.isCreative())held.shrink(1);
        if(FilterItemStack.of(previous).isFilterItem()&&(!player.isCreative()||!player.getInventory().contains(stack->ItemStack.isSameItemSameComponents(stack,previous))))player.getInventory().placeItemBackInInventory(previous,net.minecraft.util.Prediction.SERVER_ONLY);return true;
    }
    @Override public void destroy(){if(getWorld()!=null&&!getWorld().isClientSide()&&filter.isFilterItem())Block.popResource(getWorld(),getPos(),getFilter());filter=FilterItemStack.empty();}
    @Override public BehaviourType<?> getType(){return TYPE;}
    @Override public boolean isSafeNBT(){return true;}
    @Override public void write(ValueOutput output){output.store("Filter",ItemStack.OPTIONAL_CODEC,getFilter());output.putInt("FilterAmount",count);output.putBoolean("UpTo",upTo);}
    @Override public void read(ValueInput input){filter=FilterItemStack.of(input.read("Filter",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));count=input.getIntOr("FilterAmount",64);upTo=input.getBooleanOr("UpTo",true);if(count==0){count=getMaxStackSize();upTo=true;}}
}
