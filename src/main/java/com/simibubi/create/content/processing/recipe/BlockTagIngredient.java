package com.simibubi.create.content.processing.recipe;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.Create;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/** Source block-tag ingredients retain datapack membership instead of a fixed item-tag copy. */
public record BlockTagIngredient(TagKey<Block> tag) implements CustomIngredient {
    private static final MapCodec<BlockTagIngredient> CODEC = TagKey.codec(Registries.BLOCK)
        .xmap(BlockTagIngredient::new, BlockTagIngredient::tag).fieldOf("tag");
    private static final CustomIngredientSerializer<BlockTagIngredient> SERIALIZER = new CustomIngredientSerializer<>() {
        @Override public Identifier getIdentifier() { return Create.asResource("block_tag"); }
        @Override public MapCodec<BlockTagIngredient> getCodec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, BlockTagIngredient> getStreamCodec() {
            return Identifier.STREAM_CODEC.<RegistryFriendlyByteBuf>cast()
                .map(id -> new BlockTagIngredient(TagKey.create(Registries.BLOCK, id)), value -> value.tag.location());
        }
    };
    public static void register() { CustomIngredientSerializer.register(SERIALIZER); }
    @Override public boolean test(ItemStack stack) {
        return !stack.isEmpty() && items().anyMatch(item -> stack.is(item.value()));
    }
    @Override public Stream<Holder<Item>> items() {
        var items = matchingItems();
        return items.isEmpty() ? Stream.of(Items.BARRIER.builtInRegistryHolder()) : items.stream();
    }
    private java.util.List<Holder<Item>> matchingItems() {
        return java.util.stream.StreamSupport.stream(BuiltInRegistries.BLOCK.getTagOrEmpty(tag).spliterator(), false)
            .map(block -> block.value().asItem()).filter(item -> item != Items.AIR).distinct()
            .map(item -> (Holder<Item>) item.builtInRegistryHolder()).toList();
    }
    @Override public SlotDisplay display() {
        if (!matchingItems().isEmpty()) return CustomIngredient.super.display();
        var placeholder = new ItemStack(Items.BARRIER);
        placeholder.set(DataComponents.CUSTOM_NAME, Component.literal("Empty Tag: " + tag.location()));
        return new SlotDisplay.ItemStackSlotDisplay(net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(placeholder));
    }
    @Override public boolean requiresTesting() { return true; }
    @Override public CustomIngredientSerializer<?> getSerializer() { return SERIALIZER; }
}
