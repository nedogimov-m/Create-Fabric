package com.simibubi.create.api.behaviour.display;

import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkMenu;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.CompoundTag;

/** Client factories for source-specific native widgets, labels and tooltips. */
public final class DisplaySourceConfigurationWidgets {
    public static final SimpleRegistry<DisplaySource, Factory> REGISTRY = SimpleRegistry.create();

    @FunctionalInterface
    public interface Factory {
        void create(Context context);
    }

    /**
     * The current menu snapshot and its actual editable draft. A factory replaces the default
     * option widgets; source.configuration() still declares the int/string fields accepted by
     * the authoritative server. Widgets can use native labels, tooltips and custom rendering.
     */
    public record Context(DisplayLinkMenu.Opening opening, DisplayLinkMenu.SourceEntry source,
            CompoundTag draft, Font font, int x, int y, int width,
            Consumer<AbstractWidget> widgetAdder) {
        public Context {
            Objects.requireNonNull(opening);
            Objects.requireNonNull(source);
            Objects.requireNonNull(draft);
            Objects.requireNonNull(font);
            Objects.requireNonNull(widgetAdder);
        }

        public <W extends AbstractWidget> W add(W widget) {
            widgetAdder.accept(Objects.requireNonNull(widget));
            return widget;
        }
    }

    public static boolean create(DisplaySource source, Context context) {
        if (source == null) return false;
        var factory = REGISTRY.get(source);
        if (factory == null) return false;
        factory.create(context);
        return true;
    }

    private DisplaySourceConfigurationWidgets() {}
}
