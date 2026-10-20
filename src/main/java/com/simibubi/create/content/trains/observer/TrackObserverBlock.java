package com.simibubi.create.content.trains.observer;

import com.simibubi.create.AllBlockEntityTypes;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

/** Source detector plate and native fifteen-strength redstone output on all sides. */
public final class TrackObserverBlock extends Block implements EntityBlock {
  public static final BooleanProperty POWERED=BlockStateProperties.POWERED;
  public TrackObserverBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(POWERED,false));}
  @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(POWERED);}
  @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new TrackObserverBlockEntity(pos,state);}
  @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AllBlockEntityTypes.TRACK_OBSERVER?(world,pos,block,entity)->TrackObserverBlockEntity.tick(world,pos,block,(TrackObserverBlockEntity)entity):null;}
  @Override protected boolean isSignalSource(BlockState state){return true;}
  @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return state.getValue(POWERED)?15:0;}
  @Override protected boolean shouldRedstoneWireConnectTo(BlockState state,BlockGetter level,BlockPos pos,Direction side){return true;}
  public static boolean testFilterHit(BlockPos pos,net.minecraft.world.phys.BlockHitResult hit){return hit.getDirection()==Direction.UP&&hit.getLocation().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(pos)).distanceTo(new net.minecraft.world.phys.Vec3(.5,15.5/16d,.5))<.25;}
}
