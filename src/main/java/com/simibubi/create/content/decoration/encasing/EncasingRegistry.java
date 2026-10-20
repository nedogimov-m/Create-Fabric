package com.simibubi.create.content.decoration.encasing;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;

/** Source encasing API. Invoke registration callbacks after both native blocks exist. */
public final class EncasingRegistry {
    private static final Map<Block, List<Block>> ENCASED_VARIANTS = new IdentityHashMap<>();

    public static <B extends Block & EncasableBlock, E extends Block & EncasedBlock, P>
    void addVariant(B encasable, E encased) {
        var variants = ENCASED_VARIANTS.computeIfAbsent(encasable, block -> new ArrayList<>());
        if (!variants.contains(encased)) variants.add(encased);
    }

    public static List<Block> getVariants(Block block) {
        return List.copyOf(ENCASED_VARIANTS.getOrDefault(block, List.of()));
    }

    /** Native equivalent of the source Registrate onRegisterAfter callback. */
    public static <B extends Block & EncasedBlock, E extends Block & EncasableBlock>
    Consumer<B> addVariantTo(Supplier<E> encasable) {
        return encased -> addVariant(encasable.get(), encased);
    }

    private EncasingRegistry() {}
}
