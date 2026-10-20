package com.simibubi.create.content.processing.sequenced;

import com.google.gson.*;
import com.simibubi.create.api.data.recipe.*;
import com.simibubi.create.content.processing.recipe.*;
import java.util.*;
import java.util.function.*;
import net.fabricmc.fabric.api.resource.conditions.v1.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.ItemLike;

/** Actual assembly steps and weighted outcomes. Fluid amounts in step builders use Fabric droplets. */
public class SequencedAssemblyRecipeBuilder {
    private final Identifier id;
    private Function<HolderLookup.Provider, JsonElement> ingredient;
    private final List<ProcessingRecipeBuilder> steps = new ArrayList<>();
    private final List<ProcessingOutput> results = new ArrayList<>();
    private final List<ResourceCondition> conditions = new ArrayList<>();
    private ItemStack transitional;
    private int loops = 5;
    public SequencedAssemblyRecipeBuilder(Identifier id) { this.id = Objects.requireNonNull(id); }
    public SequencedAssemblyRecipeBuilder require(ItemLike item) { return require(Ingredient.of(item)); }
    public SequencedAssemblyRecipeBuilder require(Ingredient value) { ingredient = lookup -> RecipeData.encode(Ingredient.CODEC, value, lookup); return this; }
    public SequencedAssemblyRecipeBuilder require(TagKey<Item> tag) { ingredient = lookup -> new JsonPrimitive("#" + tag.location()); return this; }
    public SequencedAssemblyRecipeBuilder transitionTo(ItemLike item) { return transitionTo(new ItemStack(item)); }
    public SequencedAssemblyRecipeBuilder transitionTo(ItemStack item) {
        if (!steps.isEmpty() || item.isEmpty()) throw new IllegalStateException("Choose a nonempty transitional item before adding assembly steps");
        transitional = item.copyWithCount(1); return this;
    }
    public SequencedAssemblyRecipeBuilder loops(int value) { if (value < 0) throw new IllegalArgumentException("Assembly loops must be nonnegative"); loops = value; return this; }
    public SequencedAssemblyRecipeBuilder addStep(RecipeType<?> type, UnaryOperator<ProcessingRecipeBuilder> transform) { return addStep(stepId -> new ProcessingRecipeBuilder(type, stepId), transform); }
    public SequencedAssemblyRecipeBuilder addStep(Function<Identifier, ProcessingRecipeBuilder> factory, UnaryOperator<ProcessingRecipeBuilder> transform) {
        if (transitional == null) throw new IllegalStateException("Choose a transitional item before adding assembly steps");
        var builder = transform.apply(factory.apply(id.withSuffix("/step_" + steps.size())).require(transitional.getItem()).output(transitional));
        if (!builder.conditions().isEmpty()) throw new IllegalArgumentException("Put load conditions on the assembly owner, not an individual step");
        steps.add(builder); return this;
    }
    public SequencedAssemblyRecipeBuilder addOutput(ItemLike item, float weight) { return addOutput(new ItemStack(item), weight); }
    public SequencedAssemblyRecipeBuilder addOutput(ItemStack stack, float weight) {
        if (stack.isEmpty() || stack.getCount() > 99 || !Float.isFinite(weight) || weight < 0) throw new IllegalArgumentException("Assembly output requires a nonempty item count 1..99 and a finite nonnegative weight");
        results.add(new ProcessingOutput(stack.getItem(), stack.getCount(), stack.getComponentsPatch(), weight)); return this;
    }
    public SequencedAssemblyRecipeBuilder withCondition(ResourceCondition value) { conditions.add(Objects.requireNonNull(value)); return this; }
    public SequencedAssemblyRecipeBuilder whenModLoaded(String mod) { return withCondition(ResourceConditions.allModsLoaded(mod)); }
    public SequencedAssemblyRecipeBuilder whenModMissing(String mod) { return withCondition(ResourceConditions.not(ResourceConditions.allModsLoaded(mod))); }
    public JsonObject buildJson(HolderLookup.Provider lookup) {
        if (ingredient == null || transitional == null || steps.isEmpty() || results.isEmpty() || (long) loops * steps.size() > Integer.MAX_VALUE || results.stream().mapToDouble(ProcessingOutput::chance).sum() <= 0)
            throw new IllegalStateException("Assembly requires ingredient, transition, steps, a positive output weight and a bounded total length");
        var json = RecipeData.object("type", "create:sequenced_assembly"); json.add("ingredient", ingredient.apply(lookup));
        json.add("transitional_item", RecipeData.encode(ProcessingOutput.CODEC, new ProcessingOutput(transitional.getItem(), 1, transitional.getComponentsPatch(), 1), lookup));
        var sequence = new JsonArray(); for (var step : steps) sequence.add(step.buildJson(lookup)); json.add("sequence", sequence);
        var pool = new JsonArray(); for (var result : results) pool.add(RecipeData.encode(ProcessingOutput.WEIGHTED_CODEC, result, lookup));
        json.add("results", pool); json.addProperty("loops", loops); RecipeData.decode(json, lookup); return json;
    }
    public RecipeHolder<SequencedAssemblyRecipe> build(HolderLookup.Provider lookup) { return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, id), (SequencedAssemblyRecipe) RecipeData.decode(buildJson(lookup), lookup)); }
    public void build(RecipeOutput output) {
        if (!(output instanceof RecipeDataOutput data)) throw new IllegalArgumentException("Use a registry-aware Create recipe provider");
        RecipeData.emit(output, id.withPrefix("sequenced_assembly/"), buildJson(data.registries()), conditions);
    }
}
