package com.simibubi.create.content.contraptions.piston;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;
import static com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.*;

/** Original mounted-piston traversal, used by the actual common assembly frontier. */
public final class PistonAttachmentLogic {
    private static BlockState state(Level level,BlockPos pos)throws AssemblyException{if(!level.isInValidBounds(pos))return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();if(!LoadedLevelAccess.isLoaded(level,pos))throw AssemblyException.entitiesNotReady(pos);return LoadedLevelAccess.blockState(level,pos);}
    public static boolean enqueue(Level level,BlockPos pos,BlockState state,Queue<BlockPos> frontier,Set<BlockPos> visited)throws AssemblyException{
        var face=state.getValue(FACING);
        if(isPiston(state)){
            if(state.getValue(STATE)==PistonState.MOVING)return false;
            var back=pos.relative(face.getOpposite());if(!visited.contains(back)){var other=state(level,back);if(isExtensionPole(other)&&other.getValue(FACING).getAxis()==face.getAxis())frontier.add(back);}
            if(state.getValue(STATE)==PistonState.EXTENDED||isStickyPiston(state)){var front=pos.relative(face);if(!visited.contains(front))frontier.add(front);}
            return true;
        }
        if(isPistonHead(state)){
            var back=pos.relative(face.getOpposite());if(!visited.contains(back)){
                var other=state(level,back);
                if(isExtensionPole(other)&&other.getValue(FACING).getAxis()==face.getAxis()||isPiston(other)&&other.getValue(FACING)==face&&other.getValue(STATE)==PistonState.EXTENDED)frontier.add(back);
            }
            if(state.getValue(MechanicalPistonHeadBlock.TYPE)==PistonType.STICKY){var front=pos.relative(face);if(!visited.contains(front))frontier.add(front);}
            return true;
        }
        if(isExtensionPole(state))for(var side:Direction.values())if(side.getAxis()==face.getAxis()){
            var next=pos.relative(side);if(visited.contains(next))continue;var other=state(level,next);
            if((isExtensionPole(other)||isPistonHead(other))&&other.getValue(FACING).getAxis()==face.getAxis())frontier.add(next);
            if(isPiston(other)&&(other.getValue(FACING)==side||other.getValue(FACING)==side.getOpposite()&&other.getValue(STATE)==PistonState.EXTENDED))frontier.add(next);
        }return true;
    }
    private PistonAttachmentLogic(){}
}
