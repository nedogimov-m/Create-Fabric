package com.simibubi.create.api.data.recipe;

import com.google.gson.*;
import java.util.*;
import java.util.function.Function;
import net.fabricmc.fabric.api.resource.conditions.v1.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.*;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

/** Source 9x9 mechanical patterns, native stack-template components and Fabric resource conditions. */
public class MechanicalCraftingRecipeBuilder {
    private final ItemStack result;
    private final List<String> pattern = new ArrayList<>();
    private final Map<Character, Function<HolderLookup.Provider, JsonElement>> key = new LinkedHashMap<>();
    private final Set<TagKey<Item>> tags = new HashSet<>();
    private final List<ResourceCondition> conditions = new ArrayList<>();
    private boolean acceptMirrored = true;
    public MechanicalCraftingRecipeBuilder(ItemLike item, int count) { this(new ItemStack(item, count)); }
    public MechanicalCraftingRecipeBuilder(ItemStack result) {
        if (result.isEmpty() || result.getCount() > 99) throw new IllegalArgumentException("Mechanical output must contain 1..99 items");
        this.result = result.copy();
    }
    public static MechanicalCraftingRecipeBuilder shapedRecipe(ItemLike result) { return shapedRecipe(result, 1); }
    public static MechanicalCraftingRecipeBuilder shapedRecipe(ItemLike result, int count) { return new MechanicalCraftingRecipeBuilder(result, count); }
    public MechanicalCraftingRecipeBuilder key(Character symbol, ItemLike item) { return key(symbol, Ingredient.of(item)); }
    public MechanicalCraftingRecipeBuilder key(Character symbol, Ingredient ingredient) { return key(symbol, lookup -> RecipeData.encode(Ingredient.CODEC, ingredient, lookup)); }
    public MechanicalCraftingRecipeBuilder key(Character symbol, TagKey<Item> tag) { tags.add(tag); return key(symbol, lookup -> new JsonPrimitive("#" + tag.location())); }
    private MechanicalCraftingRecipeBuilder key(Character symbol, Function<HolderLookup.Provider, JsonElement> ingredient) {
        if (symbol == null || symbol == ' ' || key.putIfAbsent(symbol, ingredient) != null) throw new IllegalArgumentException("Duplicate/reserved mechanical pattern key " + symbol);
        return this;
    }
    public MechanicalCraftingRecipeBuilder patternLine(String line) {
        if (line.isEmpty() || line.length() > 9 || pattern.size() >= 9 || !pattern.isEmpty() && pattern.getFirst().length() != line.length())
            throw new IllegalArgumentException("Mechanical pattern must be rectangular with width and height 1..9");
        pattern.add(line); return this;
    }
    public MechanicalCraftingRecipeBuilder disallowMirrored() { acceptMirrored = false; return this; }
    public MechanicalCraftingRecipeBuilder withCondition(ResourceCondition condition) { conditions.add(Objects.requireNonNull(condition)); return this; }
    public MechanicalCraftingRecipeBuilder whenModLoaded(String mod) { return withCondition(ResourceConditions.allModsLoaded(mod)); }
    public MechanicalCraftingRecipeBuilder whenModMissing(String mod) { return withCondition(ResourceConditions.not(ResourceConditions.allModsLoaded(mod))); }
    public JsonObject buildJson(HolderLookup.Provider lookup) {
        if (pattern.isEmpty()) throw new IllegalStateException("Mechanical recipe requires a pattern");
        var used = new HashSet<Character>();
        for (var row : pattern) for (char symbol : row.toCharArray()) if (symbol != ' ') {
            if (!key.containsKey(symbol)) throw new IllegalStateException("Undefined mechanical pattern key " + symbol);
            used.add(symbol);
        }
        if (used.isEmpty() || !used.equals(key.keySet())) throw new IllegalStateException("Empty mechanical pattern or unused keys");
        var data = RecipeData.object("type", "create:mechanical_crafting"); var keys = new JsonObject(); var rows = new JsonArray();
        key.forEach((symbol, ingredient) -> keys.add(symbol.toString(), ingredient.apply(lookup))); pattern.forEach(rows::add);
        data.add("key", keys); data.add("pattern", rows); data.add("result", RecipeData.encode(ItemStackTemplate.CODEC, ItemStackTemplate.fromNonEmptyStack(result), lookup));
        data.addProperty("accept_mirrored", acceptMirrored);
        if (tags.stream().allMatch(tag -> lookup.lookupOrThrow(Registries.ITEM).get(tag).isPresent())) RecipeData.decode(data, lookup);
        return data;
    }
    public void build(RecipeOutput output) { build(output, BuiltInRegistries.ITEM.getKey(result.getItem())); }
    public void build(RecipeOutput output, String id) {
        var location = Identifier.parse(id);
        if (location.equals(BuiltInRegistries.ITEM.getKey(result.getItem()))) throw new IllegalStateException("Use build(output) when recipe and output IDs agree");
        build(output, location);
    }
    public void build(RecipeOutput output, Identifier id) {
        if (!(output instanceof RecipeDataOutput data)) throw new IllegalArgumentException("Use a registry-aware Create recipe provider");
        RecipeData.emit(output, id, buildJson(data.registries()), conditions);
    }
}
