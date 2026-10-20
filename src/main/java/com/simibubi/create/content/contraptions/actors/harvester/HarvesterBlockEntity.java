package com.simibubi.create.content.contraptions.actors.harvester;

import com.simibubi.create.AllBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Animation override is transient, matching source scene/Ponder simulations. */
public class HarvesterBlockEntity extends com.simibubi.create.foundation.blockEntity.CachedRenderBBBlockEntity {
    private float manuallyAnimatedSpeed;
    public HarvesterBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.HARVESTER,pos,state);}
    public float getAnimatedSpeed(){return manuallyAnimatedSpeed;}
    public void setAnimatedSpeed(float speed){manuallyAnimatedSpeed=Float.isFinite(speed)?speed:0;}
}
