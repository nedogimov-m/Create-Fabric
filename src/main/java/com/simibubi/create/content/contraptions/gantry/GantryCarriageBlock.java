package com.simibubi.create.content.contraptions.gantry;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.gantry.GantryShaftBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

public class GantryCarriageBlock extends DirectionalAxisKineticBlock {
    public GantryCarriageBlock(Properties p){super(p.strength(1.5f,6).sound(SoundType.STONE).noOcclusion());registerDefaultState(defaultBlockState().setValue(FACING,Direction.UP).setValue(AXIS_ALONG_FIRST_COORDINATE,false));}
    @Override protected Direction getFacingForPlacement(BlockPlaceContext context){return context.getClickedFace();}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var state=super.getStateForPlacement(context);return cycleAxisIfNecessary(state,LoadedLevelAccess.blockState(context.getLevel(),context.getClickedPos().relative(state.getValue(FACING).getOpposite())));}
    public static Direction.Axis getValidGantryShaftAxis(BlockState state){var carriage=(GantryCarriageBlock)state.getBlock();for(var axis:Direction.Axis.values())if(axis!=carriage.getRotationAxis(state)&&axis!=state.getValue(FACING).getAxis())return axis;throw new IllegalArgumentException("Carriage has no perpendicular rack axis");}
    public static Direction.Axis getValidGantryPinionAxis(BlockState state,Direction.Axis shaftAxis){for(var axis:Direction.Axis.values())if(axis!=shaftAxis&&axis!=state.getValue(FACING).getAxis())return axis;throw new IllegalArgumentException("Carriage has no perpendicular pinion axis");}
    private BlockState cycleAxisIfNecessary(BlockState state,BlockState shaft){return shaft.getBlock() instanceof GantryShaftBlock&&shaft.getValue(GantryShaftBlock.FACING).getAxis()!=state.getValue(FACING).getAxis()&&getValidGantryShaftAxis(state)!=shaft.getValue(GantryShaftBlock.FACING).getAxis()?state.cycle(AXIS_ALONG_FIRST_COORDINATE):state;}
    @Override protected boolean canSurvive(BlockState state,LevelReader reader,BlockPos pos){var behind=pos.relative(state.getValue(FACING).getOpposite());if(reader instanceof Level level&&!LoadedLevelAccess.isLoaded(level,behind))return true;var shaft=LoadedLevelAccess.blockState(reader,behind);return shaft.getBlock() instanceof GantryShaftBlock&&shaft.getValue(GantryShaftBlock.FACING).getAxis()!=state.getValue(FACING).getAxis();}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos otherPos,BlockState other,RandomSource random){return side==state.getValue(FACING).getOpposite()?cycleAxisIfNecessary(state,other):state;}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){super.neighborChanged(state,level,pos,neighbor,orientation,moved);if(!level.isClientSide()&&!canSurvive(state,level,pos))level.destroyBlock(pos,true);}
    @Override public void updateIndirectNeighbourShapes(BlockState state,LevelAccessor accessor,BlockPos pos,int flags,int recursion){super.updateIndirectNeighbourShapes(state,accessor,pos,flags,recursion);if(accessor instanceof Level level&&!level.isClientSide()&&LoadedLevelAccess.blockEntity(level,pos) instanceof GantryCarriageBlockEntity carriage)carriage.checkValidGantryShaft();}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return InteractionResult.TRY_WITH_EMPTY_HAND;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(!player.mayBuild()||player.isSpectator()||player.isShiftKeyDown()||!player.getMainHandItem().isEmpty()||!level.mayInteract(player,pos))return InteractionResult.PASS;if(!level.isClientSide()&&LoadedLevelAccess.blockEntity(level,pos) instanceof GantryCarriageBlockEntity carriage)carriage.checkValidGantryShaft();return InteractionResult.SUCCESS;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new GantryCarriageBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.GANTRY_PINION,GantryCarriageBlockEntity::tick);}
}
