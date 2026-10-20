package com.simibubi.create.content.logistics.chute;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.funnel.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;

public final class ChuteBlock extends AbstractChuteBlock implements SimpleWaterloggedBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.FACING_HOPPER;
    public static final EnumProperty<Shape> SHAPE=EnumProperty.create("shape",Shape.class);
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    public enum Shape implements StringRepresentable {INTERSECTION,WINDOW,NORMAL,ENCASED;public String getSerializedName(){return name().toLowerCase(java.util.Locale.ROOT);}}
    public ChuteBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(FACING,Direction.DOWN).setValue(SHAPE,Shape.NORMAL).setValue(WATERLOGGED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,SHAPE,WATERLOGGED);}
    @Override public Direction getFacing(BlockState state){return state.getValue(FACING);}
    @Override public boolean isOpen(BlockState state){return getFacing(state)==Direction.DOWN||state.getValue(SHAPE)==Shape.INTERSECTION;}
    @Override public boolean isTransparent(BlockState state){return state.getValue(SHAPE)==Shape.WINDOW;}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos neighborPos,BlockState neighbor,RandomSource random){if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return super.updateShape(state,level,ticks,pos,side,neighborPos,neighbor,random);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var state=defaultBlockState().setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).getType()==Fluids.WATER);if(context.getClickedFace().getAxis().isHorizontal()&&!context.isSecondaryUseActive())return updateChuteState(state.setValue(FACING,context.getClickedFace()),LoadedLevelAccess.blockState(context.getLevel(),context.getClickedPos().above()),context.getLevel(),context.getClickedPos());return state;}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){var above=LoadedLevelAccess.blockState(level,pos.above());return !isChute(above)||getChuteFacing(above)==Direction.DOWN;}
    @Override public BlockState updateChuteState(BlockState state,BlockState above,BlockGetter level,BlockPos pos){
        var facing=state.getValue(FACING);boolean vertical=facing==Direction.DOWN;
        if(!vertical&&!isChute(LoadedLevelAccess.blockState(level,pos.below().relative(facing.getOpposite())))){
            if(level instanceof Level world&&!world.isLoaded(pos.below().relative(facing.getOpposite())))return state;
            return state.setValue(FACING,Direction.DOWN).setValue(SHAPE,Shape.NORMAL);
        }
        var connections=java.util.EnumSet.noneOf(Direction.class);for(var side:Direction.Plane.HORIZONTAL){var input=LoadedLevelAccess.blockState(level,pos.above().relative(side));if(input.getBlock() instanceof ChuteBlock&&input.getValue(FACING)==side)connections.add(side);}
        var shape=state.getValue(SHAPE);
        if(vertical)return state.setValue(SHAPE,connections.isEmpty()?shape==Shape.INTERSECTION?Shape.NORMAL:shape:Shape.INTERSECTION);
        if(connections.isEmpty()||connections.contains(Direction.NORTH)&&connections.contains(Direction.SOUTH)||connections.contains(Direction.EAST)&&connections.contains(Direction.WEST))return state.setValue(SHAPE,Shape.INTERSECTION);
        if(connections.size()==1&&connections.contains(facing)&&getChuteFacing(above)!=Direction.DOWN&&!(above.getBlock() instanceof FunnelBlock&&AbstractFunnelBlock.getFunnelFacing(above)==Direction.DOWN))return state.setValue(SHAPE,shape==Shape.ENCASED?Shape.ENCASED:Shape.NORMAL);
        return state.setValue(SHAPE,Shape.INTERSECTION);
    }
    public InteractionResult onWrenched(BlockState state,UseOnContext context){var shape=state.getValue(SHAPE);if(shape==Shape.INTERSECTION)return InteractionResult.PASS;if(!context.getLevel().isClientSide()){if(shape==Shape.ENCASED){context.getLevel().setBlockAndUpdate(context.getClickedPos(),state.setValue(SHAPE,Shape.NORMAL));context.getLevel().levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK,context.getClickedPos(),Block.getId(AllBlocks.INDUSTRIAL_IRON_BLOCK.defaultBlockState()));}else if(getFacing(state)==Direction.DOWN)context.getLevel().setBlockAndUpdate(context.getClickedPos(),state.setValue(SHAPE,shape==Shape.NORMAL?Shape.WINDOW:Shape.NORMAL));}return InteractionResult.SUCCESS;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(stack.is(AllBlocks.INDUSTRIAL_IRON_BLOCK.asItem())&&state.getValue(SHAPE)!=Shape.INTERSECTION&&state.getValue(SHAPE)!=Shape.ENCASED&&player.mayBuild()&&!player.isSpectator()&&level.mayInteract(player,pos)){if(!level.isClientSide()){level.setBlockAndUpdate(pos,state.setValue(SHAPE,Shape.ENCASED));level.playSound(null,pos,net.minecraft.sounds.SoundEvents.NETHERITE_BLOCK_HIT,net.minecraft.sounds.SoundSource.BLOCKS,.5f,1.05f);}return InteractionResult.SUCCESS;}return super.useItemOn(stack,state,level,pos,player,hand,hit);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(getFacing(state)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(getFacing(state)));}
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
}
