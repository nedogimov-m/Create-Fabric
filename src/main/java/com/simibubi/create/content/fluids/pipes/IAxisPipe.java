package com.simibubi.create.content.fluids.pipes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
public interface IAxisPipe {
    Direction.Axis getAxis(BlockState state);
    static Direction.Axis getAxisOf(BlockState state){return state.getBlock() instanceof IAxisPipe pipe?pipe.getAxis(state):null;}
}
