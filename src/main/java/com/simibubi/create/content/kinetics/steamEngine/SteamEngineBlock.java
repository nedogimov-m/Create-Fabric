package com.simibubi.create.content.kinetics.steamEngine;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.tank.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class SteamEngineBlock extends FaceAttachedHorizontalDirectionalBlock implements SimpleWaterloggedBlock,EntityBlock {
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    public SteamEngineBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACE,AttachFace.FLOOR).setValue(FACING,Direction.NORTH).setValue(WATERLOGGED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACE,FACING,WATERLOGGED);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    public static Direction getFacing(BlockState state){return getConnectedDirection(state);}
    public static BlockPos getShaftPos(BlockState state,BlockPos pos){return pos.relative(getFacing(state),2);}
    public static boolean isShaftValid(BlockState state,BlockState shaft){return (shaft.is(AllBlocks.SHAFT)||shaft.is(AllBlocks.POWERED_SHAFT))&&shaft.getValue(PoweredShaftBlock.AXIS)!=getFacing(state).getAxis();}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return LoadedLevelAccess.blockState(level,pos.relative(getFacing(state).getOpposite())).getBlock() instanceof FluidTankBlock;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var state=super.getStateForPlacement(context);return state==null?null:state.setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).is(net.minecraft.tags.FluidTags.WATER));}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):Fluids.EMPTY.defaultFluidState();}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos otherPos,BlockState other,RandomSource random){if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return state;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new SteamEngineBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AllBlockEntityTypes.STEAM_ENGINE?(world,pos,block,entity)->SteamEngineBlockEntity.tick(world,pos,block,(SteamEngineBlockEntity)entity):null;}
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved){if(level.isClientSide())return;updateBoiler(level,pos,state);var shaftPos=getShaftPos(state,pos);if(level.isLoaded(shaftPos)){var shaft=LoadedLevelAccess.blockState(level,shaftPos);if(shaft.is(AllBlocks.SHAFT)&&isShaftValid(state,shaft))level.setBlockAndUpdate(shaftPos,PoweredShaftBlock.getEquivalent(shaft));}}
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){updateBoiler(level,pos,state);var shaftPos=getShaftPos(state,pos);if(level.isLoaded(shaftPos)&&LoadedLevelAccess.blockState(level,shaftPos).is(AllBlocks.POWERED_SHAFT))level.scheduleTick(shaftPos,AllBlocks.POWERED_SHAFT,1);}
    private static void updateBoiler(Level level,BlockPos pos,BlockState state){var tankPos=pos.relative(getFacing(state).getOpposite());if(level.isLoaded(tankPos)&&LoadedLevelAccess.blockEntity(level,tankPos) instanceof FluidTankBlockEntity tank)tank.updateBoilerState();}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(!player.mayBuild())return InteractionResult.PASS;if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof SteamEngineBlockEntity engine)engine.reverse();return InteractionResult.SUCCESS;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!stack.is(AllBlocks.SHAFT.asItem())||!player.mayBuild())return InteractionResult.PASS;
        var target=getShaftPos(state,pos);if(!level.isLoaded(target)||!LoadedLevelAccess.blockState(level,target).canBeReplaced())return InteractionResult.PASS;
        Direction side=java.util.Arrays.stream(Direction.orderedByNearest(player)).filter(d->d.getAxis()!=getFacing(state).getAxis()).findFirst().orElse(Direction.UP);
        var context=new UseOnContext(player,hand,hit);
        var placement=new com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem.Placement(target,side.getAxis());
        return ((BlockItem)stack.getItem()).place(com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem.assistedContext(context,placement));
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        if(state.getValue(FACE)==AttachFace.FLOOR)return Shapes.or(box(1,0,1,15,3,15),box(3,0,3,13,15,13),state.getValue(FACING).getAxis()==Direction.Axis.Z?box(1,5,4,15,13,12):box(4,5,1,12,13,15));
        if(state.getValue(FACE)==AttachFace.CEILING)return Shapes.or(box(1,13,1,15,16,15),box(3,1,3,13,16,13),state.getValue(FACING).getAxis()==Direction.Axis.Z?box(1,3,4,15,11,12):box(4,3,1,12,11,15));
        return switch(state.getValue(FACING)){case SOUTH->Shapes.or(box(1,1,0,15,15,3),box(3,3,0,13,13,15),box(1,4,5,15,12,13));case NORTH->Shapes.or(box(1,1,13,15,15,16),box(3,3,1,13,13,16),box(1,4,3,15,12,11));case EAST->Shapes.or(box(0,1,1,3,15,15),box(0,3,3,15,13,13),box(5,4,1,13,12,15));case WEST->Shapes.or(box(13,1,1,16,15,15),box(1,3,3,16,13,13),box(3,4,1,11,12,15));default->Shapes.block();};
    }
}
