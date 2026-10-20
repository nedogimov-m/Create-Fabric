package com.simibubi.create.content.redstone.diodes;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;

/** Native diode scheduling/support with loaded-only input reads and output notifications. */
public abstract class AbstractDiodeBlock extends DiodeBlock {
    protected AbstractDiodeBlock(Properties properties){super(properties);}
    @Override protected boolean isSignalSource(BlockState state){return true;}
    @Override protected int ownSignal(BlockState state,BlockGetter level,BlockPos pos){return getOutputSignal(level,pos,state);}
    @Override protected boolean shouldRedstoneWireConnectTo(BlockState state,BlockGetter level,BlockPos pos,Direction side){return side!=null&&side.getAxis()==state.getValue(FACING).getAxis();}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return side==state.getValue(FACING)?getOutputSignal(level,pos,state):0;}
    @Override protected int getInputSignal(Level level,BlockPos pos,BlockState state){
        var direction=state.getValue(FACING);var input=pos.relative(direction);var view=new DiodeSignalAccess(level);int signal=view.getSignal(input,direction);
        var neighbor=view.getBlockState(input);return Math.max(signal,neighbor.is(Blocks.REDSTONE_WIRE)?neighbor.getValue(RedstoneWireBlock.POWER):0);
    }
    @Override public boolean shouldPrioritize(BlockGetter level,BlockPos pos,BlockState state){
        var output=state.getValue(FACING).getOpposite();var neighbor=LoadedLevelAccess.blockState(level,pos.relative(output));return isDiode(neighbor)&&neighbor.getValue(FACING)!=output;
    }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return canSurviveOn(level,pos.below(),LoadedLevelAccess.blockState(level,pos.below()));}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){
        if(!(level instanceof ServerLevel)||!LoadedLevelAccess.blockState(level,pos).is(this))return;
        if(state.canSurvive(level,pos)){checkTickOnNeighbor(level,pos,state);return;}
        dropResources(state,level,pos,LoadedLevelAccess.blockEntity(level,pos));level.removeBlock(pos,false);notifyAround(level,pos);
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,net.minecraft.util.RandomSource random){
        boolean powered=state.getValue(POWERED),input=shouldTurnOn(level,pos,state);
        if(powered&&!input)writeState(level,pos,state.setValue(POWERED,false));
        else if(!powered){writeState(level,pos,state.setValue(POWERED,true));if(!input)level.scheduleTick(pos,this,getDelay(state),net.minecraft.world.ticks.TickPriority.VERY_HIGH);}
    }
    protected final boolean writeState(Level level,BlockPos pos,BlockState state){
        if(!LoadedLevelAccess.setBlockQuiet(level,pos,state))return false;updateNeighborsInFront(level,pos,state);return true;
    }
    @Override protected void updateNeighborsInFront(Level level,BlockPos pos,BlockState state){
        var output=pos.relative(state.getValue(FACING).getOpposite());
        if(LoadedLevelAccess.isLoaded(level,output))level.neighborChanged(LoadedLevelAccess.blockState(level,output),output,this,null,false);
        for(var side:Direction.values())if(side!=state.getValue(FACING)){var next=output.relative(side);if(LoadedLevelAccess.isLoaded(level,next))level.neighborChanged(LoadedLevelAccess.blockState(level,next),next,this,null,false);}
    }
    private void notifyAround(Level level,BlockPos pos){for(var side:Direction.values()){var next=pos.relative(side);if(LoadedLevelAccess.isLoaded(level,next))level.neighborChanged(LoadedLevelAccess.blockState(level,next),next,this,null,false);}}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    public InteractionResult onWrenched(BlockState state,UseOnContext context){
        if(context.getClickedFace().getAxis()!=Direction.Axis.Y)return InteractionResult.SUCCESS;
        var level=context.getLevel();var pos=context.getClickedPos();var rotated=state.rotate(Rotation.CLOCKWISE_90);
        if(!rotated.canSurvive(level,pos))return InteractionResult.PASS;
        if(level instanceof ServerLevel&&writeState(level,pos,rotated)){
            // The old output must also see the changed facing, including its strong-power neighbors.
            updateNeighborsInFront(level,pos,state);
            checkTickOnNeighbor(level,pos,rotated);
            level.playSound(null,pos,com.simibubi.create.AllSoundEvents.WRENCH_ROTATE,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
        }
        return InteractionResult.SUCCESS;
    }
}
