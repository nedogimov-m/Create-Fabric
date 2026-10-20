package com.simibubi.create.content.contraptions.pulley;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public class PulleyBlock extends HorizontalAxisKineticBlock {
    public PulleyBlock(Properties properties){super(properties);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new PulleyBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AllBlockEntityTypes.ROPE_PULLEY?(world,pos,block,be)->PulleyBlockEntity.tick(world,pos,block,(PulleyBlockEntity)be):null;}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(LoadedLevelAccess.blockEntity(level,pos) instanceof PulleyBlockEntity device)device.sourceAdvancements.place(placer);}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return InteractionResult.TRY_WITH_EMPTY_HAND;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(!player.mayBuild()||player.isSpectator()||player.isShiftKeyDown()||!level.mayInteract(player,pos))return InteractionResult.PASS;if(!level.isClientSide()&&LoadedLevelAccess.blockEntity(level,pos) instanceof PulleyBlockEntity pulley)pulley.requestAssembly();return InteractionResult.SUCCESS;}
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moving){if(!moving&&LoadedLevelAccess.isLoaded(level,pos.below())&&LoadedLevelAccess.blockState(level,pos.below()).getBlock() instanceof RopeBlockBase)level.destroyBlock(pos.below(),true);super.affectNeighborsAfterRemoval(state,level,pos,moving);}
    private abstract static class RopeBlockBase extends Block implements SimpleWaterloggedBlock {
        RopeBlockBase(Properties properties){super(properties.pushReaction(PushReaction.IMMOVEABLE).noOcclusion());registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,false));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(BlockStateProperties.WATERLOGGED);}
        @Override protected FluidState getFluidState(BlockState state){return state.getValue(BlockStateProperties.WATERLOGGED)?Fluids.WATER.getSource(false):Fluids.EMPTY.defaultFluidState();}
        @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
        @Override protected ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state,boolean includeData){return new ItemStack(AllBlocks.ROPE_PULLEY);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,LoadedLevelAccess.blockState(context.getLevel(),context.getClickedPos()).getFluidState().getType()==Fluids.WATER);}
        @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos otherPos,BlockState other,RandomSource random){if(state.getValue(BlockStateProperties.WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return state;}
        @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moving){
            if(moving||LoadedLevelAccess.blockState(level,pos).getBlock()==this)return;
            for(var side:new Direction[]{Direction.UP,Direction.DOWN}){var adjacent=pos.relative(side);if(!LoadedLevelAccess.isLoaded(level,adjacent))continue;var other=LoadedLevelAccess.blockState(level,adjacent);if(other.getBlock() instanceof RopeBlockBase)level.destroyBlock(adjacent,true);else if(side==Direction.UP&&LoadedLevelAccess.blockEntity(level,adjacent) instanceof PulleyBlockEntity pulley)pulley.onLengthBroken();}
        }
    }
    public static final class RopeBlock extends RopeBlockBase {
        public RopeBlock(Properties properties){super(properties);}
        @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Block.box(6,0,6,10,16,10);}
    }
    public static final class MagnetBlock extends RopeBlockBase {
        public MagnetBlock(Properties properties){super(properties);}
        @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Shapes.or(Block.box(3,-3,3,13,2,13),Block.box(6,0,6,10,16,10));}
    }
    public static boolean isRope(BlockState state){return state.getBlock() instanceof RopeBlockBase;}
}
