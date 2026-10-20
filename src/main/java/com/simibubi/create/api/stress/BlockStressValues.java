package com.simibubi.create.api.stress;

import com.simibubi.create.api.registry.SimpleRegistry;
import net.minecraft.world.level.block.Block;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;

/** Stress units at one RPM. Values follow the upstream Create defaults. */
public final class BlockStressValues {
    /** Source tooltip metadata; never changes actual generator behavior or stress accounting. */
    public record GeneratedRpm(int value, boolean mayGenerateLess) {}
    public static final SimpleRegistry<Block, GeneratedRpm> RPM = SimpleRegistry.create();
    /** Suppliers of the base stress impact at one RPM, including addon providers. */
    public static final SimpleRegistry<Block, DoubleSupplier> IMPACTS = SimpleRegistry.create();
    /** Suppliers of the base stress capacity at one RPM, including addon providers. */
    public static final SimpleRegistry<Block, DoubleSupplier> CAPACITIES = SimpleRegistry.create();

    // Native block bootstrap defaults are fallback providers. Direct addon registrations retain
    // source priority, while the later CStress config providers override these defaults.
    private static final Map<Block, DoubleSupplier> DEFAULT_IMPACTS = new IdentityHashMap<>();
    private static final Map<Block, DoubleSupplier> DEFAULT_CAPACITIES = new IdentityHashMap<>();
    static {
        IMPACTS.registerProvider(DEFAULT_IMPACTS::get);
        CAPACITIES.registerProvider(DEFAULT_CAPACITIES::get);
    }

    public static java.util.function.Consumer<Block> setGeneratorSpeed(int value) {
        return setGeneratorSpeed(value, false);
    }
    public static java.util.function.Consumer<Block> setGeneratorSpeed(int value, boolean mayGenerateLess) {
        return block -> RPM.register(block, new GeneratedRpm(value, mayGenerateLess));
    }

    /** Native bootstrap fallback; addons can register live suppliers through IMPACTS. */
    public static void registerImpact(Block block, double impact) {
        DEFAULT_IMPACTS.put(block, () -> impact);
        IMPACTS.invalidate();
    }

    /** Native bootstrap fallback; addons can register live suppliers through CAPACITIES. */
    public static void registerCapacity(Block block, double capacity) {
        DEFAULT_CAPACITIES.put(block, () -> capacity);
        CAPACITIES.invalidate();
    }

    public static double getImpact(Block block) {
        DoubleSupplier supplier = IMPACTS.get(block);
        return supplier == null ? 0 : supplier.getAsDouble();
    }

    public static double getCapacity(Block block) {
        DoubleSupplier supplier = CAPACITIES.get(block);
        return supplier == null ? 0 : supplier.getAsDouble();
    }

    private BlockStressValues() {}
}
