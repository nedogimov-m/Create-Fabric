package com.simibubi.create.content.contraptions.gantry;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.kinetics.gantry.GantryShaftBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Source mechanical frontier rules for enclosing and cascaded gantries. */
public final class GantryAttachmentLogic {
    private static BlockState state(Level level,BlockPos pos)throws AssemblyException{if(!level.isInValidBounds(pos))return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();if(!LoadedLevelAccess.isLoaded(level,pos))throw AssemblyException.entitiesNotReady(pos);return LoadedLevelAccess.blockState(level,pos);}
    public static void enqueue(Level level,BlockPos pos,BlockState state,Queue<BlockPos> frontier,Set<BlockPos> visited)throws AssemblyException{
        if(state.getBlock() instanceof GantryCarriageBlock carriage){
            var front=pos.relative(state.getValue(GantryCarriageBlock.FACING));if(!visited.contains(front))frontier.add(front);
            for(var side:Direction.values())if(side.getAxis()==carriage.getRotationAxis(state)){
                var next=pos.relative(side);if(visited.contains(next))continue;var other=state(level,next);
                if(other.getBlock() instanceof GantryShaftBlock&&other.getValue(GantryShaftBlock.FACING).getAxis()==side.getAxis())frontier.add(next);
            }
        }else if(state.getBlock() instanceof GantryShaftBlock){
            var facing=state.getValue(GantryShaftBlock.FACING);
            for(var side:Direction.values()){
                var next=pos.relative(side);if(visited.contains(next))continue;var other=state(level,next);
                if(side.getAxis()==facing.getAxis()&&other.getBlock() instanceof GantryShaftBlock&&other.getValue(GantryShaftBlock.FACING)==facing||other.getBlock() instanceof GantryCarriageBlock&&other.getValue(GantryCarriageBlock.FACING)==side)frontier.add(next);
            }
        }
    }
    private GantryAttachmentLogic(){}
}
