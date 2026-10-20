package com.simibubi.create.content.fluids.potion;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.Create;
import net.fabricmc.fabric.api.recipe.v1.ingredient.*;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.PotionIngredient;

/** Retains the native brewing reagent's optional potion predicate when used in a basin. */
public record BrewingReagentIngredient(PotionIngredient reagent) implements CustomIngredient {
    private static final MapCodec<BrewingReagentIngredient> CODEC=PotionIngredient.MAP_CODEC.codec().fieldOf("reagent").xmap(BrewingReagentIngredient::new,BrewingReagentIngredient::reagent);
    private static final CustomIngredientSerializer<BrewingReagentIngredient> SERIALIZER=new CustomIngredientSerializer<>(){
        @Override public Identifier getIdentifier(){return Create.asResource("brewing_reagent");}
        @Override public MapCodec<BrewingReagentIngredient> getCodec(){return CODEC;}
        @Override public StreamCodec<RegistryFriendlyByteBuf,BrewingReagentIngredient> getStreamCodec(){return PotionIngredient.STREAM_CODEC.map(BrewingReagentIngredient::new,BrewingReagentIngredient::reagent);}
    };
    public static void register(){CustomIngredientSerializer.register(SERIALIZER);}
    @Override public boolean test(ItemStack stack){return reagent.test(stack);}
    @Override public java.util.stream.Stream<Holder<Item>> items(){return reagent.ingredient().items();}
    @Override public boolean requiresTesting(){return reagent.potions().isPresent();}
    @Override public CustomIngredientSerializer<?> getSerializer(){return SERIALIZER;}
}
