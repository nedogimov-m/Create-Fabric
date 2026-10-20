package com.simibubi.create.content.redstone.deskBell;

import com.simibubi.create.*;
import com.simibubi.create.content.equipment.bell.BellShapes;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class DeskBellBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.FACING;
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED,WATERLOGGED=BlockStateProperties.WATERLOGGED;
    public DeskBellBlock(Properties properties) { super(properties);registerDefaultState(defaultBlockState().setValue(FACING,Direction.UP).setValue(POWERED,false).setValue(WATERLOGGED,false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,POWERED,WATERLOGGED); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING,context.getClickedFace()).setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER)); }
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos otherPos,BlockState other,RandomSource random) { if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return state; }
    @Override protected FluidState getFluidState(BlockState state) { return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state); }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return BellShapes.desk(state.getValue(FACING)); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new DeskBellBlockEntity(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) { return createTickerHelper(type,AllBlockEntityTypes.DESK_BELL,DeskBellBlockEntity::tick); }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) { return stack.is(AllItems.WRENCH)?InteractionResult.PASS:useWithoutItem(state,level,pos,player,hit); }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        playSound(player,level,pos);if(!level.isClientSide()){LoadedLevelAccess.setBlockQuiet(level,pos,state.setValue(POWERED,true));if(LoadedLevelAccess.blockEntity(level,pos) instanceof DeskBellBlockEntity bell)bell.ding();}return InteractionResult.SUCCESS;
    }
    public void playSound(Player player,Level level,BlockPos pos) { level.playSound(player,pos,AllSoundEvents.DESK_BELL_USE,SoundSource.BLOCKS,1,1); }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moved) { if(!level.isClientSide()&&(!previous.is(this)||previous.getValue(POWERED)!=state.getValue(POWERED)||previous.getValue(FACING)!=state.getValue(FACING)))updateNeighbours(state,level,pos); }
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved) { if(!moved&&state.getValue(POWERED))updateNeighbours(state,level,pos); }
    @Override protected boolean isSignalSource(BlockState state) { return true; }
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side) { return state.getValue(POWERED)?15:0; }
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side) { return state.getValue(POWERED)&&state.getValue(FACING)==side?15:0; }
    public void unPress(BlockState state,Level level,BlockPos pos) { LoadedLevelAccess.setBlockQuiet(level,pos,state.setValue(POWERED,false)); }
    private void updateNeighbours(BlockState state,Level level,BlockPos pos) { notify(level,pos);notify(level,pos.relative(state.getValue(FACING).getOpposite())); }
    private void notify(Level level,BlockPos pos) { for(var side:Direction.values()){var next=pos.relative(side);if(LoadedLevelAccess.isLoaded(level,next))level.neighborChanged(LoadedLevelAccess.blockState(level,next),next,this,null,false);} }
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type) { return false; }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) { return state.setValue(FACING,rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
}
