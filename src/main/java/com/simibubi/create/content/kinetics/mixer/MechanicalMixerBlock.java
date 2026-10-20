package com.simibubi.create.content.kinetics.mixer;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.content.processing.basin.BasinBlock;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;

public final class MechanicalMixerBlock extends KineticBlock implements ICogWheel {
    public MechanicalMixerBlock(Properties properties) { super(properties); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return false; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return box(0, 2, 0, 16, 16, 16); }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return !(level.getBlockState(pos.below()).getBlock() instanceof BasinBlock); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MechanicalMixerBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, AllBlockEntityTypes.MECHANICAL_MIXER, MechanicalMixerBlockEntity::tick);
    }
}
