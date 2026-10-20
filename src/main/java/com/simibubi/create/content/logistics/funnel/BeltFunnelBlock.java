package com.simibubi.create.content.logistics.funnel;

import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.util.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

public final class BeltFunnelBlock extends AbstractFunnelBlock {
    public static final EnumProperty<Direction> HORIZONTAL_FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Shape> SHAPE=EnumProperty.create("shape",Shape.class);
    public enum Shape implements StringRepresentable {RETRACTED,EXTENDED,PUSHING,PULLING;@Override public String getSerializedName(){return name().toLowerCase(java.util.Locale.ROOT);}}
    public BeltFunnelBlock(Properties properties,boolean brass){super(properties,brass);registerDefaultState(defaultBlockState().setValue(HORIZONTAL_FACING,Direction.NORTH).setValue(SHAPE,Shape.RETRACTED));}
    @Override protected net.minecraft.world.item.ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state,boolean data){return new net.minecraft.world.item.ItemStack(parent());}
    public FunnelBlock parent(){return brass?com.simibubi.create.AllBlocks.BRASS_FUNNEL:com.simibubi.create.AllBlocks.ANDESITE_FUNNEL;}
    public boolean isOfSameType(FunnelBlock funnel){return parent()==funnel;}
    @Override protected Direction getFacing(BlockState state){return state.getValue(HORIZONTAL_FACING);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(HORIZONTAL_FACING,SHAPE);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(HORIZONTAL_FACING,rotation.rotate(getFacing(state)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(getFacing(state)));}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var facing=context.getClickedFace().getAxis().isHorizontal()?context.getClickedFace():context.getHorizontalDirection();return super.getStateForPlacement(context).setValue(HORIZONTAL_FACING,facing).setValue(SHAPE,getShapeForPosition(context.getLevel(),context.getClickedPos(),facing,!context.isSecondaryUseActive()));}
    public static Shape getShapeForPosition(BlockGetter level,BlockPos pos,Direction facing,boolean extracting){var below=LoadedLevelAccess.blockState(level,pos.below());return below.getBlock() instanceof BeltBlock&&below.getValue(BeltBlock.HORIZONTAL_FACING).getAxis()==facing.getAxis()?Shape.RETRACTED:extracting?Shape.PUSHING:Shape.PULLING;}
    public static boolean isOnValidBelt(BlockState state,BlockGetter level,BlockPos pos){var below=LoadedLevelAccess.blockState(level,pos.below());if(below.getBlock() instanceof BeltBlock)return BeltBlock.canTransportObjects(below);var input=com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(level,pos.below(),com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.TYPE);return input!=null&&input.canSupportBeltFunnels();}

    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos neighborPos,BlockState neighbor,RandomSource random){
        super.updateShape(state,level,ticks,pos,side,neighborPos,neighbor,random);if(!isOnValidBelt(state,level,pos))return parent().defaultBlockState().setValue(FunnelBlock.FACING,getFacing(state)).setValue(FunnelBlock.EXTRACTING,state.getValue(SHAPE)==Shape.PUSHING).setValue(POWERED,state.getValue(POWERED)).setValue(WATERLOGGED,state.getValue(WATERLOGGED));var previous=state.getValue(SHAPE);var next=getShapeForPosition(level,pos,getFacing(state),previous==Shape.PUSHING);return next==previous||next==Shape.PUSHING&&previous==Shape.PULLING||next==Shape.RETRACTED&&previous==Shape.EXTENDED?state:state.setValue(SHAPE,next);
    }
    @Override public InteractionResult onWrenched(BlockState state,UseOnContext context){var previous=state.getValue(SHAPE);var below=LoadedLevelAccess.blockState(context.getLevel(),context.getClickedPos().below());var next=switch(previous){case PUSHING->Shape.PULLING;case PULLING->Shape.PUSHING;case EXTENDED->Shape.RETRACTED;case RETRACTED->below.getBlock() instanceof BeltBlock&&below.getValue(BeltBlock.SLOPE)!=BeltSlope.HORIZONTAL?Shape.RETRACTED:Shape.EXTENDED;};if(!context.getLevel().isClientSide()){
        context.getLevel().setBlockAndUpdate(context.getClickedPos(),state.setValue(SHAPE,next));
        if(next==Shape.EXTENDED){var facing=state.getValue(HORIZONTAL_FACING);var adjacent=LoadedLevelAccess.blockState(context.getLevel(),context.getClickedPos().relative(facing));if(adjacent.getBlock() instanceof BeltFunnelBlock&&adjacent.getValue(SHAPE)==Shape.EXTENDED&&adjacent.getValue(HORIZONTAL_FACING)==facing.getOpposite())com.simibubi.create.foundation.advancement.GameplayAdvancements.award(context.getPlayer(),"belt_funnel_kiss");}
    }return InteractionResult.SUCCESS;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return FunnelShapes.belt(getFacing(state),state.getValue(SHAPE));}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){if(context instanceof EntityCollisionContext entity&&entity.getEntity() instanceof ItemEntity&&(state.getValue(SHAPE)==Shape.PUSHING||state.getValue(SHAPE)==Shape.PULLING))return FunnelShapes.collision(getFacing(state));return getShape(state,level,pos,context);}
}
