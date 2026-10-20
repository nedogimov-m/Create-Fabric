package com.simibubi.create.infrastructure.worldgen;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.Create;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
/** Native26.3 registers actual MapCodec types, replacing NeoForge DeferredHolder. */
public class AllPlacementModifiers {
    public static final MapCodec<ConfigPlacementFilter> CONFIG_FILTER=Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE,Create.asResource("config_filter"),ConfigPlacementFilter.CODEC);
    public static void register(){}
}
