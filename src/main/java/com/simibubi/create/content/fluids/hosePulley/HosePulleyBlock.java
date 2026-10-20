package com.simibubi.create.content.fluids.hosePulley;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

public final class HosePulleyBlock extends HorizontalKineticBlock {
    public HosePulleyBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(HORIZONTAL_FACING,Direction.NORTH));}
    @Override public void setPlacedBy(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof HosePulleyBlockEntity device)device.sourceAdvancements.place(placer);}
    @Override public Direction.Axis getRotationAxis(BlockState state){return state.getValue(HORIZONTAL_FACING).getClockWise().getAxis();}
    @Override public boolean hasShaftTowards(LevelReader level,BlockPos pos,BlockState state,Direction side){return state.getValue(HORIZONTAL_FACING).getClockWise()==side;}
    public static boolean hasPipeTowards(BlockState state,Direction side){return state.getValue(HORIZONTAL_FACING).getCounterClockWise()==side;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var preferred=getPreferredHorizontalFacing(context);return defaultBlockState().setValue(HORIZONTAL_FACING,preferred==null?context.getHorizontalDirection().getOpposite():preferred.getCounterClockWise());}
    @Override public Direction getPreferredHorizontalFacing(BlockPlaceContext context){
        var parent=super.getPreferredHorizontalFacing(context);if(parent!=null)return parent;
        Direction preferred=null;
        for(var side:Direction.Plane.HORIZONTAL){
            var pos=context.getClickedPos().relative(side);var state=LoadedLevelAccess.blockState(context.getLevel(),pos);
            if(FluidPipeBlock.canConnectTo(context.getLevel(),pos,state,side)){
                if(preferred!=null&&preferred.getAxis()!=side.getAxis())return null;preferred=side;
            }
        }return preferred==null?null:preferred.getOpposite();
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new HosePulleyBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.HOSE_PULLEY,HosePulleyBlockEntity::tick);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
}
