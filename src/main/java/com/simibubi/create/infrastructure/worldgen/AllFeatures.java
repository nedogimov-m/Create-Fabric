package com.simibubi.create.infrastructure.worldgen;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.Create;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
/** Actual native feature type; configured data lives in the dynamic Feature registry. */
public class AllFeatures {
    public static final MapCodec<LayeredOreFeature> LAYERED_ORE=Registry.register(BuiltInRegistries.FEATURE_TYPE,Create.asResource("layered_ore"),LayeredOreFeature.CODEC);
    public static void register(){}
}
