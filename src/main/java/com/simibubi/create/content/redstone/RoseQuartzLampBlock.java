package com.simibubi.create.content.redstone;

import com.simibubi.create.content.redstone.diodes.BrassDiodeBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.redstone.Orientation;

/** Pinned cluster winner/one-tick activation, comparator distance and native POWERING light. */
public class RoseQuartzLampBlock extends Block {
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED,POWERING=BrassDiodeBlock.POWERING,ACTIVATE=BooleanProperty.create("activate");
    public RoseQuartzLampBlock(Properties properties){super(properties.lightLevel(state->state.getValue(POWERING)?15:0));registerDefaultState(defaultBlockState().setValue(POWERED,false).setValue(POWERING,false).setValue(ACTIVATE,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(POWERED,POWERING,ACTIVATE);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(POWERED,context.getLevel().hasNeighborSignal(context.getClickedPos()));}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,Orientation orientation,boolean moving){
        if(level.isClientSide())return;boolean powered=state.getValue(POWERED);if(powered==level.hasNeighborSignal(pos))return;if(powered){level.setBlock(pos,state.cycle(POWERED),Block.UPDATE_CLIENTS);return;}
        forEachInCluster(level,pos,(target,current)->{level.setBlock(target,current.setValue(POWERING,false),Block.UPDATE_CLIENTS);scheduleActivation(level,target);});
        level.setBlock(pos,state.setValue(POWERED,true).setValue(POWERING,true).setValue(ACTIVATE,true),Block.UPDATE_CLIENTS);level.updateNeighborsAt(pos,this);scheduleActivation(level,pos);
    }
    private void scheduleActivation(Level level,BlockPos pos){if(!level.getBlockTicks().hasScheduledTick(pos,this))level.scheduleTick(pos,this,1);}
    private void forEachInCluster(Level level,BlockPos source,BiConsumer<BlockPos,BlockState> callback){
        var pending=new ArrayDeque<BlockPos>();var visited=new HashSet<BlockPos>();pending.add(source);visited.add(source);
        while(!pending.isEmpty()){var pos=pending.remove();for(var side:Direction.values()){var target=pos.relative(side);if(target.distManhattan(source)>16||!visited.add(target)||!LoadedLevelAccess.isLoaded(level,target))continue;var state=LoadedLevelAccess.blockState(level,target);if(!state.is(this))continue;callback.accept(target,state);pending.add(target);}}
    }
    @Override protected boolean isSignalSource(BlockState state){return true;}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){if(side==null)return 0;var neighbor=level.getBlockState(pos.relative(side.getOpposite()));if(neighbor.is(this))return 0;if(neighbor.is(Blocks.COMPARATOR))return getDistanceToPowered(level,pos,side);return state.getValue(POWERING)?15:0;}
    private int getDistanceToPowered(BlockGetter level,BlockPos pos,Direction column){var target=pos.mutable();for(int power=15;power>0;power--){if(level instanceof Level world&&!LoadedLevelAccess.isLoaded(world,target))return 0;var state=level.getBlockState(target);if(!state.is(this))return 0;if(state.getValue(POWERING))return power;target.move(column);}return 0;}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){if(state.getValue(POWERING)||state.getValue(ACTIVATE))level.setBlock(pos,state.setValue(ACTIVATE,false).setValue(POWERING,state.getValue(ACTIVATE)),Block.UPDATE_CLIENTS);level.updateNeighborsAt(pos,this);}
    public BlockState getRotatedBlockState(BlockState state,Direction face){return state.cycle(POWERING);}
    public InteractionResult onWrenched(BlockState state,UseOnContext context){var level=context.getLevel();if(context.getPlayer()==null||!context.getPlayer().mayBuild()||!level.mayInteract(context.getPlayer(),context.getClickedPos()))return InteractionResult.PASS;if(!level.isClientSide()){level.setBlock(context.getClickedPos(),getRotatedBlockState(state,context.getClickedFace()),Block.UPDATE_ALL);forEachInCluster(level,context.getClickedPos(),(target,current)->level.updateNeighborsAt(target,this));}return InteractionResult.SUCCESS;}
}
