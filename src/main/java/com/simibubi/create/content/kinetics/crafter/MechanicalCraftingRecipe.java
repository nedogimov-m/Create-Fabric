package com.simibubi.create.content.kinetics.crafter;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllRecipeTypes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.*;

/** The 9x9 mechanical pattern is independent of the vanilla crafting table's size limit. */
public record MechanicalCraftingRecipe(Map<String, Ingredient> key, List<String> pattern, ItemStackTemplate result,
                                       boolean acceptMirrored) implements Recipe<CraftingInput> {
    private static final MapCodec<MechanicalCraftingRecipe> RAW_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Ingredient.CODEC).fieldOf("key").forGetter(MechanicalCraftingRecipe::key),
            Codec.STRING.listOf(1, 9).fieldOf("pattern").forGetter(MechanicalCraftingRecipe::pattern),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(MechanicalCraftingRecipe::result),
            Codec.BOOL.optionalFieldOf("accept_mirrored", false).forGetter(MechanicalCraftingRecipe::acceptMirrored)
    ).apply(i, MechanicalCraftingRecipe::new));
    public static final MapCodec<MechanicalCraftingRecipe> CODEC = RAW_CODEC.flatXmap(MechanicalCraftingRecipe::validate, DataResult::success);
    private static DataResult<MechanicalCraftingRecipe> validate(MechanicalCraftingRecipe recipe) {
        if (recipe.pattern.isEmpty() || recipe.pattern.size() > 9) return DataResult.error(() -> "Mechanical recipe height must be 1..9");
        int width = recipe.pattern.getFirst().length();
        if (width < 1 || width > 9 || recipe.pattern.stream().anyMatch(s -> s.length() != width)) return DataResult.error(() -> "Mechanical recipe rows must have equal width 1..9");
        if (recipe.key.keySet().stream().anyMatch(k -> k.length() != 1 || k.equals(" "))) return DataResult.error(() -> "Recipe keys must be one non-space character");
        var used = new HashSet<String>();
        for (String row : recipe.pattern) for (char symbol : row.toCharArray()) if (symbol != ' ') {
            String token = String.valueOf(symbol);
            if (!recipe.key.containsKey(token)) return DataResult.error(() -> "Undefined recipe symbol: " + token);
            used.add(token);
        }
        if (used.isEmpty() || !used.equals(recipe.key.keySet())) return DataResult.error(() -> "Empty recipe or unused ingredient keys");
        int top = 0, bottom = recipe.pattern.size() - 1, left = width, right = -1;
        while (recipe.pattern.get(top).chars().allMatch(c -> c == ' ')) top++;
        while (recipe.pattern.get(bottom).chars().allMatch(c -> c == ' ')) bottom--;
        for (int y = top; y <= bottom; y++) for (int x = 0; x < width; x++) if (recipe.pattern.get(y).charAt(x) != ' ') { left = Math.min(left, x); right = Math.max(right, x); }
        var trimmed = new ArrayList<String>(); for (int y = top; y <= bottom; y++) trimmed.add(recipe.pattern.get(y).substring(left, right + 1));
        return DataResult.success(new MechanicalCraftingRecipe(Map.copyOf(recipe.key), List.copyOf(trimmed), recipe.result, recipe.acceptMirrored));
    }
    @Override public boolean matches(CraftingInput input, Level level) {
        if (input.width() != pattern.getFirst().length() || input.height() != pattern.size()) return false;
        return matches(input, false) || acceptMirrored && matches(input, true);
    }
    private boolean matches(CraftingInput input, boolean mirrored) {
        int width = input.width();
        for (int y = 0; y < input.height(); y++) for (int x = 0; x < width; x++) {
            char symbol = pattern.get(y).charAt(mirrored ? width - 1 - x : x);
            var stack = input.getItem(x, y);
            if (symbol == ' ' ? !stack.isEmpty() : !key.get(String.valueOf(symbol)).test(stack)) return false;
        }
        return true;
    }
    @Override public ItemStack assemble(CraftingInput input) { return result.create(); }
    @Override public boolean isSpecial() { return true; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public RecipeSerializer<MechanicalCraftingRecipe> getSerializer() { return AllRecipeTypes.MECHANICAL_CRAFTING_SERIALIZER; }
    @Override public RecipeType<MechanicalCraftingRecipe> getType() { return AllRecipeTypes.MECHANICAL_CRAFTING; }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}
