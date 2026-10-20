package com.simibubi.create.content.kinetics.fan.processing;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;

/** The source live sorted view, refreshed for genuine Fabric addon registration. */
public final class FanProcessingTypeRegistry {
    private static final List<FanProcessingType> SORTED_TYPES=new ArrayList<>();
    public static final List<FanProcessingType> SORTED_TYPES_VIEW=Collections.unmodifiableList(SORTED_TYPES);
    private static boolean listening;
    public static void init(){
        SORTED_TYPES.clear();CreateBuiltInRegistries.FAN_PROCESSING_TYPE.forEach(SORTED_TYPES::add);
        SORTED_TYPES.sort(Comparator.comparingInt(FanProcessingType::getPriority).reversed());
    }
    static void ensureInitialized(){
        if(!listening){
            listening=true;
            RegistryEntryAddedCallback.event(CreateBuiltInRegistries.FAN_PROCESSING_TYPE).register((rawId,id,type)->init());
            init();
        }
    }
    private FanProcessingTypeRegistry(){}
}
