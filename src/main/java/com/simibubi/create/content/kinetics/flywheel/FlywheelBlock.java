package com.simibubi.create.content.kinetics.flywheel;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

public final class FlywheelBlock extends RotatedPillarKineticBlock {
    public FlywheelBlock(Properties properties) { super(properties); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(AXIS); }
    @Override public boolean hasShaftTowards(LevelReader level,BlockPos pos,BlockState state,Direction face) { return face.getAxis() == state.getValue(AXIS); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> Shapes.or(box(6,0,0,10,16,16),box(0,5,5,16,11,11));
            case Y -> Shapes.or(box(0,6,0,16,10,16),box(5,0,5,11,16,11));
            case Z -> Shapes.or(box(0,0,6,16,16,10),box(5,5,0,11,11,16));
        };
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new FlywheelBlockEntity(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) { return createTickerHelper(type,AllBlockEntityTypes.FLYWHEEL,FlywheelBlockEntity::tick); }
}
