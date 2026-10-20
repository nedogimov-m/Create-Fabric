package com.simibubi.create.content.decoration.girder;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Source top/bottom SINGLE/PAIR action selection; inactive horizontal action stays absent. */
public final class GirderWrenchBehavior {
    public enum Action {SINGLE,PAIR}
    public record Target(Direction direction,Action action){}
    public static List<Target> getValidDirections(BlockGetter level,BlockPos pos){
        var state=LoadedLevelAccess.blockState(level,pos);if(!(state.getBlock()instanceof GirderBlock)||state.getValue(GirderBlock.X)==state.getValue(GirderBlock.Z))return List.of();var result=new ArrayList<Target>();
        for(var side:new Direction[]{Direction.DOWN,Direction.UP}){var next=pos.relative(side);if(!GirderBlock.available(level,next))continue;var other=LoadedLevelAccess.blockState(level,next);if(!(other.getBlock()instanceof GirderBlock))result.add(new Target(side,Action.SINGLE));else if(other.getValue(GirderBlock.X)!=other.getValue(GirderBlock.Z))result.add(new Target(side,Action.PAIR));}
        return List.copyOf(result);
    }
    public static Target target(BlockHitResult hit,BlockGetter level,BlockPos pos){return getValidDirections(level,pos).stream().min(Comparator.comparingDouble(target->Vec3.atCenterOf(pos).relative(target.direction(),.5).distanceToSqr(hit.getLocation()))).orElse(null);}
    public static boolean handleClick(Level level,BlockPos pos,BlockState state,BlockHitResult hit){
        var target=target(hit,level,pos);if(target==null)return false;if(level.isClientSide())return true;if(LoadedLevelAccess.blockState(level,pos)!=state)return false;
        var property=target.direction()==Direction.UP?GirderBlock.TOP:GirderBlock.BOTTOM;
        if(!LoadedLevelAccess.setBlockQuiet(level,pos,postProcess(state.cycle(property))))return false;
        if(target.action()==Action.PAIR){var next=pos.relative(target.direction());var other=LoadedLevelAccess.blockState(level,next);if(other.getBlock()instanceof GirderBlock)LoadedLevelAccess.setBlockQuiet(level,next,postProcess(other.cycle(target.direction()==Direction.UP?GirderBlock.BOTTOM:GirderBlock.TOP)));}return true;
    }
    private static BlockState postProcess(BlockState state){return state.getValue(GirderBlock.TOP)&&state.getValue(GirderBlock.BOTTOM)||state.getValue(GirderBlock.AXIS)!=Direction.Axis.Y?state:state.setValue(GirderBlock.AXIS,state.getValue(GirderBlock.X)?Direction.Axis.X:Direction.Axis.Z);}
    private GirderWrenchBehavior(){}
}
