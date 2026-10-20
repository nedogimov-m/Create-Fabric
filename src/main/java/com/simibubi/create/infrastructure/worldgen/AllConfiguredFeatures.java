package com.simibubi.create.infrastructure.worldgen;
import com.simibubi.create.*;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
/** Source configured IDs now refer to actual polymorphic native Feature values. */
public class AllConfiguredFeatures {
    public static final ResourceKey<Feature> ZINC_ORE=key("zinc_ore"),STRIATED_ORES_OVERWORLD=key("striated_ores_overworld"),STRIATED_ORES_NETHER=key("striated_ores_nether");
    private static ResourceKey<Feature> key(String name){return ResourceKey.create(Registries.FEATURE,Create.asResource(name));}
    public static void bootstrap(BootstrapContext<Feature> context){
        context.register(ZINC_ORE,new OreFeature(List.of(BlockReplacement.replace(new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES),AllBlocks.ZINC_ORE.defaultBlockState()),BlockReplacement.replace(new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES),AllBlocks.DEEPSLATE_ZINC_ORE.defaultBlockState())),12));
        context.register(STRIATED_ORES_OVERWORLD,new LayeredOreFeature(new LayeredOreConfiguration(List.of(AllLayerPatterns.SCORIA.get(),AllLayerPatterns.CINNABAR.get(),AllLayerPatterns.MAGNETITE.get(),AllLayerPatterns.MALACHITE.get(),AllLayerPatterns.LIMESTONE.get(),AllLayerPatterns.OCHRESTONE.get()),32,0)));
        context.register(STRIATED_ORES_NETHER,new LayeredOreFeature(new LayeredOreConfiguration(List.of(AllLayerPatterns.SCORIA_NETHER.get(),AllLayerPatterns.SCORCHIA_NETHER.get()),32,0)));
    }
}
