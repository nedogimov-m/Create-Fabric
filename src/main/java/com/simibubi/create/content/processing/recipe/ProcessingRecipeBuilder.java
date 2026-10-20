package com.simibubi.create.content.processing.recipe;

import com.google.gson.*;
import com.simibubi.create.api.data.recipe.*;
import com.simibubi.create.foundation.fluid.FluidStack;
import java.util.*;
import java.util.function.Function;
import net.fabricmc.fabric.api.resource.conditions.v1.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.*;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.*;

/** Builder for real native processing schemas. Fluid quantities are Fabric droplets throughout this API. */
public class ProcessingRecipeBuilder {
    private final Identifier type, id;
    private final List<Function<HolderLookup.Provider, JsonElement>> items = new ArrayList<>(), fluids = new ArrayList<>(),
        outputs = new ArrayList<>(), fluidOutputs = new ArrayList<>();
    private final List<ResourceCondition> conditions = new ArrayList<>();
    private final Set<Identifier> itemReferences = new HashSet<>(), outputReferences = new HashSet<>();
    private final Set<TagKey<Item>> itemTags = new HashSet<>();
    private final Set<String> requiredMods = new HashSet<>();
    private int duration = 100;
    private HeatCondition heat = HeatCondition.NONE;
    private boolean keepHeldItem;

