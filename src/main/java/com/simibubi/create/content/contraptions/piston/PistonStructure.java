package com.simibubi.create.content.contraptions.piston;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import static com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock.*;

/** Native break cleanup is preflighted as a whole; scans never request absent chunks. */
public final class PistonStructure {
    private record Cleanup(BlockPos base,List<BlockPos> parts){}
    private static BlockState state(Level level,BlockPos pos)throws AssemblyException{if(!level.isInValidBounds(pos))return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();if(!LoadedLevelAccess.isLoaded(level,pos))throw AssemblyException.entitiesNotReady(pos);return LoadedLevelAccess.blockState(level,pos);}
    private static Cleanup cleanup(Level level,BlockPos pos,BlockState state)throws AssemblyException{
        var axis=state.getValue(FACING).getAxis();var plus=Direction.get(Direction.AxisDirection.POSITIVE,axis);BlockPos base=isPiston(state)?pos:null,head=isPistonHead(state)?pos:null;
        var scans=isPiston(state)?new Direction[]{state.getValue(FACING)}:isPistonHead(state)?new Direction[]{state.getValue(FACING).getOpposite()}:new Direction[]{plus,plus.getOpposite()};
        for(var direction:scans)for(int distance=1;distance<=maxAllowedPistonPoles();distance++){
            var next=pos.relative(direction,distance);var block=state(level,next);
            if(isExtensionPole(block)&&block.getValue(FACING).getAxis()==axis)continue;
            if(base==null&&isPiston(block)&&block.getValue(FACING).getAxis()==axis)base=next;
            if(head==null&&isPistonHead(block)&&block.getValue(FACING).getAxis()==axis)head=next;
            break;
        }
        var parts=new LinkedHashSet<BlockPos>();
        if(base!=null&&head!=null&&state(level,base).getValue(FACING)==state(level,head).getValue(FACING)){
            for(var next:BlockPos.betweenClosed(base,head))if(!next.equals(base)&&!next.equals(pos))parts.add(next.immutable());
        }
        if(isPiston(state))for(int distance=1;distance<=maxAllowedPistonPoles();distance++){
            var next=pos.relative(state.getValue(FACING).getOpposite(),distance);var block=state(level,next);
            if(!isExtensionPole(block)||block.getValue(FACING).getAxis()!=axis)break;parts.add(next);
        }
        return new Cleanup(base,List.copyOf(parts));
    }
    public static boolean canBreak(Level level,BlockPos pos,BlockState state){
        if(!isPiston(state)&&!isExtensionPole(state)&&!isPistonHead(state))return true;
        try{cleanup(level,pos,state);if(LoadedLevelAccess.blockEntity(level,pos) instanceof MechanicalPistonBlockEntity piston&&!piston.canDisassembleNow())return false;return true;}
        catch(AssemblyException missing){return false;}
    }
    public static void destroyConnectedParts(Level level,BlockPos pos,BlockState state,Player player){
        if(level.isClientSide())return;
        if(LoadedLevelAccess.blockEntity(level,pos) instanceof MechanicalPistonBlockEntity piston&&!piston.disassemble())return;
        try{
            var cleanup=cleanup(level,pos,state);boolean drop=player==null||!player.getAbilities().instabuild;
            for(var part:cleanup.parts())level.destroyBlock(part,drop);
            if(cleanup.base()!=null&&!cleanup.base().equals(pos)){
                var base=state(level,cleanup.base());if(isPiston(base))level.setBlockAndUpdate(cleanup.base(),base.setValue(STATE,PistonState.RETRACTED));
                if(LoadedLevelAccess.blockEntity(level,cleanup.base()) instanceof MechanicalPistonBlockEntity piston)piston.onLengthBroken();
            }
        }catch(AssemblyException unavailable){/* Player BEFORE veto prevents an incomplete ordinary player break. */}
    }
    public static void register(){PlayerBlockBreakEvents.BEFORE.register((level,player,pos,state,be)->canBreak(level,pos,state));}
    private PistonStructure(){}
}
