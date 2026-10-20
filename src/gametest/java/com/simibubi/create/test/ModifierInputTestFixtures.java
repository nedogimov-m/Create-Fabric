package com.simibubi.create.test;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.DisplaySourceConfiguration;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

/** One actual registered menu source, its authoritative scalar and real sign output. */
public final class ModifierInputTestFixtures implements ModInitializer {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("create_test", "modifier_input_source");
    public static final String VALUE = "Threshold";
    public static final DisplaySource SOURCE = new DisplaySource() {
        @Override public Component getName() { return Component.literal("Create modifier input"); }
        @Override public void populateData(DisplayLinkContext context) {
            if (!context.sourceConfig().contains(VALUE)) context.sourceConfig().putInt(VALUE, 20);
        }
        @Override public List<DisplaySourceConfiguration> configuration(DisplayLinkContext context) {
            return List.of(DisplaySourceConfiguration.integer(VALUE, 0, 101, 20, 5, 0, 0, 100,
                "create_test.modifier_threshold", ""));
        }
        @Override public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
            return List.of(Component.literal("modifier=" + context.sourceConfig().getIntOr(VALUE, 20)));
        }
    };
    @Override public void onInitialize() {
        Registry.register(CreateBuiltInRegistries.DISPLAY_SOURCE, ID, SOURCE);
        DisplaySource.BY_BLOCK.add(Blocks.EMERALD_BLOCK, SOURCE);
    }
}
