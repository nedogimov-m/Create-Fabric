package com.simibubi.create.content.contraptions.chassis;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Source attachment search over immediately available chunks. An incomplete scan never becomes a smaller carrier. */
public final class ChassisAttachmentLogic {
    private static BlockState state(Level level,BlockPos pos)throws AssemblyException{
        if(!level.isInValidBounds(pos))return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        if(!LoadedLevelAccess.isLoaded(level,pos))throw AssemblyException.entitiesNotReady(pos);
        return LoadedLevelAccess.blockState(level,pos);
    }
    public static void enqueue(Level level,BlockPos pos,Direction forced,Queue<BlockPos> frontier,Set<BlockPos> visited)throws AssemblyException{
        if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof ChassisBlockEntity chassis))throw AssemblyException.structureChanged(pos);
        chassis.addAttachedChasses(frontier,visited);for(var included:chassis.getIncludedBlockPositions(forced,false))if(!visited.contains(included))frontier.add(included);
    }
    public static List<ChassisBlockEntity> collectGroup(ChassisBlockEntity start)throws AssemblyException{
        var level=start.getLevel();if(level==null)throw AssemblyException.structureChanged(start.getBlockPos());
        var frontier=new ArrayDeque<BlockPos>();var visited=new HashSet<BlockPos>();var collected=new ArrayList<ChassisBlockEntity>();frontier.add(start.getBlockPos());
        while(!frontier.isEmpty()){
            var pos=frontier.removeFirst();if(!visited.add(pos))continue;state(level,pos);
            if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof ChassisBlockEntity chassis))throw AssemblyException.structureChanged(pos);
            collected.add(chassis);addAttachedChasses(chassis,frontier,visited);
        }return List.copyOf(collected);
    }
    public static void addAttachedChasses(ChassisBlockEntity chassis,Queue<BlockPos> frontier,Set<BlockPos> visited)throws AssemblyException{
        var level=chassis.getLevel();if(level==null)throw AssemblyException.structureChanged(chassis.getBlockPos());var current=chassis.getBlockState();if(!(current.getBlock() instanceof AbstractChassisBlock))throw AssemblyException.structureChanged(chassis.getBlockPos());
        var axis=current.getValue(AbstractChassisBlock.AXIS);boolean radial=current.getBlock() instanceof RadialChassisBlock;
        for(var direction:Direction.values()){
            if(radial&&direction.getAxis()!=axis)continue;var next=chassis.getBlockPos().relative(direction);if(visited.contains(next))continue;var other=state(level,next);
            if(other.getBlock()==current.getBlock()&&other.getValue(AbstractChassisBlock.AXIS)==axis)frontier.add(next.immutable());
        }
    }
    public static List<BlockPos> included(ChassisBlockEntity chassis,Direction forced,int range)throws AssemblyException{
        var level=chassis.getLevel();if(level==null)throw AssemblyException.structureChanged(chassis.getBlockPos());var current=chassis.getBlockState();if(!(current.getBlock() instanceof AbstractChassisBlock block))throw AssemblyException.structureChanged(chassis.getBlockPos());
        var result=new LinkedHashSet<BlockPos>();var axis=current.getValue(AbstractChassisBlock.AXIS);
        if(block instanceof LinearChassisBlock){
            for(int sign:new int[]{1,-1}){
                var facing=Direction.get(Direction.AxisDirection.POSITIVE,axis);if(sign<0)facing=facing.getOpposite();boolean sticky=current.getValue(block.getGlueableSide(current,facing));if(forced!=facing&&!sticky)continue;
                for(int i=1;i<=range;i++){
                    var pos=chassis.getBlockPos().relative(facing,i);var sampled=state(level,pos);
                    if(!BlockMovementChecks.isMovementNecessary(sampled,level,pos)||BlockMovementChecks.isBrittle(sampled))break;
                    result.add(pos.immutable());if(BlockMovementChecks.isNotSupportive(sampled,facing))break;
                }
            }return List.copyOf(result);
        }
        for(var side:Direction.values()){
            if(side.getAxis()==axis||!current.getValue(block.getGlueableSide(current,side)))continue;
            var frontier=new ArrayDeque<BlockPos>();var seen=new HashSet<BlockPos>();frontier.add(chassis.getBlockPos().relative(side));
            while(!frontier.isEmpty()){
                var pos=frontier.removeFirst();if(!seen.add(pos))continue;
                // Out-of-range cells cannot belong to this search; no chunk lookup is needed for them.
                if(!pos.closerThan(chassis.getBlockPos(),range+.5))continue;
                var sampled=state(level,pos);if(!BlockMovementChecks.isMovementNecessary(sampled,level,pos)||BlockMovementChecks.isBrittle(sampled))continue;
                if(!pos.equals(chassis.getBlockPos()))result.add(pos.immutable());
                for(var offset:Direction.values())if(offset.getAxis()!=axis&&(!pos.equals(chassis.getBlockPos())||offset==side)&&!BlockMovementChecks.isNotSupportive(sampled,offset))frontier.add(pos.relative(offset));
            }
        }return List.copyOf(result);
    }
    private ChassisAttachmentLogic(){}
}
