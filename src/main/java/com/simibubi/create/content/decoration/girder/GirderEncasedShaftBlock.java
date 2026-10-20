package com.simibubi.create.content.decoration.girder;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Actual native kinetic shaft across the beam, using the existing real ENCASED_SHAFT BE type. */
public final class GirderEncasedShaftBlock extends HorizontalAxisKineticBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty TOP=GirderBlock.TOP,BOTTOM=GirderBlock.BOTTOM,WATERLOGGED=GirderBlock.WATERLOGGED;
    public GirderEncasedShaftBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(TOP,false).setValue(BOTTOM,false).setValue(WATERLOGGED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(TOP,BOTTOM,WATERLOGGED);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return GirderShapes.shaft(state.getValue(HORIZONTAL_AXIS));}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.or(getShape(state,level,pos,CollisionContext.empty()),GirderShapes.POLE);}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return super.getStateForPlacement(context).setValue(WATERLOGGED,LoadedLevelAccess.blockState(context.getLevel(),context.getClickedPos()).getFluidState().getType()==Fluids.WATER);}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos next,BlockState neighbor,RandomSource random){
        if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));if(side.getAxis()!=Direction.Axis.Y||!GirderBlock.available(level,next))return state;
        var property=side==Direction.UP?TOP:BOTTOM;if(neighbor.getBlockSupportShape(level,next).isEmpty())state=state.setValue(property,false);return GirderBlock.updateVerticalProperty(level,pos,state,property,neighbor,side);
    }
    public BlockState getRotatedBlockState(BlockState state,Direction face){var shaft=state.getValue(HORIZONTAL_AXIS);return AllBlocks.METAL_GIRDER.defaultBlockState().setValue(WATERLOGGED,state.getValue(WATERLOGGED)).setValue(GirderBlock.X,shaft==Direction.Axis.Z).setValue(GirderBlock.Z,shaft==Direction.Axis.X).setValue(GirderBlock.AXIS,shaft==Direction.Axis.X?Direction.Axis.Z:Direction.Axis.X).setValue(TOP,state.getValue(TOP)).setValue(BOTTOM,state.getValue(BOTTOM));}
    public InteractionResult onWrenched(BlockState state,UseOnContext context){
        var level=context.getLevel();var pos=context.getClickedPos();if(level.isClientSide())return InteractionResult.SUCCESS;
        var next=Block.updateFromNeighbourShapes(getRotatedBlockState(state,context.getClickedFace()),level,pos);
        if(LoadedLevelAccess.blockState(level,pos)!=state||!next.canSurvive(level,pos)||!level.setBlock(pos,next,Block.UPDATE_ALL))return InteractionResult.FAIL;
        var player=context.getPlayer();if(player!=null&&!player.isCreative())player.getInventory().placeItemBackInInventory(new ItemStack(AllBlocks.SHAFT),Prediction.SERVER_ONLY);
        level.playSound(null,pos,AllSoundEvents.WRENCH_ROTATE,SoundSource.BLOCKS,1,1);return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return stack.is(AllBlocks.METAL_GIRDER.asItem())?GirderPlacementHelper.place(player,hand,level,state,pos,hit):InteractionResult.PASS;}
}
