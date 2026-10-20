package com.simibubi.create.api.data.recipe;

import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.ItemLike;

/** Generator helpers use the actual native recipe type and registry-aware builder. */
public abstract class ProcessingRecipeGen<R extends Recipe<?>> extends BaseRecipeProvider {
    public ProcessingRecipeGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, String namespace) { super(output, registries, namespace); }
    protected abstract RecipeType<R> getRecipeType();
    protected ProcessingRecipeBuilder getBuilder(Identifier id) { return new ProcessingRecipeBuilder(getRecipeType(), id); }
    protected GeneratedRecipe create(String namespace, Supplier<? extends ItemLike> input, UnaryOperator<ProcessingRecipeBuilder> transform) {
        return register(output -> {
            var item = input.get(); var id = BuiltInRegistries.ITEM.getKey(item.asItem());
            transform.apply(getBuilder(Identifier.fromNamespaceAndPath(namespace, id.getPath())).require(item)).build(output);
        });
    }
    protected GeneratedRecipe create(Supplier<? extends ItemLike> input, UnaryOperator<ProcessingRecipeBuilder> transform) { return create(modid, input, transform); }
    protected GeneratedRecipe createWithDeferredId(Supplier<Identifier> id, UnaryOperator<ProcessingRecipeBuilder> transform) { return register(output -> transform.apply(getBuilder(id.get())).build(output)); }
    protected GeneratedRecipe create(Identifier id, UnaryOperator<ProcessingRecipeBuilder> transform) { return createWithDeferredId(() -> id, transform); }
    protected GeneratedRecipe create(String id, UnaryOperator<ProcessingRecipeBuilder> transform) { return create(asResource(id), transform); }
    protected Supplier<Identifier> idWithSuffix(Supplier<? extends ItemLike> item, String suffix) { return () -> asResource(BuiltInRegistries.ITEM.getKey(item.get().asItem()).getPath() + suffix); }
    @Override public String getName() { return modid + " processing recipes: " + BuiltInRegistries.RECIPE_TYPE.getKey(getRecipeType()); }
}
