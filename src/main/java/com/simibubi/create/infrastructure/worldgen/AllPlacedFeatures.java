package com.simibubi.create.infrastructure.worldgen;
import com.simibubi.create.Create;
import com.simibubi.create.infrastructure.config.CWorldGen;
import net.fabricmc.fabric.api.biome.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
/** Original source tag selectors and underground-ores step, using actual Fabric biome hooks. */
public final class AllPlacedFeatures {
    public static final ResourceKey<PlacedFeature> ZINC_ORE=key("zinc_ore"),STRIATED_ORES_OVERWORLD=key("striated_ores_overworld"),STRIATED_ORES_NETHER=key("striated_ores_nether");
    private static ResourceKey<PlacedFeature> key(String id){return ResourceKey.create(Registries.PLACED_FEATURE,Create.asResource(id));}
    private static boolean registered;
    public static void register(){if(registered)return;registered=true;AllFeatures.register();AllPlacementModifiers.register();CWorldGen.reload();BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_OVERWORLD),GenerationStep.Decoration.UNDERGROUND_ORES,ZINC_ORE);BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_OVERWORLD),GenerationStep.Decoration.UNDERGROUND_ORES,STRIATED_ORES_OVERWORLD);BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_NETHER),GenerationStep.Decoration.UNDERGROUND_ORES,STRIATED_ORES_NETHER);}
    private AllPlacedFeatures(){}
}
