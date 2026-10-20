package com.simibubi.create.content.fluids.potion;

import com.mojang.datafixers.util.Either;
import com.simibubi.create.*;
import com.simibubi.create.content.processing.basin.*;
import com.simibubi.create.content.processing.recipe.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.material.Fluids;
import java.util.*;

/** Minecraft 26.3 brewing is data driven; basin brewing follows the current recipe manager. */
public final class PotionMixingRecipes {
    private PotionMixingRecipes(){}
    private record Index(Object recipes,Map<Item,List<RecipeHolder<BrewingRecipe>>> reagents){}
    private static final Map<RecipeManager,Index> INDICES=new WeakHashMap<>();
    private static synchronized Index index(RecipeManager manager){
        var recipes=manager.getRecipes();var cached=INDICES.get(manager);if(cached!=null&&cached.recipes==recipes)return cached;
        var byItem=new IdentityHashMap<Item,List<RecipeHolder<BrewingRecipe>>>();
        for(var holder:recipes)if(holder.value() instanceof BrewingRecipe recipe){
            var typed=new RecipeHolder<>(holder.id(),recipe);
            recipe.getReagent().ingredient().items().forEach(item->byItem.computeIfAbsent(item.value(),ignored->new ArrayList<>()).add(typed));
        }
        var index=new Index(recipes,byItem);INDICES.put(manager,index);return index;
    }
    public static RecipeHolder<BasinRecipe> findRecipe(ServerLevel level,BasinRecipeInput input){
        if(!HeatCondition.HEATED.test(input.heat()))return null;
        var index=index(level.getServer().getRecipeManager());
        for(var fluid:input.fluids()){
            if(fluid.amount()<FluidConstants.BUCKET)continue;
            var variant=fluid.variant();ItemStack bottle;
            if(variant.getFluid().isSame(Fluids.WATER))bottle=PotionContents.createItemStack(Items.POTION,Potions.WATER);
            else if(variant.getFluid().isSame(AllFluids.POTION))bottle=PotionFluidHandler.fillBottle(variant);else continue;
            for(var item:input.items()){
                if(item.isEmpty())continue;
                for(var holder:index.reagents.getOrDefault(item.getItem(),List.of())){
                    var recipe=holder.value();var brewingInput=new BrewingInput(bottle,item);if(!recipe.matches(brewingInput,level))continue;
                    var result=recipe.assemble(brewingInput);if(!PotionFluidHandler.isPotionItem(result))continue;
                    var output=PotionFluidHandler.getFluidFromPotionItem(result);
                    var ingredient=new FluidIngredient(new FluidIngredient.ExactSelector(variant.getFluid(),DataComponentExactPredicate.allOf(variant.getComponents())),FluidConstants.BUCKET);
                    var converted=new BasinRecipe(true,List.of(Either.left(new BrewingReagentIngredient(recipe.getReagent()).toVanilla()),Either.right(ingredient)),
                        List.of(Either.right(new FluidOutput(output.variant().getFluid(),FluidConstants.BUCKET,output.variant().getComponentsPatch()))),HeatCondition.HEATED,100);
                    if(!converted.matches(input,level))continue;
                    var id=holder.id().identifier();return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE,Create.asResource("potion_mixing/"+id.getNamespace()+"/"+id.getPath())),converted);
                }
            }
        }return null;
    }
}
