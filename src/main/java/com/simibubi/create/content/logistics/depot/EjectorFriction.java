package com.simibubi.create.content.logistics.depot;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Native26.3 removed the world/position/entity Block.getFriction overload. */
public final class EjectorFriction {
    public static float value(Entity entity,float original){return value(entity.level(),entity.getBlockPosBelowThatAffectsMyMovement(),original);}
    public static float value(Level level,BlockPos pos,float original){
        return LoadedLevelAccess.blockEntity(level,pos) instanceof EjectorBlockEntity ejector
                && ejector.getState()==EjectorBlockEntity.State.LAUNCHING?1:original;
    }
    private EjectorFriction(){}
}
