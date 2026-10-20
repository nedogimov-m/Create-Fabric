package com.simibubi.create.content.kinetics.deployer;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public record ItemApplicationRecipe(List<Ingredient> ingredients,List<ProcessingOutput> results,boolean keepHeldItem,boolean deploying) implements Recipe<ItemApplicationRecipe.Input>,IAssemblyRecipe {
    public record Input(ItemStack item,ItemStack tool) implements RecipeInput {
        @Override public ItemStack getItem(int slot){return switch(slot){case 0->item;case 1->tool;default->throw new IndexOutOfBoundsException(slot);};}
        @Override public int size(){return 2;}
    }
    public static MapCodec<ItemApplicationRecipe> codec(boolean deploying){return RecordCodecBuilder.mapCodec(i->i.group(
        Ingredient.CODEC.listOf(2,2).fieldOf("ingredients").forGetter(ItemApplicationRecipe::ingredients),
        ProcessingOutput.CODEC.listOf(1,4).fieldOf("results").forGetter(ItemApplicationRecipe::results),
        Codec.BOOL.optionalFieldOf("keep_held_item",false).forGetter(ItemApplicationRecipe::keepHeldItem)
    ).apply(i,(ingredients,results,keep)->new ItemApplicationRecipe(ingredients,results,keep,deploying)));}
    public Ingredient getProcessedItem(){return ingredients.getFirst();}
    public Ingredient getRequiredHeldItem(){return ingredients.get(1);}
    public boolean shouldKeepHeldItem(){return keepHeldItem;}
    @Override public boolean matches(Input input,Level level){return getProcessedItem().test(input.item)&&getRequiredHeldItem().test(input.tool);}
    @Override public ItemStack assemble(Input input){return results.getFirst().stack();}
    @Override public boolean isSpecial(){return true;}
    @Override public boolean showNotification(){return false;}
    @Override public String group(){return "";}
    @Override public RecipeType<ItemApplicationRecipe> getType(){return deploying?AllRecipeTypes.DEPLOYING:AllRecipeTypes.ITEM_APPLICATION;}
    @Override public RecipeSerializer<ItemApplicationRecipe> getSerializer(){return deploying?AllRecipeTypes.DEPLOYING_SERIALIZER:AllRecipeTypes.ITEM_APPLICATION_SERIALIZER;}
    @Override public PlacementInfo placementInfo(){return PlacementInfo.NOT_PLACEABLE;}
    @Override public RecipeBookCategory recipeBookCategory(){return RecipeBookCategories.CRAFTING_MISC;}
}
