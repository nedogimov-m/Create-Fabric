package com.simibubi.create.content.equipment.bell;

import com.simibubi.create.AllBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class PeculiarBellBlockEntity extends AbstractBellBlockEntity {
    public PeculiarBellBlockEntity(BlockPos pos,BlockState state) { super(AllBlockEntityTypes.PECULIAR_BELL,pos,state); }
}
