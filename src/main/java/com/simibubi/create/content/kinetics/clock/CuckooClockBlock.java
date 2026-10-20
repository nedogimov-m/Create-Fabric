package com.simibubi.create.content.kinetics.clock;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
/** Original rear shaft, source19-high silhouette and two genuine device variants. */
public class CuckooClockBlock extends HorizontalKineticBlock {
 private final boolean mysterious;
 public static CuckooClockBlock regular(Properties properties){return new CuckooClockBlock(false,properties);}
 public static CuckooClockBlock mysterious(Properties properties){return new CuckooClockBlock(true,properties);}
 protected CuckooClockBlock(boolean mysterious,Properties properties){super(properties);this.mysterious=mysterious;}
 @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return box(1,0,1,15,19,15);}
 @Override public BlockState getStateForPlacement(BlockPlaceContext context){var preferred=getPreferredHorizontalFacing(context);return defaultBlockState().setValue(HORIZONTAL_FACING,(preferred!=null?preferred:context.getHorizontalDirection()).getOpposite());}
 @Override public boolean hasShaftTowards(LevelReader level,BlockPos pos,BlockState state,Direction face){return face==state.getValue(HORIZONTAL_FACING).getOpposite();}
 public static boolean containsSurprise(BlockState state){return state.getBlock() instanceof CuckooClockBlock clock&&clock.mysterious;}
 @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(placer instanceof net.minecraft.server.level.ServerPlayer player&&level.getBlockEntity(pos) instanceof CuckooClockBlockEntity clock)clock.setAdvancementOwner(player);}
 @Override public Direction.Axis getRotationAxis(BlockState state){return state.getValue(HORIZONTAL_FACING).getAxis();}
 @Override protected boolean isPathfindable(BlockState state,net.minecraft.world.level.pathfinder.PathComputationType type){return false;}
 @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new CuckooClockBlockEntity(pos,state);}
 @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.CUCKOO_CLOCK,CuckooClockBlockEntity::tick);}
}
