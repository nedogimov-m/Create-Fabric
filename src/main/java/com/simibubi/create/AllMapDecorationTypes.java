package com.simibubi.create;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;

/** Actual native map registry, shared by native map packet and native decoration sprite atlas. */
public final class AllMapDecorationTypes {
    public static final Holder<MapDecorationType> STATION_MAP_DECORATION=Registry.registerForHolder(BuiltInRegistries.MAP_DECORATION_TYPE,Create.asResource("station"),new MapDecorationType(Create.asResource("station"),true,true));
    public static void register(){}
    private AllMapDecorationTypes(){}
}
