package com.simibubi.create.content.logistics.factoryBoard;

import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.registries.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

/** Resolves source recipe ingredients against connected samples using the current server recipes. */
public final class FactoryPanelCrafting {
    public record Arrangement(List<ItemStack> grid,int output) {}
    public static Arrangement find(FactoryPanelBehaviour panel) {
        if(panel.getFilter().isEmpty()||panel.targetedBy.isEmpty())return null;var level=panel.getWorld();if(level.getServer()==null)return null;
        var samples=new ArrayList<ItemStack>();for(var connection:panel.targetedBy.values()){var source=FactoryPanelBehaviour.at(level,connection);if(source!=null&&!source.getFilter().isEmpty())samples.add(source.getFilter());}if(samples.isEmpty())return null;
        var ignored=TagKey.create(Registries.RECIPE_SERIALIZER,com.simibubi.create.Create.asResource("automation_ignore"));
        for(var holder:level.getServer().getRecipeManager().getRecipes()){
            if(!(holder.value() instanceof CraftingRecipe recipe)||holder.id().identifier().getPath().endsWith("_manual_only")||BuiltInRegistries.RECIPE_SERIALIZER.wrapAsHolder(recipe.getSerializer()).is(ignored))continue;
            var info=recipe.placementInfo();if(info.isImpossibleToPlace()||info.ingredients().size()>9)continue;
            int width=recipe instanceof ShapedRecipe shaped?shaped.getWidth():Math.min(3,info.ingredients().size()),height=recipe instanceof ShapedRecipe shaped?shaped.getHeight():(info.ingredients().size()+2)/3;if(width<1||height<1||width>3||height>3)continue;
            var grid=new ArrayList<>(Collections.nCopies(9,ItemStack.EMPTY));var used=new HashSet<ItemVariant>();boolean valid=true;
            for(int slot=0;slot<width*height;slot++){
                int index=slot<info.slotsToIngredientIndex().size()?info.slotsToIngredientIndex().getInt(slot):-1;if(index<0)continue;var ingredient=info.ingredients().get(index);var sample=samples.stream().filter(ingredient::test).findFirst().orElse(ItemStack.EMPTY);if(sample.isEmpty()){valid=false;break;}grid.set((slot/width+(3-height)/2)*3+slot%width+(3-width)/2,sample.copyWithCount(1));used.add(ItemVariant.of(sample));
            }
            if(!valid||samples.stream().anyMatch(stack->!used.contains(ItemVariant.of(stack))))continue;var input=CraftingInput.of(3,3,grid);if(!recipe.matches(input,level))continue;var output=recipe.assemble(input);if(output.is(panel.getFilter().getItem()))return new Arrangement(List.copyOf(grid),output.getCount());
        }return null;
    }
    private FactoryPanelCrafting() {}
}
