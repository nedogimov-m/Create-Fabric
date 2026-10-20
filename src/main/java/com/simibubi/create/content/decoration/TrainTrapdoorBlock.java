package com.simibubi.create.content.decoration;

import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import net.minecraft.core.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

public final class TrainTrapdoorBlock extends TrapDoorBlock {
    public TrainTrapdoorBlock(BlockSetType type,Properties properties){super(type,properties);}
    public static TrainTrapdoorBlock metal(Properties properties){return new TrainTrapdoorBlock(SlidingDoorBlock.TRAIN_SET_TYPE,properties);}
    public static TrainTrapdoorBlock glass(Properties properties){return new TrainTrapdoorBlock(SlidingDoorBlock.GLASS_SET_TYPE,properties);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!player.mayBuild()||player.isSpectator()||!level.mayInteract(player,pos))return InteractionResult.PASS;
        state=state.cycle(OPEN);level.setBlock(pos,state,UPDATE_CLIENTS);
        if(state.getValue(WATERLOGGED))level.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));
        playSound(player,level,pos,state.getValue(OPEN));return InteractionResult.SUCCESS;
    }
    @Override protected boolean skipRendering(BlockState state,BlockState other,Direction face){return other.is(this)&&isConnected(state,other,face);}
    public static boolean isConnected(BlockState state,BlockState other,Direction face){
        state=state.setValue(WATERLOGGED,false).setValue(POWERED,false);other=other.setValue(WATERLOGGED,false).setValue(POWERED,false);
        boolean open=state.getValue(OPEN);Half half=state.getValue(HALF);Direction facing=state.getValue(FACING);
        if(open!=other.getValue(OPEN))return false;
        if(!open&&half==other.getValue(HALF))return face.getAxis()!=Direction.Axis.Y;
        if(!open&&half!=other.getValue(HALF)&&face.getAxis()==Direction.Axis.Y)return true;
        if(open&&facing.getOpposite()==other.getValue(FACING)&&face.getAxis()==facing.getAxis())return true;
        if((open?state.setValue(HALF,Half.TOP):state)!=(open?other.setValue(HALF,Half.TOP):other))return false;
        return face.getAxis()!=facing.getAxis();
    }
}
