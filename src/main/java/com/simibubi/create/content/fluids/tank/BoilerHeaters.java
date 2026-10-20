package com.simibubi.create.content.fluids.tank;

import com.simibubi.create.*;
import com.simibubi.create.api.boiler.BoilerHeater;
import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class BoilerHeaters {
    private BoilerHeaters(){}
    public static void registerDefaults(){
        BoilerHeater.REGISTRY.register(AllBlocks.BLAZE_BURNER,BoilerHeater.BLAZE_BURNER);
        BoilerHeater.REGISTRY.registerProvider(SimpleRegistry.Provider.forBlockTag(TagKey.create(Registries.BLOCK,Create.asResource("passive_boiler_heaters")),BoilerHeater.PASSIVE));
    }
    public static int passive(Level level,BlockPos pos,BlockState state){return !state.hasProperty(BlockStateProperties.LIT)||state.getValue(BlockStateProperties.LIT)?BoilerHeater.PASSIVE_HEAT:BoilerHeater.NO_HEAT;}
    public static int blazeBurner(Level level,BlockPos pos,BlockState state){
        return switch(state.getValue(BlazeBurnerBlock.HEAT_LEVEL)){case NONE->BoilerHeater.NO_HEAT;case SMOULDERING->BoilerHeater.PASSIVE_HEAT;case FADING,KINDLED->1;case SEETHING->2;};
    }
}
