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

/** Genuine test-mod registration for one addon-like display source; no @GameTest cases. UNRUN. */
public final class DisplayWidgetApiTestFixtures implements ModInitializer {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("create_test", "custom_widget_source");
    public static final DisplaySource SOURCE = new CustomSource();
    public static final String NUMBER = "Threshold", CAPTION = "Caption";

    @Override
    public void onInitialize() {
        Registry.register(CreateBuiltInRegistries.DISPLAY_SOURCE, ID, SOURCE);
        DisplaySource.BY_BLOCK.add(Blocks.DIAMOND_BLOCK, SOURCE);
    }

    private static final class CustomSource extends DisplaySource {
        @Override
        public Component getName() {
            return Component.literal("Custom native display widgets");
        }

        @Override
        public void populateData(DisplayLinkContext context) {
            var data = context.sourceConfig();
            if (!data.contains(NUMBER)) data.putInt(NUMBER, 3);
            if (!data.contains(CAPTION)) data.putString(CAPTION, "seed");
        }

        @Override
        public List<DisplaySourceConfiguration> configuration(DisplayLinkContext context) {
            return List.of(
                DisplaySourceConfiguration.integer(NUMBER, -8, 9, 3, 1, 0, 7, 54,
                    "create_test.display.threshold", ""),
                DisplaySourceConfiguration.text(CAPTION, 8));
        }

        @Override
        public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
            var data = context.sourceConfig();
            return List.of(Component.literal(data.getStringOr(CAPTION, "") + ":" + data.getIntOr(NUMBER, 3)));
        }
    }
}
