package com.simibubi.create.foundation.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

/** Source keyed static recipe search, with the actual native manager/reload generation. */
public class RecipeFinder {
	private record CachedSearch(RecipeManager manager, Object generation,
			List<RecipeHolder<? extends Recipe<?>>> recipes) {}

	private static final Cache<Object, CachedSearch> CACHED_SEARCHES = CacheBuilder.newBuilder().build();
	public static final ResourceManagerReloadListener LISTENER = resourceManager -> CACHED_SEARCHES.invalidateAll();

	/** The same key denotes the same static predicate; null disables caching, as in the source API. */
	public static List<RecipeHolder<? extends Recipe<?>>> get(@Nullable Object cacheKey, Level level,
			Predicate<RecipeHolder<? extends Recipe<?>>> conditions) {
		if (!(level instanceof ServerLevel server))
			return new ArrayList<>();
		var manager = server.getServer().getRecipeManager();
		var generation = manager.getRecipes();
		if (cacheKey != null) {
			var cached = CACHED_SEARCHES.getIfPresent(cacheKey);
			if (cached != null && cached.manager() == manager && cached.generation() == generation)
				return cached.recipes();
		}
		var recipes = new ArrayList<RecipeHolder<? extends Recipe<?>>>();
		for (RecipeHolder<? extends Recipe<?>> recipe : generation)
			if (conditions.test(recipe))
				recipes.add(recipe);
		if (cacheKey != null)
			CACHED_SEARCHES.put(cacheKey, new CachedSearch(manager, generation, recipes));
		return recipes;
	}
}
