package com.simibubi.create.content.logistics.packagePort.postbox;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ScheduledTickAccess;

/** All16 source colors, native horizontal placement/waterlogging, comparator and inventory opening. */
public final class PostboxBlock extends HorizontalDirectionalBlock implements EntityBlock,SimpleWaterloggedBlock {
  public static final EnumProperty<Direction> FACING=HorizontalDirectionalBlock.FACING;
  public static final BooleanProperty OPEN=BlockStateProperties.OPEN,WATERLOGGED=BlockStateProperties.WATERLOGGED;
  private final DyeColor color;
  public PostboxBlock(Properties properties,DyeColor color){super(properties.mapColor(color).noOcclusion());this.color=color;registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH).setValue(OPEN,false).setValue(WATERLOGGED,false));}
  public DyeColor getColor(){return color;}
  @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,OPEN,WATERLOGGED);}
  @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite()).setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).getType()==Fluids.WATER);}
  @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new PostboxBlockEntity(pos,state);}
  @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AllBlockEntityTypes.PACKAGE_POSTBOX?(world,pos,block,entity)->((PostboxBlockEntity)entity).tick():null;}
  @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos neighbour,BlockState neighbourState,RandomSource random){if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return super.updateShape(state,level,ticks,pos,side,neighbour,neighbourState,random);}
  @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
  @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return state.getValue(FACING).getAxis()==Direction.Axis.X?box(0,0,2,16,14,14):box(2,0,0,14,14,16);}
  @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
  @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
  @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos,Direction side){return LoadedLevelAccess.blockEntity(level,pos) instanceof PostboxBlockEntity box?box.getComparatorOutput():0;}
  @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return player.mayBuild()&&!player.isSpectator()&&level.mayInteract(player,pos)&&LoadedLevelAccess.blockEntity(level,pos) instanceof PostboxBlockEntity box&&box.use(player)?InteractionResult.SUCCESS:InteractionResult.PASS;}
  @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide()&&placer instanceof Player player&&LoadedLevelAccess.blockEntity(level,pos) instanceof PostboxBlockEntity box)PostboxPlacement.placed(player,box);}
}
