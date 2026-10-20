package com.simibubi.create.content.processing.recipe;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import java.util.Locale;

public enum HeatCondition implements StringRepresentable {
    NONE, HEATED, SUPERHEATED;
    public static final Codec<HeatCondition> CODEC = StringRepresentable.fromEnum(HeatCondition::values);
    public boolean test(HeatCondition available) { return available.ordinal() >= ordinal(); }
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
}
