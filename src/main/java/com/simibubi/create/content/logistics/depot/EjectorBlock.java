package com.simibubi.create.content.logistics.depot;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class EjectorBlock extends HorizontalKineticBlock implements SimpleWaterloggedBlock {
    public EjectorBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(BlockStateProperties.WATERLOGGED);}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(BlockStateProperties.WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return super.getStateForPlacement(context).setValue(BlockStateProperties.WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).getType()==Fluids.WATER);}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction direction,BlockPos neighborPos,BlockState neighbor,RandomSource random){if(state.getValue(BlockStateProperties.WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return super.updateShape(state,level,ticks,pos,direction,neighborPos,neighbor,random);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return box(0,0,0,16,13,16);}
    @Override public Direction.Axis getRotationAxis(BlockState state){return state.getValue(HORIZONTAL_FACING).getClockWise().getAxis();}
    @Override public boolean hasShaftTowards(LevelReader level,BlockPos pos,BlockState state,Direction side){return getRotationAxis(state)==side.getAxis();}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new EjectorBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.WEIGHTED_EJECTOR,EjectorBlockEntity::tick);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,Orientation orientation,boolean moved){super.neighborChanged(state,level,pos,block,orientation,moved);if(LoadedLevelAccess.blockEntity(level,pos) instanceof EjectorBlockEntity ejector)ejector.updateSignal();}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(placer instanceof ServerPlayer player&&LoadedLevelAccess.blockEntity(level,pos) instanceof EjectorBlockEntity ejector)ejector.beginPlacement(player);}
    @Override public void fallOn(Level level,BlockState state,BlockPos pos,Entity entity,double distance){
        if(LoadedLevelAccess.blockEntity(level,pos) instanceof EjectorBlockEntity&&!entity.isSuppressingBounce()){entity.causeFallDamage(distance,1,level.damageSources().fall());return;}
        super.fallOn(level,state,pos,entity,distance);
    }
    @Override public void stepOn(Level level,BlockPos pos,BlockState block,Entity entity){
        super.stepOn(level,pos,block,entity);
        if(!entity.isAlive()||entity.isSuppressingBounce())return;
        if(entity instanceof ItemEntity||entity instanceof PackageEntity){SharedDepotBlockMethods.onLanded(level,entity);return;}
        if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof EjectorBlockEntity ejector)||ejector.getState()==EjectorBlockEntity.State.RETRACTING||ejector.isPowered()||ejector.getLauncher().getHorizontalDistance()==0)return;
        if(entity.onGround()){
            entity.setOnGround(false);var center=Vec3.atCenterOf(pos).add(0,7/16f,0);var position=entity.position();
            entity.setDeltaMovement(0,-.125,0);
            if(center.distanceTo(position)>4/16f){var half=center.add(position).scale(.5f);entity.setPos(half.x,half.y,half.z);return;}
        }
        if(level.isClientSide())EjectorNetworking.requestClientTrigger(pos);else{ejector.activate();ejector.setChanged();ejector.sync();}
    }
    public static boolean settingsHit(BlockState state,BlockPos pos,BlockHitResult hit,EjectorBlockEntity ejector){
        Direction face=hit.getDirection();boolean top=face==Direction.UP;
        if(!top&&face.getAxis()!=state.getValue(HORIZONTAL_FACING).getAxis()||top&&ejector.getState()==EjectorBlockEntity.State.CHARGED)return false;
        var relative=hit.getLocation().subtract(pos.getX(),pos.getY(),pos.getZ());
        if(top){var offset=com.simibubi.create.foundation.utility.SourceVectorMath.rotate(new Vec3(0,0,-5/16f),net.createmod.catnip.math.AngleHelper.horizontalAngle(state.getValue(HORIZONTAL_FACING)),Direction.Axis.Y);return relative.distanceTo(new Vec3(.5,10.5/16f,.5).add(offset))<3/16f;}
        double horizontal=face.getAxis()==Direction.Axis.X?relative.z:relative.x;
        return Math.abs(relative.y-6/16f)<3/16f&&Math.abs(horizontal-.5)<3/16f;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(stack.is(AllItems.WRENCH))return InteractionResult.PASS;
        if(!player.isShiftKeyDown()&&LoadedLevelAccess.blockEntity(level,pos) instanceof EjectorBlockEntity ejector&&settingsHit(state,pos,hit,ejector)){
            if(player instanceof ServerPlayer actual)EjectorNetworking.openSettings(actual,ejector);return InteractionResult.SUCCESS;
        }
        return SharedDepotBlockMethods.onUse(stack,state,level,pos,player,hand,hit);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return useItemOn(ItemStack.EMPTY,state,level,pos,player,InteractionHand.MAIN_HAND,hit);}
    @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos,Direction side){return SharedDepotBlockMethods.getComparatorInputOverride(state,level,pos);}
    @Override protected boolean isPathfindable(BlockState state,net.minecraft.world.level.pathfinder.PathComputationType type){return false;}
}
