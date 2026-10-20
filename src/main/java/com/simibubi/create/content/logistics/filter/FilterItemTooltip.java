package com.simibubi.create.content.logistics.filter;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Client supplies its Create Shift modifier without loading client classes on a server. */
public final class FilterItemTooltip {
    private static BooleanSupplier shiftDown = () -> false;
    public static boolean shiftDown() { return shiftDown.getAsBoolean(); }
    public static void setShiftDown(BooleanSupplier modifier) { shiftDown = Objects.requireNonNull(modifier); }
    private FilterItemTooltip() {}
}
