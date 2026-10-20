package com.simibubi.create.content.equipment.bell;

import com.simibubi.create.*;
import com.simibubi.create.foundation.utility.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public abstract class AbstractBellBlock extends BellBlock {
    protected AbstractBellBlock(Properties properties) { super(properties); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return BellShapes.bell(state.getValue(ATTACHMENT),state.getValue(FACING)); }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) { return super.canSurvive(state,level instanceof Level world?new LoadedLevelReader(world):level,pos); }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved) {
        if(!(level instanceof ServerLevel))return;boolean powered=LoadedLevelAccess.getBestNeighborSignal(level,pos)>0;if(powered==state.getValue(POWERED))return;
        LoadedLevelAccess.setBlockQuiet(level,pos,state.setValue(POWERED,powered));
        if(powered){var type=state.getValue(ATTACHMENT);var facing=state.getValue(FACING);ring(level,pos,type==BellAttachType.CEILING||type==BellAttachType.FLOOR?facing:facing.getClockWise(),null);}
    }
    @Override public boolean onHit(Level level,BlockState state,BlockHitResult hit,Player player,boolean flag) {
        return canRingFrom(state,hit.getDirection(),hit.getLocation().y-hit.getBlockPos().getY())&&ring(level,hit.getBlockPos(),hit.getDirection(),player);
    }
    public boolean canRingFrom(BlockState state,Direction hit,double height) { if(hit.getAxis().isVertical()||height>.8124)return false;var type=state.getValue(ATTACHMENT);boolean wall=type==BellAttachType.SINGLE_WALL||type==BellAttachType.DOUBLE_WALL;return wall?state.getValue(FACING).getAxis()!=hit.getAxis():state.getValue(FACING).getAxis()==hit.getAxis(); }
    protected boolean ring(Level level,BlockPos pos,Direction direction,Player player) {
        if(level.isClientSide())return true;
        if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof AbstractBellBlockEntity bell)||!bell.ring(level,pos,direction))return false;
        playSound(level,pos);if(player!=null)player.awardStat(Stats.BELL_RING);return true;
    }
    @Override public boolean attemptToRing(Level level,BlockPos pos,Direction direction) { return attemptToRing(null,level,pos,direction); }
    @Override public boolean attemptToRing(Entity entity,Level level,BlockPos pos,Direction direction) { return ring(level,pos,direction==null?LoadedLevelAccess.blockState(level,pos).getValue(FACING):direction,entity instanceof Player player?player:null); }
    public abstract void playSound(Level level,BlockPos pos);
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        if(this instanceof HauntedBellBlock)return createTickerHelper(type,AllBlockEntityTypes.HAUNTED_BELL,AbstractBellBlockEntity::tick);
        return createTickerHelper(type,AllBlockEntityTypes.PECULIAR_BELL,AbstractBellBlockEntity::tick);
    }
}
