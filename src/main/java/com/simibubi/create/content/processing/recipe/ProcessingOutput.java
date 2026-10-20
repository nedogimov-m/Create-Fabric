package com.simibubi.create.content.processing.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Each output item receives its own probability roll, matching upstream Create. */
public record ProcessingOutput(Item item, int count, DataComponentPatch components, float chance) {
    public static final Codec<ProcessingOutput> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(ProcessingOutput::item),
            Codec.intRange(1, 99).optionalFieldOf("count", 1).forGetter(ProcessingOutput::count),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ProcessingOutput::components),
            Codec.floatRange(0, 1).optionalFieldOf("chance", 1f).forGetter(ProcessingOutput::chance)
    ).apply(instance, ProcessingOutput::new));
    public static final Codec<ProcessingOutput> WEIGHTED_CODEC=RecordCodecBuilder.create(instance->instance.group(
        BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(ProcessingOutput::item),Codec.intRange(1,99).optionalFieldOf("count",1).forGetter(ProcessingOutput::count),DataComponentPatch.CODEC.optionalFieldOf("components",DataComponentPatch.EMPTY).forGetter(ProcessingOutput::components),Codec.floatRange(0,Float.MAX_VALUE).optionalFieldOf("chance",1f).forGetter(ProcessingOutput::chance)
    ).apply(instance,ProcessingOutput::new));

    public ItemStack stack() {
        ItemStack stack = new ItemStack(item, count);
        stack.applyComponents(components);
        return stack;
    }

    public ItemStack rollOutput(RandomSource random) {
        int rolled = count;
        if (chance < 1) {
            for (int i = 0; i < count; i++) {
                if (random.nextFloat() > chance) {
                    rolled--;
                }
            }
        }
        return rolled == 0 ? ItemStack.EMPTY : stack().copyWithCount(rolled);
    }
}
