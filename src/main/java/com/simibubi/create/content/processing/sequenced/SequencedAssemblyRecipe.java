package com.simibubi.create.content.processing.sequenced;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.*;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.network.codec.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public record SequencedAssemblyRecipe(Ingredient ingredient,ProcessingOutput transitionalItem,List<SequencedRecipe> sequence,List<ProcessingOutput> resultPool,int loops) implements Recipe<SingleRecipeInput> {
    public record SequencedAssembly(Identifier id,int step,float progress){
        public static final Codec<SequencedAssembly> CODEC=RecordCodecBuilder.create(i->i.group(
            Identifier.CODEC.fieldOf("id").forGetter(SequencedAssembly::id),Codec.intRange(0,Integer.MAX_VALUE).fieldOf("step").forGetter(SequencedAssembly::step),Codec.floatRange(0,1).fieldOf("progress").forGetter(SequencedAssembly::progress)
        ).apply(i,SequencedAssembly::new));
        public static final StreamCodec<io.netty.buffer.ByteBuf,SequencedAssembly> STREAM_CODEC=ByteBufCodecs.fromCodec(CODEC);
    }
    public static final MapCodec<SequencedAssemblyRecipe> CODEC=RecordCodecBuilder.<SequencedAssemblyRecipe>mapCodec(i->i.group(
        Ingredient.CODEC.fieldOf("ingredient").forGetter(SequencedAssemblyRecipe::ingredient),ProcessingOutput.CODEC.fieldOf("transitional_item").forGetter(SequencedAssemblyRecipe::transitionalItem),
        SequencedRecipe.CODEC.listOf(1,Integer.MAX_VALUE).fieldOf("sequence").forGetter(SequencedAssemblyRecipe::sequence),ProcessingOutput.WEIGHTED_CODEC.listOf(1,Integer.MAX_VALUE).fieldOf("results").forGetter(SequencedAssemblyRecipe::resultPool),
        Codec.intRange(0,Integer.MAX_VALUE).optionalFieldOf("loops",1).forGetter(SequencedAssemblyRecipe::loops)
    ).apply(i,SequencedAssemblyRecipe::new)).validate(recipe->(long)recipe.sequence.size()*recipe.loops>Integer.MAX_VALUE||recipe.resultPool.stream().mapToDouble(ProcessingOutput::chance).sum()<=0?DataResult.error(()->"Invalid assembly length or empty output weight"):DataResult.success(recipe));
    public record Match(Identifier id,SequencedAssemblyRecipe parent,Recipe<?> recipe,ItemStack input){
        public List<ItemStack> roll(RandomSource random){return List.of(parent.advance(id,input,random));}
    }
    public static List<Match> getRecipes(Level level,ItemStack stack,RecipeType<?> type,Predicate<Recipe<?>> filter){
        if(!(level instanceof ServerLevel server)||stack.isEmpty())return List.of();var matches=new ArrayList<Match>();
        for(var holder:server.getServer().getRecipeManager().getRecipes()){
            if(!(holder.value() instanceof SequencedAssemblyRecipe recipe)||!recipe.appliesTo(holder.id().identifier(),stack))continue;
            var step=recipe.sequence.get(recipe.getStep(stack)%recipe.sequence.size()).recipe();if(step.getType()==type&&filter.test(step))matches.add(new Match(holder.id().identifier(),recipe,step,stack.copyWithCount(1)));
        }return matches;
    }
    public static Optional<Match> getRecipe(Level level,ItemStack stack,RecipeType<?> type,Predicate<Recipe<?>> filter){return getRecipes(level,stack,type,filter).stream().findFirst();}
    public int getStep(ItemStack stack){var progress=stack.get(AllDataComponents.SEQUENCED_ASSEMBLY);return progress==null?0:progress.step;}
    public int totalSteps(){return sequence.size()*loops;}
    public boolean appliesTo(Identifier id,ItemStack stack){var progress=stack.get(AllDataComponents.SEQUENCED_ASSEMBLY);return progress==null?ingredient.test(stack):progress.id.equals(id)&&stack.is(transitionalItem.item())&&progress.step<totalSteps();}
    public ItemStack advance(Identifier id,ItemStack input,RandomSource random){
        int step=getStep(input)+1;if(step>=totalSteps())return rollResult(random);
        var result=transitionalItem.stack().copyWithCount(1);result.set(AllDataComponents.SEQUENCED_ASSEMBLY,new SequencedAssembly(id,step,(float)step/totalSteps()));return result;
    }
    public ItemStack rollResult(RandomSource random){double weight=resultPool.stream().mapToDouble(ProcessingOutput::chance).sum(),roll=random.nextFloat()*weight;for(var output:resultPool){roll-=output.chance();if(roll<0)return output.stack();}return ItemStack.EMPTY;}
    public float getOutputChance(){return (float)(resultPool.getFirst().chance()/resultPool.stream().mapToDouble(ProcessingOutput::chance).sum());}
    @Override public boolean matches(SingleRecipeInput input,Level level){return false;}
    @Override public ItemStack assemble(SingleRecipeInput input){return ItemStack.EMPTY;}
    @Override public boolean isSpecial(){return true;}
    @Override public boolean showNotification(){return false;}
    @Override public String group(){return "";}
    @Override public RecipeType<SequencedAssemblyRecipe> getType(){return AllRecipeTypes.SEQUENCED_ASSEMBLY;}
    @Override public RecipeSerializer<SequencedAssemblyRecipe> getSerializer(){return AllRecipeTypes.SEQUENCED_ASSEMBLY_SERIALIZER;}
    @Override public PlacementInfo placementInfo(){return PlacementInfo.NOT_PLACEABLE;}
    @Override public RecipeBookCategory recipeBookCategory(){return RecipeBookCategories.CRAFTING_MISC;}
}
