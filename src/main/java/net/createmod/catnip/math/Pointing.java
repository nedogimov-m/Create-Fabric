package net.createmod.catnip.math;

import net.minecraft.util.StringRepresentable;
import java.util.Locale;

public enum Pointing implements StringRepresentable {
    UP(0), LEFT(270), DOWN(180), RIGHT(90);
    private final int rotation;
    Pointing(int rotation) { this.rotation = rotation; }
    public int getXRotation() { return rotation; }
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
}
