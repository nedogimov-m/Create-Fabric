package com.simibubi.create.content.kinetics.flywheel;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class FlywheelBlockEntity extends KineticBlockEntity {
    private float visualSpeed,angle;
    public FlywheelBlockEntity(BlockPos pos,BlockState state) { super(AllBlockEntityTypes.FLYWHEEL,pos,state); }
    public float getVisualAngle(float partial) { return angle+visualSpeed*.3f*partial; }
    public static void tick(Level level,BlockPos pos,BlockState state,FlywheelBlockEntity entity) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, entity)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level,pos,state,entity);
        if (!level.isClientSide()) return;
        entity.visualSpeed += (entity.getSpeed()-entity.visualSpeed)/64;
        entity.angle = (entity.angle+entity.visualSpeed*.3f)%360;
    
        }
    }
}
