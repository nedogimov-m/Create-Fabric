package com.simibubi.create.content.kinetics.base;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Two shaft faces use actual solver provenance rather than the faster output port. */
public class DirectionalShaftHalvesBlockEntity extends KineticBlockEntity {
    public DirectionalShaftHalvesBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    public Direction getSourceFacing(){return getKineticSourceFace();}
}
