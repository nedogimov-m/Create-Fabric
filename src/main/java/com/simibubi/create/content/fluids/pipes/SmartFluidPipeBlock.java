package com.simibubi.create.content.fluids.pipes;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

public final class SmartFluidPipeBlock extends BaseEntityBlock implements IAxisPipe,SimpleWaterloggedBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<AttachFace> FACE=BlockStateProperties.ATTACH_FACE;
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    public SmartFluidPipeBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.SOUTH).setValue(FACE,AttachFace.FLOOR).setValue(WATERLOGGED,false));}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof FluidPipeBlockEntity device)device.sourceAdvancements.place(placer);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,FACE,WATERLOGGED);}
    public static Direction.Axis getPipeAxis(BlockState state){return state.getValue(FACE)==AttachFace.WALL?Direction.Axis.Y:state.getValue(FACING).getAxis();}
    @Override public Direction.Axis getAxis(BlockState state){return getPipeAxis(state);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new SmartFluidPipeBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.SMART_FLUID_PIPE,FluidPipeBlockEntity::tick);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var nearest=context.getNearestLookingDirections()[0];var state=defaultBlockState().setValue(FACE,nearest.getAxis().isVertical()?nearest==Direction.UP?AttachFace.CEILING:AttachFace.FLOOR:AttachFace.WALL).setValue(FACING,nearest.getAxis().isVertical()?context.getHorizontalDirection():nearest.getOpposite());
        Direction.Axis preferred=null;
        for(var side:Direction.values()){
            var pos=context.getClickedPos().relative(side);if(!FluidPipeBlock.canConnectTo(context.getLevel(),pos,FluidPropagator.stateAt(context.getLevel(),pos),side))continue;
            if(preferred!=null&&preferred!=side.getAxis()){preferred=null;break;}preferred=side.getAxis();
        }
        if(preferred==Direction.Axis.Y)state=state.setValue(FACE,AttachFace.WALL).setValue(FACING,state.getValue(FACING).getOpposite());
        else if(preferred!=null){if(state.getValue(FACE)==AttachFace.WALL)state=state.setValue(FACE,AttachFace.FLOOR);for(var side:context.getNearestLookingDirections())if(side.getAxis()==preferred)state=state.setValue(FACING,side.getOpposite());}
        return state.setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).is(net.minecraft.tags.FluidTags.WATER));
    }
    public static float angleY(BlockState state){return switch(state.getValue(FACING)){case NORTH->180;case WEST->-90;case EAST->-270;default->0;}+(state.getValue(FACE)==AttachFace.WALL?180:0);}
    public static Vec3 filterPosition(BlockState state){
        var face=state.getValue(FACE);float y=face==AttachFace.CEILING?0.55f:face==AttachFace.WALL?11.4f:15.45f,z=face==AttachFace.CEILING?4.6f:face==AttachFace.WALL?0.55f:4.625f;
        return new Vec3(0,y/16-0.5,z/16-0.5).yRot((float)Math.toRadians(angleY(state))).add(0.5,0.5,0.5);
    }
    public static boolean filterHit(BlockState state,BlockPos pos,BlockHitResult hit){return hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)).distanceToSqr(filterPosition(state))<0.25*0.25;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!player.mayBuild()||player.isSpectator()||stack.is(AllItems.WRENCH)||!filterHit(state,pos,hit))return InteractionResult.PASS;
        if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof SmartFluidPipeBlockEntity pipe)pipe.filtering.applyPlayerSample(player,hand);
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!player.mayBuild()||!filterHit(state,pos,hit))return InteractionResult.PASS;
        if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof SmartFluidPipeBlockEntity pipe)pipe.filtering.applyPlayerSample(player,InteractionHand.MAIN_HAND);return InteractionResult.SUCCESS;
    }
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):Fluids.EMPTY.defaultFluidState();}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos next,BlockState neighbor,RandomSource random){if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return state;}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){if(level.getBlockEntity(pos) instanceof SmartFluidPipeBlockEntity pipe)pipe.updateNetwork();}
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){for(var side:Direction.values()){var pipe=FluidPropagator.getPipe(level,pos.relative(side));if(pipe!=null)FluidPropagator.propagateChangedPipe(level,pos.relative(side),pipe.blockEntity.getBlockState());}}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.setValue(FACING,mirror.mirror(state.getValue(FACING)));}
    public static BlockState wrenchRotation(BlockState state,Direction face){return face.getAxis().isVertical()?state.setValue(FACING,state.getValue(FACING).getClockWise()):state.cycle(FACE);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        var face=state.getValue(FACE);var boxes=new ArrayList<AABB>();boxes.add(new AABB(3,3,3,13,13,13));
        if(face==AttachFace.WALL){boxes.add(new AABB(4,0,4,12,16,12));boxes.add(new AABB(5,5,13,11,13,14));boxes.add(new AABB(5,6,14,11,12,15));boxes.add(new AABB(5,7,15,11,11,16));boxes.add(new AABB(5,8,16,11,10,17));}
        else{boxes.add(new AABB(4,4,0,12,12,16));if(face==AttachFace.FLOOR){boxes.add(new AABB(5,13,3,11,14,11));boxes.add(new AABB(5,14,4,11,15,10));boxes.add(new AABB(5,15,5,11,16,9));boxes.add(new AABB(5,16,6,11,17,8));}else{boxes.add(new AABB(5,2,3,11,3,11));boxes.add(new AABB(5,1,4,11,2,10));boxes.add(new AABB(5,0,5,11,1,9));boxes.add(new AABB(5,-1,6,11,0,8));}}
        var shape=Shapes.empty();float radians=(float)Math.toRadians(angleY(state)-(face==AttachFace.WALL?180:0));
        for(var box:boxes){var a=new Vec3(box.minX/16-0.5,box.minY/16,box.minZ/16-0.5).yRot(radians);var b=new Vec3(box.maxX/16-0.5,box.maxY/16,box.maxZ/16-0.5).yRot(radians);shape=Shapes.or(shape,Shapes.box(Math.min(a.x,b.x)+0.5,Math.min(a.y,b.y),Math.min(a.z,b.z)+0.5,Math.max(a.x,b.x)+0.5,Math.max(a.y,b.y),Math.max(a.z,b.z)+0.5));}return shape;
    }
}
