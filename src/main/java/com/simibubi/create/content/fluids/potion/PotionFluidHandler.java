package com.simibubi.create.content.fluids.potion;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.fluids.potion.PotionFluid.BottleType;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.createmod.catnip.data.Pair;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.material.Fluids;

public final class PotionFluidHandler {
    private static final TagKey<Item> NOT_POTION=TagKey.create(Registries.ITEM,com.simibubi.create.Create.asResource("not_potion"));
    private PotionFluidHandler(){}
    public static boolean isPotionItem(ItemStack stack){return stack.getItem() instanceof PotionItem&&!stack.is(NOT_POTION)&&!(stack.getItem().getCraftingRemainder()!=null&&stack.getItem().getCraftingRemainder().create().getItem() instanceof BucketItem);}
    public static BottleType bottleTypeFromItem(Item item){return item==Items.LINGERING_POTION?BottleType.LINGERING:item==Items.SPLASH_POTION?BottleType.SPLASH:BottleType.REGULAR;}
    public static Item itemFromBottleType(BottleType type){return switch(type){case REGULAR->Items.POTION;case SPLASH->Items.SPLASH_POTION;case LINGERING->Items.LINGERING_POTION;};}
    public static FluidStack getFluidFromPotionItem(ItemStack stack){
        var contents=stack.getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY);var bottle=bottleTypeFromItem(stack.getItem());
        if(contents.equals(new PotionContents(Potions.WATER))&&bottle==BottleType.REGULAR)return new FluidStack(FluidVariant.of(Fluids.WATER),GenericItemFilling.BOTTLE);
        return PotionFluid.of(GenericItemFilling.BOTTLE,contents,bottle);
    }
    public static FluidStack getFluidFromPotion(PotionContents contents,BottleType bottle,long amount){
        return contents.equals(new PotionContents(Potions.WATER))&&bottle==BottleType.REGULAR?new FluidStack(FluidVariant.of(Fluids.WATER),amount):PotionFluid.of(amount,contents,bottle);
    }
    public static Pair<FluidStack,ItemStack> emptyPotion(ItemStack stack,boolean simulate){var fluid=getFluidFromPotionItem(stack);if(!simulate)stack.shrink(1);return Pair.of(fluid,new ItemStack(Items.GLASS_BOTTLE));}
    public static ItemStack fillBottle(FluidVariant fluid){
        var item=new ItemStack(itemFromBottleType(fluid.getComponents().getOrDefault(AllDataComponents.POTION_FLUID_BOTTLE_TYPE,BottleType.REGULAR)));
        item.set(DataComponents.POTION_CONTENTS,fluid.getComponents().getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY));return item;
    }
    public static Component name(FluidVariant fluid){
        var contents=fluid.getComponents().getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY);
        var item=itemFromBottleType(fluid.getComponents().getOrDefault(AllDataComponents.POTION_FLUID_BOTTLE_TYPE,BottleType.REGULAR));
        return contents.getName(item.getDescriptionId()+".effect.");
    }
}
