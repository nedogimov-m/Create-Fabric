package com.simibubi.create.content.fluids.pipes;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

public class FluidPipeBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final Map<Direction,BooleanProperty> PROPERTY_BY_DIRECTION=Map.of(Direction.NORTH,BlockStateProperties.NORTH,Direction.SOUTH,BlockStateProperties.SOUTH,Direction.EAST,BlockStateProperties.EAST,Direction.WEST,BlockStateProperties.WEST,Direction.UP,BlockStateProperties.UP,Direction.DOWN,BlockStateProperties.DOWN);
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    public FluidPipeBlock(Properties properties){super(properties);var state=stateDefinition.any().setValue(WATERLOGGED,false);for(var p:PROPERTY_BY_DIRECTION.values())state=state.setValue(p,false);registerDefaultState(state);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof FluidPipeBlockEntity device)device.sourceAdvancements.place(placer);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(WATERLOGGED);PROPERTY_BY_DIRECTION.values().forEach(builder::add);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new FluidPipeBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.FLUID_PIPE,FluidPipeBlockEntity::tick);}
    public static boolean isPipe(BlockState state){return state.getBlock() instanceof FluidPipeBlock&&!(state.getBlock() instanceof EncasedPipeBlock);}
    public static boolean isOpenAt(BlockState state,Direction side){return state.getValue(PROPERTY_BY_DIRECTION.get(side));}
    public BlockState getAxisState(Direction.Axis axis){var state=defaultBlockState();for(var side:Direction.values())state=state.setValue(PROPERTY_BY_DIRECTION.get(side),side.getAxis()==axis);return state;}
    public static boolean canConnectTo(LevelReader level,BlockPos pos,BlockState state,Direction side){
        if(level instanceof Level world&&!world.isLoaded(pos))return false;
        var bracket=com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(level,pos,com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour.TYPE);
        if(bracket!=null&&bracket.isBracketPresent())return FluidPropagator.getStraightPipeAxis(state)==side.getAxis();
        if(FluidPropagator.hasFluidCapability(level,pos,side.getOpposite())||VanillaFluidTargets.canProvideFluidWithoutCapability(state)||isPipe(state))return true;
        var transport=FluidPropagator.getPipe(level,pos);return transport!=null&&transport.canHaveFlowToward(state,side.getOpposite());
    }
    public BlockState updateBlockState(BlockState state,Direction preferred,Direction ignore,LevelReader level,BlockPos pos){
        var bracket=com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(level,pos,com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour.TYPE);
        if(bracket!=null&&bracket.isBracketPresent())return state;
        var previous=state;int oldCount=(int)PROPERTY_BY_DIRECTION.values().stream().filter(previous::getValue).count();
        for(var side:Direction.values())if(side!=ignore)state=state.setValue(PROPERTY_BY_DIRECTION.get(side),canConnectTo(level,pos.relative(side),level instanceof Level world&&!world.isLoaded(pos.relative(side))?Blocks.AIR.defaultBlockState():FluidPropagator.stateAt(level,pos.relative(side)),side));
        Direction only=null;for(var side:Direction.values())if(isOpenAt(state,side)){if(only!=null)return state;only=side;}
        if(only!=null)return state.setValue(PROPERTY_BY_DIRECTION.get(only.getOpposite()),true);
        if(oldCount==2)return previous;
        return state.setValue(PROPERTY_BY_DIRECTION.get(preferred),true).setValue(PROPERTY_BY_DIRECTION.get(preferred.getOpposite()),true);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return updateBlockState(defaultBlockState(),context.getNearestLookingDirection(),null,context.getLevel(),context.getClickedPos()).setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).is(net.minecraft.tags.FluidTags.WATER));}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos neighborPos,BlockState neighbor,RandomSource random){if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return updateBlockState(state,side,side.getOpposite(),level,pos);}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):Fluids.EMPTY.defaultFluidState();}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){if(level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe)pipe.updateNetwork();}
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){FluidPropagator.propagateChangedPipe(level,pos,state);for(var side:Direction.values()){var other=FluidPropagator.getPipe(level,pos.relative(side));if(other!=null)FluidPropagator.propagateChangedPipe(level,pos.relative(side),other.blockEntity.getBlockState());}}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){var shape=box(4,4,4,12,12,12);for(var side:Direction.values())if(isOpenAt(state,side))shape=Shapes.or(shape,box(side==Direction.WEST?0:4,side==Direction.DOWN?0:4,side==Direction.NORTH?0:4,side==Direction.EAST?16:12,side==Direction.UP?16:12,side==Direction.SOUTH?16:12));return shape;}
    @Override protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){
        if(!stack.is(AllBlocks.COPPER_CASING.asItem())||this instanceof EncasedPipeBlock||!player.mayBuild())return net.minecraft.world.InteractionResult.PASS;
        if(!level.isClientSide()){
            var flow=FluidPropagator.getPipe(level,pos);var data=flow==null?null:flow.interfaces;
            var next=AllBlocks.ENCASED_FLUID_PIPE.defaultBlockState();for(var side:Direction.values())next=next.setValue(PROPERTY_BY_DIRECTION.get(side),isOpenAt(state,side));
            level.setBlockAndUpdate(pos,next.setValue(WATERLOGGED,state.getValue(WATERLOGGED)));var after=FluidPropagator.getPipe(level,pos);if(after!=null&&data!=null){after.interfaces=data;after.wipePressure();}
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
    @Override protected net.minecraft.world.item.ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state,boolean includeData){return new net.minecraft.world.item.ItemStack(AllBlocks.FLUID_PIPE);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){var next=state;for(var side:Direction.values())next=next.setValue(PROPERTY_BY_DIRECTION.get(rotation.rotate(side)),isOpenAt(state,side));return next;}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){var next=state;for(var side:Direction.values())next=next.setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(side)),isOpenAt(state,side));return next;}
}
