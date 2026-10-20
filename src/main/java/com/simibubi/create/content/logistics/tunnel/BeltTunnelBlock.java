package com.simibubi.create.content.logistics.tunnel;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public class BeltTunnelBlock extends BaseEntityBlock {
    public static final EnumProperty<Shape> SHAPE=EnumProperty.create("shape",Shape.class);
    public static final EnumProperty<Direction.Axis> HORIZONTAL_AXIS=BlockStateProperties.HORIZONTAL_AXIS;
    public enum Shape implements StringRepresentable {STRAIGHT,WINDOW,CLOSED,T_LEFT,T_RIGHT,CROSS;public String getSerializedName(){return name().toLowerCase(java.util.Locale.ROOT);}}
    public final boolean brass;
    public BeltTunnelBlock(Properties properties,boolean brass){super(properties);this.brass=brass;registerDefaultState(defaultBlockState().setValue(HORIZONTAL_AXIS,Direction.Axis.Z).setValue(SHAPE,Shape.STRAIGHT));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(SHAPE,HORIZONTAL_AXIS);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return brass?new BrassTunnelBlockEntity(pos,state):new BeltTunnelBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return brass?createTickerHelper(type,AllBlockEntityTypes.BRASS_TUNNEL,BeltTunnelBlockEntity::tick):createTickerHelper(type,AllBlockEntityTypes.ANDESITE_TUNNEL,BeltTunnelBlockEntity::tick);}
    public boolean isValidPositionForPlacement(BlockState state,BlockGetter level,BlockPos pos){var below=LoadedLevelAccess.blockState(level,pos.below());return below.getBlock() instanceof BeltBlock&&below.getValue(BeltBlock.SLOPE)==BeltSlope.HORIZONTAL;}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return isValidPositionForPlacement(state,level,pos)&&LoadedLevelAccess.blockState(level,pos.below()).getValue(BeltBlock.CASING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return getTunnelState(context.getLevel(),context.getClickedPos());}
    public static boolean hasWindow(BlockState state){return state.getValue(SHAPE)==Shape.WINDOW||state.getValue(SHAPE)==Shape.CLOSED;}
    public static boolean isStraight(BlockState state){return hasWindow(state)||state.getValue(SHAPE)==Shape.STRAIGHT;}
    public static boolean isJunction(BlockState state){return !isStraight(state);}
    public BlockState getTunnelState(BlockGetter level,BlockPos pos){
        var state=defaultBlockState();var below=LoadedLevelAccess.blockState(level,pos.below());if(below.getBlock() instanceof BeltBlock)state=state.setValue(HORIZONTAL_AXIS,below.getValue(BeltBlock.HORIZONTAL_FACING).getAxis());
        var axis=state.getValue(HORIZONTAL_AXIS);var left=Direction.get(Direction.AxisDirection.POSITIVE,axis).getClockWise();boolean l=hasValidOutput(level,pos.below(),left),r=hasValidOutput(level,pos.below(),left.getOpposite());
        if(l||r)return state.setValue(SHAPE,l&&r?Shape.CROSS:l?Shape.T_LEFT:Shape.T_RIGHT);
        return canHaveWindow(level,pos,axis)?state.setValue(SHAPE,Shape.WINDOW):state;
    }
    private static boolean hasValidOutput(BlockGetter level,BlockPos pos,Direction side){var next=LoadedLevelAccess.blockState(level,pos.relative(side));if(next.getBlock() instanceof BeltBlock)return next.getValue(BeltBlock.HORIZONTAL_FACING).getAxis()==side.getAxis();var input=BlockEntityBehaviour.get(level,pos.relative(side),DirectBeltInputBehaviour.TYPE);return input!=null&&input.canInsertFromSide(side);}
    private static boolean canHaveWindow(BlockGetter level,BlockPos pos,Direction.Axis axis){var direction=Direction.get(Direction.AxisDirection.POSITIVE,axis);var first=LoadedLevelAccess.blockState(level,pos.relative(direction));var second=LoadedLevelAccess.blockState(level,pos.relative(direction.getOpposite()));boolean f1=extended(first,direction.getOpposite()),f2=extended(second,direction);return (first.getBlock() instanceof BeltTunnelBlock||f1)&&(second.getBlock() instanceof BeltTunnelBlock||f2)&&!(f1&&f2);}
    private static boolean extended(BlockState state,Direction facing){return state.getBlock() instanceof BeltFunnelBlock&&state.getValue(BeltFunnelBlock.SHAPE)==BeltFunnelBlock.Shape.EXTENDED&&state.getValue(BeltFunnelBlock.HORIZONTAL_FACING)==facing;}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos neighborPos,BlockState neighbor,RandomSource random){if(level instanceof Level world&&!world.isClientSide())ticks.scheduleTick(pos,this,1);if(side.getAxis().isVertical())return state;var next=getTunnelState(level,pos);return next.getValue(HORIZONTAL_AXIS)==state.getValue(HORIZONTAL_AXIS)&&hasWindow(next)==hasWindow(state)?state:next;}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){if(!level.isClientSide())level.scheduleTick(pos,this,1);}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){if(!canSurvive(state,level,pos)){level.destroyBlock(pos,true);return;}var next=getTunnelState(level,pos);if(next.getValue(SHAPE)==Shape.WINDOW&&state.getValue(SHAPE)==Shape.CLOSED)next=next.setValue(SHAPE,Shape.CLOSED);if(next!=state)level.setBlockAndUpdate(pos,next);if(LoadedLevelAccess.blockEntity(level,pos) instanceof BeltTunnelBlockEntity tunnel)tunnel.updateTunnelConnections();}
    public InteractionResult onWrenched(BlockState state,UseOnContext context){if(!hasWindow(state))return InteractionResult.PASS;if(!context.getLevel().isClientSide())context.getLevel().setBlock(context.getClickedPos(),state.setValue(SHAPE,state.getValue(SHAPE)==Shape.WINDOW?Shape.CLOSED:Shape.WINDOW),Block.UPDATE_CLIENTS);return InteractionResult.SUCCESS;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return TunnelSettings.interact(level,player,hand,hit);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){var result=TunnelSettings.interact(level,player,InteractionHand.MAIN_HAND,hit);if(result.consumesAction())return result;if(!brass||!player.mayBuild()||player.isSpectator()||!level.mayInteract(player,pos)||!(LoadedLevelAccess.blockEntity(level,pos) instanceof BrassTunnelBlockEntity tunnel))return InteractionResult.PASS;var stacks=tunnel.grabAllStacksOfGroup(level.isClientSide());if(stacks.isEmpty())return InteractionResult.PASS;if(!level.isClientSide())stacks.forEach(stack->player.getInventory().placeItemBackInInventory(stack,Prediction.SERVER_ONLY));return InteractionResult.SUCCESS;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return BeltTunnelShapes.getShape(state);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(HORIZONTAL_AXIS,rotation.rotate(Direction.get(Direction.AxisDirection.POSITIVE,state.getValue(HORIZONTAL_AXIS))).getAxis());}
}
