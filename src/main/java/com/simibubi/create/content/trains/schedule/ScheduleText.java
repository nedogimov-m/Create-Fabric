package com.simibubi.create.content.trains.schedule;

import java.util.Arrays;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The source schedule translation keys, represented by native translatable components. */
public final class ScheduleText {
    private ScheduleText() {}

    public static MutableComponent translate(String key, Object... arguments) {
        return Component.translatable("create." + key, arguments);
    }

    public static List<Component> options(String prefix, String... keys) {
        return Arrays.stream(keys)
                .map(key -> (Component) translate(prefix == null ? key : prefix + "." + key)).toList();
    }
}
