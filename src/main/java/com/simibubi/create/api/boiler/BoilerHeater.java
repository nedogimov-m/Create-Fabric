package com.simibubi.create.api.boiler;

import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.content.fluids.tank.BoilerHeaters;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

@FunctionalInterface
public interface BoilerHeater {
    int PASSIVE_HEAT=0,NO_HEAT=-1;
    BoilerHeater PASSIVE=BoilerHeaters::passive,BLAZE_BURNER=BoilerHeaters::blazeBurner;
    SimpleRegistry<Block,BoilerHeater> REGISTRY=SimpleRegistry.create();
    static float findHeat(Level level,BlockPos pos,BlockState state){var heater=REGISTRY.get(state);return heater==null?NO_HEAT:heater.getHeat(level,pos,state);}
    float getHeat(Level level,BlockPos pos,BlockState state);
}
