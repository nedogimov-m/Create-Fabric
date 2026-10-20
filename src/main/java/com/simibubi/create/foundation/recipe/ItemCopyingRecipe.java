package com.simibubi.create.foundation.recipe;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.Create;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Source component-copying recipe, including removal of enchantments from actual copies. */
public final class ItemCopyingRecipe extends CustomRecipe {
    public interface SupportsItemCopying {
        default ItemStack createCopy(ItemStack original, int count) {
            ItemStack copy = original.copyWithCount(count);
            copy.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            copy.remove(DataComponents.STORED_ENCHANTMENTS);
            return copy;
        }
        default boolean canCopyFromItem(ItemStack item) { return item.has(getComponentType()); }
        default boolean canCopyToItem(ItemStack item) { return !item.has(getComponentType()); }
        DataComponentType<?> getComponentType();
    }

    public static final MapCodec<ItemCopyingRecipe> CODEC = CraftingBookCategory.CODEC
            .optionalFieldOf("category", CraftingBookCategory.MISC)
            .xmap(ItemCopyingRecipe::new, ItemCopyingRecipe::category);
    public static final RecipeSerializer<ItemCopyingRecipe> SERIALIZER = Registry.register(
            BuiltInRegistries.RECIPE_SERIALIZER, Create.asResource("item_copying"),
            new RecipeSerializer<>(CODEC, StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, ItemCopyingRecipe recipe) -> buffer.writeEnum(recipe.category),
                    buffer -> new ItemCopyingRecipe(buffer.readEnum(CraftingBookCategory.class)))));
    private final CraftingBookCategory category;
    private record CopyCheck(ItemStack original, int targets) {}

    public ItemCopyingRecipe(CraftingBookCategory category) { this.category = category; }
    @Override public CraftingBookCategory category() { return category; }
    @Override public boolean matches(CraftingInput input, Level level) { return copyCheck(input) != null; }
    @Override public ItemStack assemble(CraftingInput input) {
        CopyCheck check = copyCheck(input);
        if (check == null || !(check.original.getItem() instanceof SupportsItemCopying copying)) return ItemStack.EMPTY;
        return copying.createCopy(check.original, check.targets + 1);
    }
    private static @Nullable CopyCheck copyCheck(CraftingInput input) {
        ItemStack source = ItemStack.EMPTY;
        int targets = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack item = input.getItem(slot);
            if (item.isEmpty()) continue;
            if (!(item.getItem() instanceof SupportsItemCopying copying)) return null;
            if (!copying.canCopyFromItem(item)) continue;
            source = item;
            break;
        }
        if (source.isEmpty()) return null;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack item = input.getItem(slot);
            if (item.isEmpty() || item == source) continue;
            if (item.getItem() != source.getItem() || !(item.getItem() instanceof SupportsItemCopying copying)) return null;
            if (copying.canCopyFromItem(item) || !copying.canCopyToItem(item)) return null;
            targets++;
        }
        return targets == 0 ? null : new CopyCheck(source, targets);
    }
    @Override public RecipeSerializer<ItemCopyingRecipe> getSerializer() { return SERIALIZER; }
    public static void register() { /* Initializes the actual registered native serializer. */ }
}
