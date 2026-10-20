package com.simibubi.create.content.decoration.placard;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Concrete source placard item display and exact component/filter confirmation; no fake display-link behaviour. */
public final class PlacardBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock,SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED,POWERED=BlockStateProperties.POWERED;
    public PlacardBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(FACE,AttachFace.WALL).setValue(FACING,Direction.NORTH).setValue(WATERLOGGED,false).setValue(POWERED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACE,FACING,WATERLOGGED,POWERED);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new PlacardBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AllBlockEntityTypes.PLACARD?(world,pos,block,entity)->PlacardBlockEntity.tick(world,pos,block,(PlacardBlockEntity)entity):null;}
    public static Direction connectedDirection(BlockState state){return getConnectedDirection(state);}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return canAttachLenient(level,pos,getConnectedDirection(state).getOpposite());}
    public static boolean canAttachLenient(LevelReader level,BlockPos pos,Direction direction){var support=pos.relative(direction);return !LoadedLevelAccess.blockState(level,support).getCollisionShape(level,support).isEmpty();}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var state=super.getStateForPlacement(context);if(state==null)return null;if(state.getValue(FACE)==AttachFace.FLOOR)state=state.setValue(FACING,state.getValue(FACING).getOpposite());return state.setValue(WATERLOGGED,LoadedLevelAccess.blockState(context.getLevel(),context.getClickedPos()).getFluidState().getType()==Fluids.WATER);}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos next,BlockState neighbor,RandomSource random){if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));if(level instanceof Level world&&!LoadedLevelAccess.isLoaded(world,next))return state;return super.updateShape(state,level,ticks,pos,side,next,neighbor,random);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return switch(connectedDirection(state)){case UP->box(2,0,2,14,3,14);case DOWN->box(2,13,2,14,16,14);case SOUTH->box(2,2,0,14,14,3);case NORTH->box(2,2,13,14,14,16);case EAST->box(0,2,2,3,14,14);case WEST->box(13,2,2,16,14,14);};}
    @Override protected boolean isSignalSource(BlockState state){return true;}
    @Override protected int ownSignal(BlockState state,BlockGetter level,BlockPos pos){return state.getValue(POWERED)?15:0;}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return state.getValue(POWERED)?15:0;}
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return state.getValue(POWERED)&&getConnectedDirection(state)==side?15:0;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return interact(stack,state,level,pos,player);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return interact(ItemStack.EMPTY,state,level,pos,player);}
    private static InteractionResult interact(ItemStack inHand,BlockState state,Level level,BlockPos pos,Player player){
        if(player.isShiftKeyDown()||player.isSpectator()||!level.mayInteract(player,pos))return InteractionResult.PASS;if(level.isClientSide())return InteractionResult.SUCCESS;
        if(!(LoadedLevelAccess.blockEntity(level,pos)instanceof PlacardBlockEntity entity))return InteractionResult.PASS;var held=entity.getHeldItem();
        if(!player.mayBuild()||inHand.isEmpty()||!held.isEmpty()){
            if(held.isEmpty()||inHand.isEmpty()||state.getValue(POWERED))return InteractionResult.FAIL;
            var filter=FilterItemStack.of(held);boolean matches=filter.isFilterItem()?filter.test(level,inHand):ItemStack.isSameItemSameComponents(held,inHand);
            level.playSound(null,pos,matches?AllSoundEvents.CONFIRM:AllSoundEvents.DENY,SoundSource.BLOCKS,1,1);if(!matches)return InteractionResult.SUCCESS;
            if(!level.setBlock(pos,state.setValue(POWERED,true),Block.UPDATE_ALL))return InteractionResult.FAIL;updateNeighbours(state,level,pos);entity.startPulse();return InteractionResult.SUCCESS;
        }
        level.playSound(null,pos,SoundEvents.ITEM_FRAME_ADD_ITEM,SoundSource.BLOCKS,1,1);entity.setHeldItem(inHand.copyWithCount(1));if(!player.isCreative())inHand.shrink(1);return InteractionResult.SUCCESS;
    }
    @Override protected void attack(BlockState state,Level level,BlockPos pos,Player player){
        if(level.isClientSide()||player.isSpectator()||!level.mayInteract(player,pos)||!(LoadedLevelAccess.blockEntity(level,pos)instanceof PlacardBlockEntity entity)||entity.getHeldItem().isEmpty())return;
        player.getInventory().placeItemBackInInventory(entity.getHeldItem(),Prediction.SERVER_ONLY);level.playSound(null,pos,SoundEvents.ITEM_FRAME_REMOVE_ITEM,SoundSource.BLOCKS,1,1);entity.setHeldItem(ItemStack.EMPTY);
    }
    public static void updateNeighbours(BlockState state,Level level,BlockPos pos){notifyAt(state,level,pos);notifyAt(state,level,pos.relative(getConnectedDirection(state).getOpposite()));}
    private static void notifyAt(BlockState state,Level level,BlockPos pos){for(var side:Direction.values()){var next=pos.relative(side);if(LoadedLevelAccess.isLoaded(level,next))level.neighborChanged(LoadedLevelAccess.blockState(level,next),next,state.getBlock(),null,false);}}
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){if(!moved&&state.getValue(POWERED))updateNeighbours(state,level,pos);}
    public InteractionResult onWrenched(BlockState state,UseOnContext context){
        var next=context.getClickedFace().getAxis()==Direction.Axis.Y?state.setValue(FACING,state.getValue(FACING).getClockWise()):state;var level=context.getLevel();var pos=context.getClickedPos();if(!next.canSurvive(level,pos))return InteractionResult.PASS;
        if(!level.isClientSide()&&next!=state&&level.setBlock(pos,Block.updateFromNeighbourShapes(next,level,pos),Block.UPDATE_ALL)){updateNeighbours(state,level,pos);updateNeighbours(next,level,pos);level.playSound(null,pos,AllSoundEvents.WRENCH_ROTATE,SoundSource.BLOCKS,1,1);}return InteractionResult.SUCCESS;
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
}
