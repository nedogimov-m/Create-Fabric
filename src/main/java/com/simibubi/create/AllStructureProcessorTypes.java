package com.simibubi.create;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.schematics.SchematicProcessor;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Current native structure-processor registry stores MapCodecs directly. */
public final class AllStructureProcessorTypes {
    public static final MapCodec<SchematicProcessor> SCHEMATIC=Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR,Create.asResource("schematic"),SchematicProcessor.CODEC);
    public static MapCodec<SchematicProcessor> register(){return SCHEMATIC;}
    private AllStructureProcessorTypes(){}
}
