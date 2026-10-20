package com.simibubi.create.api.data.recipe;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.*;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.*;
import net.minecraft.world.item.crafting.Recipe;

/** Native 26.3 DataProvider. Add to a Fabric data-generator pack with pack.addProvider. */
public abstract class BaseRecipeProvider implements DataProvider {
    protected final String modid;
    protected final List<GeneratedRecipe> all = new ArrayList<>();
    protected final CompletableFuture<HolderLookup.Provider> registries;
    private final PackOutput.PathProvider recipes, advancements;

    public BaseRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, String defaultNamespace) {
        Identifier.fromNamespaceAndPath(defaultNamespace, "validation");
        this.modid = defaultNamespace;
        this.registries = Objects.requireNonNull(registries);
        recipes = output.createPathProvider(PackOutput.Target.DATA_PACK, "recipe");
        advancements = output.createPathProvider(PackOutput.Target.DATA_PACK, "advancement");
    }
    protected Identifier asResource(String path) { return Identifier.fromNamespaceAndPath(modid, path); }
    protected GeneratedRecipe register(GeneratedRecipe recipe) { all.add(Objects.requireNonNull(recipe)); return recipe; }
    public void buildRecipes(RecipeOutput output) { List.copyOf(all).forEach(recipe -> recipe.register(output)); }
    @Override public String getName() { return modid + " recipes"; }
    @Override public CompletableFuture<?> run(CachedOutput output) {
        return registries.thenCompose(lookup -> {
            var prepared = new LinkedHashMap<Identifier, JsonObject>();
            var preparedAdvancements = new LinkedHashMap<Identifier, AdvancementHolder>();
            RecipeDataOutput collector = new RecipeDataOutput() {
                @Override public HolderLookup.Provider registries() { return lookup; }
                @Override public void acceptJson(Identifier id, JsonObject data) {
                    if (prepared.putIfAbsent(id, data.deepCopy()) != null)
                        throw new IllegalStateException("Duplicate generated recipe " + id);
                }
                @Override public void accept(ResourceKey<Recipe<?>> id, Recipe<?> recipe, AdvancementHolder advancement) {
                    acceptJson(id.identifier(), Recipe.DIRECT_CODEC.encodeStart(ops(), recipe).getOrThrow().getAsJsonObject());
                    if (advancement != null && preparedAdvancements.putIfAbsent(advancement.id(), advancement) != null)
                        throw new IllegalStateException("Duplicate generated advancement " + advancement.id());
                }
                @Override public Advancement.Builder advancement() { return Advancement.Builder.recipeAdvancement(); }
            };
            // Prepare all entries first; an invalid or duplicate recipe cannot publish a partial provider.
            buildRecipes(collector);
            var writes = new ArrayList<CompletableFuture<?>>();
            prepared.forEach((id, data) -> writes.add(DataProvider.saveStable(output, data, recipes.json(id))));
            preparedAdvancements.forEach((id, holder) -> writes.add(DataProvider.saveStable(output, lookup,
                Advancement.CODEC, holder.value(), advancements.json(id))));
            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        });
    }
    @FunctionalInterface public interface GeneratedRecipe { void register(RecipeOutput output); }
}
