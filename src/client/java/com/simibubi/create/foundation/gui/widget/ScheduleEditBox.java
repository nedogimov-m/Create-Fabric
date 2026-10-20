package com.simibubi.create.foundation.gui.widget;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;

/** Native EditBox with the source schedule's pre-mutation integer/glob validation. */
public final class ScheduleEditBox extends EditBox {
    private Predicate<String> filter = value -> true;
    private int selectionEnd;
    private int maximumLength = 32;

    public ScheduleEditBox(Font font, int x, int y, int width, int height, Component title) {
        super(font, x, y, width, height, title);
    }

    public void setFilter(Predicate<String> filter) {
        this.filter = Objects.requireNonNull(filter);
    }

    @Override
    public void setValue(String value) {
        if (filter == null || filter.test(value)) super.setValue(value);
    }

    @Override
    public void setHighlightPos(int position) {
        super.setHighlightPos(position);
        selectionEnd = Math.clamp(position, 0, getValue().length());
    }

    @Override
    public void setMaxLength(int maximumLength) {
        this.maximumLength = maximumLength;
        super.setMaxLength(maximumLength);
    }

    @Override
    public void insertText(String text) {
        int from = Math.min(getCursorPosition(), selectionEnd);
        int to = Math.max(getCursorPosition(), selectionEnd);
        int available = maximumLength - getValue().length() - from + to;
        if (available <= 0) return;
        String filtered = StringUtil.filterText(text);
        if (filtered.length() > available) {
            if (Character.isHighSurrogate(filtered.charAt(available - 1))) available--;
            filtered = filtered.substring(0, available);
        }
        String proposed = new StringBuilder(getValue()).replace(from, to, filtered).toString();
        if (filter.test(proposed)) super.insertText(text);
    }

    @Override
    public void deleteCharsToPos(int position) {
        if (!getHighlighted().isEmpty()) {
            insertText("");
            return;
        }
        int target = Math.clamp(position, 0, getValue().length());
        int from = Math.min(target, getCursorPosition());
        int to = Math.max(target, getCursorPosition());
        String proposed = new StringBuilder(getValue()).delete(from, to).toString();
        if (filter.test(proposed)) super.deleteCharsToPos(position);
    }
}
