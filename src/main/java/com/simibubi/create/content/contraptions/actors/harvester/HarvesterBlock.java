package com.simibubi.create.content.contraptions.actors.harvester;

import com.simibubi.create.content.contraptions.actors.AttachedActorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class HarvesterBlock extends AttachedActorBlock implements EntityBlock {
    public HarvesterBlock(Properties properties){super(properties.forceSolidOn());}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new HarvesterBlockEntity(pos,state);}
}
