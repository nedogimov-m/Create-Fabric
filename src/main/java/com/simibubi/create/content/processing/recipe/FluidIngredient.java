package com.simibubi.create.content.processing.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.world.level.material.Fluid;
import java.util.function.Predicate;

public record FluidIngredient(Selector predicate, long amount) {
    public static final Codec<Long> AMOUNT_CODEC = Codec.LONG.validate(value -> value > 0 && value <= Long.MAX_VALUE / 64
            ? com.mojang.serialization.DataResult.success(value)
            : com.mojang.serialization.DataResult.error(() -> "Invalid fluid amount: " + value));
    private record Exact(Fluid fluid,long amount,DataComponentExactPredicate components) {}
    private record Tagged(TagKey<Fluid> tag,long amount,DataComponentExactPredicate components) {}
    private static final Codec<Exact> EXACT = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(Exact::fluid),
            AMOUNT_CODEC.fieldOf("amount").forGetter(Exact::amount),
            DataComponentExactPredicate.CODEC.optionalFieldOf("components",DataComponentExactPredicate.EMPTY).forGetter(Exact::components)
    ).apply(i, Exact::new));
    private static final Codec<Tagged> TAGGED = RecordCodecBuilder.create(i -> i.group(
            TagKey.codec(Registries.FLUID).fieldOf("fluid_tag").forGetter(Tagged::tag),
            AMOUNT_CODEC.fieldOf("amount").forGetter(Tagged::amount),
            DataComponentExactPredicate.CODEC.optionalFieldOf("components",DataComponentExactPredicate.EMPTY).forGetter(Tagged::components)
    ).apply(i, Tagged::new));

    // Keep the original selector, so recipe synchronization uses the same exact/tag semantics as loading.
    public sealed interface Selector extends Predicate<FluidVariant> permits ExactSelector, TagSelector {}
    public record ExactSelector(Fluid fluid,DataComponentExactPredicate components) implements Selector {
        public ExactSelector(Fluid fluid){this(fluid,DataComponentExactPredicate.EMPTY);}
        @Override public boolean test(FluidVariant variant) { return !variant.isBlank() && variant.getFluid().isSame(fluid)&&components.test(variant.getComponents()); }
    }
    public record TagSelector(TagKey<Fluid> tag,DataComponentExactPredicate components) implements Selector {
        public TagSelector(TagKey<Fluid> tag){this(tag,DataComponentExactPredicate.EMPTY);}
        @Override public boolean test(FluidVariant variant) { return !variant.isBlank() && variant.getFluid().is(tag)&&components.test(variant.getComponents()); }
    }
    public static final Codec<FluidIngredient> CODEC = Codec.either(EXACT, TAGGED).xmap(
            either -> either.map(v -> new FluidIngredient(new ExactSelector(v.fluid(),v.components()), v.amount()),
                    v -> new FluidIngredient(new TagSelector(v.tag(),v.components()), v.amount())),
            ingredient -> ingredient.predicate() instanceof ExactSelector exact
                    ? com.mojang.datafixers.util.Either.left(new Exact(exact.fluid(), ingredient.amount(),exact.components()))
                    : com.mojang.datafixers.util.Either.right(new Tagged(((TagSelector) ingredient.predicate()).tag(), ingredient.amount(),((TagSelector)ingredient.predicate()).components())));
    public boolean test(FluidVariant variant) { return predicate.test(variant); }
}
