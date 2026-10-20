package com.simibubi.create.content.fluids.pipes;

import com.simibubi.create.foundation.fluid.FluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public final class VanillaFluidTargets {
    public static boolean canProvideFluidWithoutCapability(BlockState state){return state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_HONEY)||state.is(Blocks.CAULDRON)||state.is(Blocks.WATER_CAULDRON)||state.is(Blocks.LAVA_CAULDRON);}
    public static FluidStack fluid(BlockState state){
        if(state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_HONEY)&&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL_HONEY)>=5)return new FluidStack(FluidVariant.of(com.simibubi.create.AllFluids.HONEY),FluidConstants.BUCKET/4);
        if(state.is(Blocks.LAVA_CAULDRON))return new FluidStack(FluidVariant.of(Fluids.LAVA),FluidConstants.BUCKET);
        if(state.is(Blocks.WATER_CAULDRON)&&state.getBlock() instanceof LayeredCauldronBlock cauldron&&cauldron.isFull(state))return new FluidStack(FluidVariant.of(Fluids.WATER),FluidConstants.BUCKET);
        return FluidStack.EMPTY;
    }
}
