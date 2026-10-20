package com.simibubi.create.content.logistics.item.filter.attribute;

import com.simibubi.create.*;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.logistics.item.filter.attribute.attributes.*;
import java.util.*;
import java.util.function.*;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;

/** All 28 upstream attribute types, using component-driven 26.3 item properties. */
public final class AllItemAttributeTypes {
    public static final ItemAttributeType
        PLACEABLE=singleton("placeable",s->s.getItem() instanceof BlockItem),
        CONSUMABLE=singleton("consumable",s->s.has(DataComponents.FOOD)),
        FLUID_CONTAINER=singleton("fluid_container",s->ContainerItemContext.withConstant(s.copyWithCount(1)).find(FluidStorage.ITEM)!=null),
        ENCHANTED=singleton("enchanted",ItemStack::isEnchanted),
        MAX_ENCHANTED=singleton("max_enchanted",s->net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentsForCrafting(s).entrySet().stream().anyMatch(entry->entry.getIntValue()>=entry.getKey().value().getMaxLevel())),
        RENAMED=singleton("renamed",s->s.has(DataComponents.CUSTOM_NAME)),
        DAMAGED=singleton("damaged",ItemStack::isDamaged),
        BADLY_DAMAGED=singleton("badly_damaged",s->s.isDamaged()&&(float)s.getDamageValue()/s.getMaxDamage()>0.75f),
        NOT_STACKABLE=singleton("not_stackable",s->!s.isStackable()),
        EQUIPABLE=singleton("equipable",s->s.has(DataComponents.EQUIPPABLE)&&s.get(DataComponents.EQUIPPABLE).slot().getType()!=EquipmentSlot.Type.HAND),
        FURNACE_FUEL=singleton("furnace_fuel",(s,w)->fuelTime(s,w)>0),
        WASHABLE=singleton("washable",(s,w)->testRecipe(s,w,AllRecipeTypes.SPLASHING)),
        HAUNTABLE=singleton("hauntable",(s,w)->testRecipe(s,w,AllRecipeTypes.HAUNTING)),
        CRUSHABLE=singleton("crushable",(s,w)->testRecipe(s,w,AllRecipeTypes.CRUSHING)||testRecipe(s,w,AllRecipeTypes.MILLING)),
        SMELTABLE=singleton("smeltable",(s,w)->testRecipe(s,w,RecipeType.SMELTING)),
        SMOKABLE=singleton("smokable",(s,w)->testRecipe(s,w,RecipeType.SMOKING)),
        BLASTABLE=singleton("blastable",(s,w)->testRecipe(s,w,RecipeType.BLASTING)),
        COMPOSTABLE=singleton("compostable",s->s.has(DataComponents.COMPOSTABLE)),
        IN_TAG=register("in_tag",new InTagAttribute.Type()),
        IN_ITEM_GROUP=register("in_item_group",new InItemGroupAttribute.Type()),
        ADDED_BY=register("added_by",new AddedByAttribute.Type()),
        HAS_ENCHANT=register("has_enchant",new EnchantAttribute.Type()),
        SHULKER_FILL_LEVEL=register("shulker_fill_level",new ShulkerFillLevelAttribute.Type()),
        HAS_COLOR=register("has_color",new ColorAttribute.Type()),
        HAS_FLUID=register("has_fluid",new FluidContentsAttribute.Type()),
        HAS_NAME=register("has_name",new ItemNameAttribute.Type()),
        BOOK_AUTHOR=register("book_author",new BookAuthorAttribute.Type()),
        BOOK_COPY=register("book_copy",new BookCopyAttribute.Type());
    private static LootContext context(ServerLevel level,ItemStack stack,net.minecraft.world.level.block.state.BlockState state){
        var furnace=new net.minecraft.world.level.block.entity.FurnaceBlockEntity(net.minecraft.core.BlockPos.ZERO,state);furnace.setLevel(level);
        var params=new LootParams.Builder(level).withParameter(LootContextParams.BLOCK_STATE,state).withParameter(LootContextParams.BLOCK_ENTITY,furnace).withParameter(LootContextParams.ORIGIN,Vec3.ZERO).withParameter(LootContextParams.CONTAINER,new SimpleContainer(stack.copyWithCount(1))).create(LootContextParamSets.CONTAINER_PROCESS);
        return new LootContext.Builder(params).withOptionalRandomSource(net.minecraft.util.RandomSource.create(0)).create(Optional.empty());
    }
    private static int fuelTime(ItemStack stack,Level level){return stack.has(DataComponents.COOKING_FUEL)&&level instanceof ServerLevel server?ResolvableInt.getFromItem(stack,DataComponents.COOKING_FUEL,CookingFuel::burnTime,context(server,stack,Blocks.FURNACE.defaultBlockState()),0):0;}
    private static <T extends Recipe<SingleRecipeInput>> boolean testRecipe(ItemStack stack,Level level,RecipeType<T> type){
        return level instanceof ServerLevel server?server.getServer().getRecipeManager().getRecipeFor(type,new SingleRecipeInput(stack.copy()),level).isPresent():level!=null&&level.recipeAccess().getSynchronizedRecipes().getFirstMatch(type,new SingleRecipeInput(stack.copy()),level).isPresent();
    }
    private static ItemAttributeType singleton(String id,Predicate<ItemStack> test){return singleton(id,(stack,level)->test.test(stack));}
    private static ItemAttributeType singleton(String id,BiPredicate<ItemStack,Level> test){return register(id,new SingletonItemAttribute.Type(type->new SingletonItemAttribute(type,test,id)));}
    private static ItemAttributeType register(String id,ItemAttributeType type){return Registry.register(CreateBuiltInRegistries.ITEM_ATTRIBUTE_TYPE,Create.asResource(id),type);}
    public static void init(){}
    private AllItemAttributeTypes(){}
}
