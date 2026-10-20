package com.simibubi.create.content.redstone.displayLink;

import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/** Source option data shared by actual native widgets, authoritative menu snapshots and validation. */
public record DisplaySourceConfiguration(String key, List<String> choices, int maxLength,
        int minimum, int maximum, int shiftStep, int defaultValue,
        int widgetLine, int widgetOffset, int widgetWidth, String title, String suffix) {
    public DisplaySourceConfiguration {
        choices = List.copyOf(choices);
        if (key.isBlank()) throw new IllegalArgumentException("Empty display option key");
        maxLength = Math.clamp(maxLength, 0, 256);
        Objects.requireNonNull(title); Objects.requireNonNull(suffix);
        if (widgetLine < -1 || widgetLine > 1 || widgetOffset < 0 || widgetOffset > 137
                || widgetWidth < 1 || widgetWidth > 137 || shiftStep < 1)
            throw new IllegalArgumentException("Invalid actual source widget bounds");
        if (maximum < minimum || maximum > minimum && (!choices.isEmpty() || maxLength != 0
                || defaultValue < minimum || defaultValue >= maximum))
            throw new IllegalArgumentException("Invalid actual source integer range");
    }
    public DisplaySourceConfiguration(String key, List<String> choices, int maxLength) {
        this(key, choices, maxLength, 0, 0, 1, 0, -1, 0, 135, "", "");
    }
    public static DisplaySourceConfiguration text(String key, int maxLength) {
        return new DisplaySourceConfiguration(key, List.of(), maxLength);
    }
    public static DisplaySourceConfiguration choice(String key, String... choices) {
        if (choices.length == 0) throw new IllegalArgumentException("A display selection needs actual choices");
        return new DisplaySourceConfiguration(key, List.of(choices), 0);
    }
    public static DisplaySourceConfiguration integer(String key, int minimum, int maximum,
            int defaultValue, int shiftStep, int line, int offset, int width, String title, String suffix) {
        return new DisplaySourceConfiguration(key, List.of(), 0, minimum, maximum, shiftStep, defaultValue,
                line, offset, width, title, suffix);
    }
    public boolean isNumber() { return maximum > minimum; }
    public boolean isText() { return choices.isEmpty() && !isNumber(); }
    public int number(CompoundTag data) { return Math.clamp(data.getIntOr(key, defaultValue), minimum, maximum - 1); }
    public void copyValidated(CompoundTag from, CompoundTag to) {
        if (isNumber()) to.putInt(key, number(from));
        else if (isText()) {
            String value = from.getStringOr(key, "");
            to.putString(key, value.substring(0, Math.min(value.length(), maxLength)));
        } else to.putInt(key, Math.clamp(from.getIntOr(key, 0), 0, choices.size() - 1));
    }
}
