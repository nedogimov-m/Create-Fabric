package com.simibubi.create.content.kinetics.turntable;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class TurntableBlockEntity extends KineticBlockEntity {
    public TurntableBlockEntity(BlockPos pos,BlockState state) { super(AllBlockEntityTypes.TURNTABLE,pos,state); }
}
