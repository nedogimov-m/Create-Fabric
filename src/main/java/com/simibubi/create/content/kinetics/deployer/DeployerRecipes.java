package com.simibubi.create.content.kinetics.deployer;

import com.simibubi.create.*;
import com.simibubi.create.content.equipment.sandPaper.*;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import java.util.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public final class DeployerRecipes {
    private static final TagKey<RecipeSerializer<?>> AUTOMATION_IGNORE=TagKey.create(Registries.RECIPE_SERIALIZER,Create.asResource("automation_ignore"));
    public record Match(Identifier id,Recipe<?> recipe,SequencedAssemblyRecipe.Match assembly,List<ProcessingOutput> results,boolean keepTool){
        public List<ItemStack> roll(RandomSource random){return assembly!=null?assembly.roll(random):results.stream().map(output->output.rollOutput(random)).filter(stack->!stack.isEmpty()).toList();}
    }
    public static Match find(Level level,ItemStack input,ItemStack tool){
        return find(level,null,input,tool);
    }
    public static Match find(DeployerBlockEntity deployer,ItemStack input,ItemStack tool){return find(deployer.getLevel(),deployer,input,tool);}
    private static Match find(Level level,DeployerBlockEntity deployer,ItemStack input,ItemStack tool){
        if(!(level instanceof ServerLevel server)||input.isEmpty()||tool.isEmpty())return null;var manager=server.getServer().getRecipeManager();
        if(tool.getItem() instanceof SandPaperItem){var recipe=manager.getRecipeFor(AllRecipeTypes.SANDPAPER_POLISHING,new SingleRecipeInput(input),level);if(recipe.isPresent())return new Match(recipe.get().id().identifier(),recipe.get().value(),null,recipe.get().value().results(),false);}
        var candidate=new ItemApplicationRecipe.Input(input,tool);
        var search=new DeployerRecipeSearchEvent(deployer,candidate);
        search.addRecipe(()->SequencedAssemblyRecipe.getRecipe(level,input,AllRecipeTypes.DEPLOYING,r->r instanceof ItemApplicationRecipe recipe&&recipe.getRequiredHeldItem().test(tool)).map(match->{var recipe=(ItemApplicationRecipe)match.recipe();return new Match(match.id(),recipe,match,recipe.results(),recipe.keepHeldItem());}),100);
        for(var type:List.of(AllRecipeTypes.DEPLOYING,AllRecipeTypes.ITEM_APPLICATION)){
            search.addRecipe(()->manager.getRecipeFor(type,candidate,level).filter(found->!found.id().identifier().getPath().endsWith("_manual_only")&&!BuiltInRegistries.RECIPE_SERIALIZER.wrapAsHolder(found.value().getSerializer()).is(AUTOMATION_IGNORE)).map(found->{var recipe=found.value();return new Match(found.id().identifier(),recipe,null,recipe.results(),recipe.keepHeldItem());}),50);
        }
        if(deployer!=null)DeployerRecipeSearchEvent.EVENT.invoker().onSearch(search);return search.getRecipe();
    }
    private DeployerRecipes(){}
}