    public ProcessingRecipeBuilder(Identifier type, Identifier id) { this.type = Objects.requireNonNull(type); this.id = Objects.requireNonNull(id); }
    public ProcessingRecipeBuilder(RecipeType<?> type, Identifier id) { this(BuiltInRegistries.RECIPE_TYPE.getKey(type), id); }
    public Identifier type() { return type; }
    public Identifier id() { return id; }
    public ProcessingRecipeBuilder require(Ingredient ingredient) { items.add(lookup -> RecipeData.encode(Ingredient.CODEC, ingredient, lookup)); return this; }
    public ProcessingRecipeBuilder require(ItemLike item) { return require(Ingredient.of(item)); }
    public ProcessingRecipeBuilder require(TagKey<Item> tag) {
        itemTags.add(tag); items.add(lookup -> new JsonPrimitive("#" + tag.location())); return this;
    }
    public ProcessingRecipeBuilder require(Identifier item) {
        itemReferences.add(item); items.add(lookup -> new JsonPrimitive(item.toString())); return this;
    }
    public ProcessingRecipeBuilder require(DatagenMod mod, String item) { return require(mod.asResource(item)); }
    public ProcessingRecipeBuilder require(FluidIngredient fluid) { fluids.add(lookup -> RecipeData.encode(FluidIngredient.CODEC, fluid, lookup)); return this; }
    public ProcessingRecipeBuilder require(Fluid fluid, long droplets) {
        if (fluid instanceof FlowingFluid flowing) fluid = flowing.getSource();
        return require(new FluidIngredient(new FluidIngredient.ExactSelector(fluid), positiveFluid(droplets)));
    }
    public ProcessingRecipeBuilder require(TagKey<Fluid> tag, long droplets) { return require(new FluidIngredient(new FluidIngredient.TagSelector(tag), positiveFluid(droplets))); }
    public ProcessingRecipeBuilder withItemIngredients(Ingredient... values) { items.clear(); itemReferences.clear(); itemTags.clear(); for (var value : values) require(value); return this; }
    public ProcessingRecipeBuilder withFluidIngredients(FluidIngredient... values) { fluids.clear(); for (var value : values) require(value); return this; }
    public ProcessingRecipeBuilder output(ItemLike item) { return output(item, 1); }
    public ProcessingRecipeBuilder output(ItemLike item, int count) { return output(1f, new ItemStack(item, count)); }
    public ProcessingRecipeBuilder output(float chance, ItemLike item) { return output(chance, item, 1); }
    public ProcessingRecipeBuilder output(float chance, ItemLike item, int count) { return output(chance, new ItemStack(item, count)); }
    public ProcessingRecipeBuilder output(ItemStack stack) { return output(1f, stack); }
    public ProcessingRecipeBuilder output(float chance, ItemStack stack) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Processing output must be nonempty");
        return output(new ProcessingOutput(stack.getItem(), stack.getCount(), stack.getComponentsPatch(), chance));
    }
    public ProcessingRecipeBuilder output(ProcessingOutput value) {
        count(value.count()); probability(value.chance());
        outputs.add(lookup -> RecipeData.encode(ProcessingOutput.CODEC, value, lookup)); return this;
    }
    public ProcessingRecipeBuilder output(Identifier item) { return output(1f, item, 1); }
    public ProcessingRecipeBuilder output(DatagenMod mod, String item) { return output(mod.asResource(item)); }
    public ProcessingRecipeBuilder output(float chance, DatagenMod mod, String item, int count) { return output(chance, mod.asResource(item), count); }
    public ProcessingRecipeBuilder output(float chance, Identifier item, int count) {
        probability(chance); count(count); outputReferences.add(item);
        outputs.add(lookup -> { var result = RecipeData.object("id", item.toString()); result.addProperty("count", count); result.addProperty("chance", chance); return result; }); return this;
    }
    public ProcessingRecipeBuilder output(Fluid fluid, long droplets) {
        if (fluid instanceof FlowingFluid flowing) fluid = flowing.getSource();
        return output(new FluidOutput(fluid, positiveFluid(droplets), net.minecraft.core.component.DataComponentPatch.EMPTY));
    }
    public ProcessingRecipeBuilder output(FluidStack fluid) { return output(new FluidOutput(fluid.variant().getFluid(), positiveFluid(fluid.getAmount()), fluid.variant().getComponentsPatch())); }
    public ProcessingRecipeBuilder output(FluidOutput fluid) { positiveFluid(fluid.amount()); fluidOutputs.add(lookup -> RecipeData.encode(FluidOutput.CODEC, fluid, lookup)); return this; }
    public ProcessingRecipeBuilder withItemOutputs(ProcessingOutput... values) { outputs.clear(); outputReferences.clear(); for (var value : values) output(value); return this; }
    public ProcessingRecipeBuilder withSingleItemOutput(ItemStack value) { outputs.clear(); outputReferences.clear(); return output(value); }
    public ProcessingRecipeBuilder withFluidOutputs(FluidOutput... values) { fluidOutputs.clear(); for (var value : values) output(value); return this; }
    public ProcessingRecipeBuilder duration(int ticks) { if (ticks < 1 || ticks > 1_000_000) throw new IllegalArgumentException("Duration must be 1..1000000 ticks"); duration = ticks; return this; }
    public ProcessingRecipeBuilder averageProcessingDuration() { return duration(100); }
    public ProcessingRecipeBuilder requiresHeat(HeatCondition value) { heat = Objects.requireNonNull(value); return this; }
    public ProcessingRecipeBuilder keepHeldItem() { keepHeldItem = true; return this; }
    public ProcessingRecipeBuilder toolNotConsumed() { return keepHeldItem(); }
    public ProcessingRecipeBuilder whenModLoaded(String mod) { requiredMods.add(mod); return withCondition(ResourceConditions.allModsLoaded(mod)); }
    public ProcessingRecipeBuilder whenModMissing(String mod) { return withCondition(ResourceConditions.not(ResourceConditions.allModsLoaded(mod))); }
    public ProcessingRecipeBuilder withCondition(ResourceCondition condition) { conditions.add(Objects.requireNonNull(condition)); return this; }
    public List<ResourceCondition> conditions() { return List.copyOf(conditions); }

    public JsonObject buildJson(HolderLookup.Provider lookup) {
        validateShape();
        var data = RecipeData.object("type", type.toString()); var ingredients = new JsonArray(); var results = new JsonArray();
        items.forEach(value -> ingredients.add(value.apply(lookup))); fluids.forEach(value -> ingredients.add(value.apply(lookup)));
        outputs.forEach(value -> results.add(value.apply(lookup))); fluidOutputs.forEach(value -> results.add(value.apply(lookup)));
        data.add("ingredients", ingredients); data.add("results", results);
        if (Set.of("milling", "crushing", "cutting", "mixing", "compacting").contains(type.getPath())) data.addProperty("processing_time", duration);
        if (Set.of("mixing", "compacting").contains(type.getPath())) data.addProperty("heat_requirement", heat.getSerializedName());
        if (Set.of("deploying", "item_application").contains(type.getPath())) data.addProperty("keep_held_item", keepHeldItem);
        boolean deferred = false;
        var referencedItems = new HashSet<>(itemReferences); referencedItems.addAll(outputReferences);
        for (var item : referencedItems) if (!BuiltInRegistries.ITEM.containsKey(item)) {
            if (!requiredMods.contains(item.getNamespace())) throw new IllegalArgumentException("Absent item " + item + " requires an explicit whenModLoaded condition for its namespace");
            deferred = true;
        }
        for (var tag : itemTags) if (lookup.lookupOrThrow(Registries.ITEM).get(tag).isEmpty()) deferred = true;
        if (!deferred) RecipeData.decode(data, lookup);
        return data;
    }
    public Recipe<?> build(HolderLookup.Provider lookup) { return RecipeData.decode(buildJson(lookup), lookup); }
    public void build(RecipeOutput output) {
        if (!(output instanceof RecipeDataOutput data)) throw new IllegalArgumentException("Use a registry-aware Create recipe provider");
        RecipeData.emit(output, id.withPrefix(type.getPath() + "/"), buildJson(data.registries()), conditions);
    }
    private void validateShape() {
        if (!type.getNamespace().equals("create")) return; // A custom registered serializer owns its own schema validation.
        int max = switch (type.getPath()) { case "crushing" -> 7; case "milling", "cutting", "deploying", "item_application" -> 4; case "splashing", "haunting" -> 12; case "mixing", "compacting" -> 16; default -> 1; };
        boolean basin = Set.of("mixing", "compacting").contains(type.getPath()), filling = type.getPath().equals("filling"), emptying = type.getPath().equals("emptying"), application = Set.of("deploying", "item_application").contains(type.getPath());
        if (basin) {
            if (items.size() + fluids.size() < 1 || items.size() + fluids.size() > 32 || outputs.size() + fluidOutputs.size() < 1 || outputs.size() + fluidOutputs.size() > max) throw new IllegalArgumentException("Invalid native basin input/output count");
        } else if (items.size() != (application ? 2 : 1) || fluids.size() != (filling ? 1 : 0)
                || fluidOutputs.size() != (emptying ? 1 : 0) || outputs.size() < (emptying ? 0 : 1) || outputs.size() > max)
            throw new IllegalArgumentException("Invalid native processing input/output counts for " + type);
        if (!basin && heat != HeatCondition.NONE) throw new IllegalArgumentException("Only basin recipes accept heat requirements");
        if (!application && keepHeldItem) throw new IllegalArgumentException("Only item application recipes retain held tools");
    }
    private static long positiveFluid(long amount) { if (amount <= 0 || amount > Long.MAX_VALUE / 64) throw new IllegalArgumentException("Invalid fluid droplets " + amount); return amount; }
    private static void count(int count) { if (count < 1 || count > 99) throw new IllegalArgumentException("Output count must be 1..99"); }
    private static void probability(float chance) { if (!Float.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("Output probability must be 0..1"); }
}
