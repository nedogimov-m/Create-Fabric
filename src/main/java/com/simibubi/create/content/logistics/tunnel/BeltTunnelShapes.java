package com.simibubi.create.content.logistics.tunnel;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import static net.minecraft.world.level.block.Block.box;

public final class BeltTunnelShapes {
    private static final Map<Direction.Axis,Map<BeltTunnelBlock.Shape,VoxelShape>> CACHE=new EnumMap<>(Direction.Axis.class);
    static {
        var solid=box(0,-5,0,16,16,16);var openings=new EnumMap<Direction,VoxelShape>(Direction.class);
        openings.put(Direction.NORTH,box(2,-5,0,14,10,2));openings.put(Direction.SOUTH,box(2,-5,14,14,10,16));openings.put(Direction.EAST,box(14,-5,2,16,10,14));openings.put(Direction.WEST,box(0,-5,2,2,10,14));
        for(var axis:new Direction.Axis[]{Direction.Axis.X,Direction.Axis.Z}){
            var shapes=new EnumMap<BeltTunnelBlock.Shape,VoxelShape>(BeltTunnelBlock.Shape.class);
            for(var shape:BeltTunnelBlock.Shape.values()){
                var holes=Shapes.empty();var left=Direction.get(Direction.AxisDirection.POSITIVE,axis).getClockWise();
                for(var side:Direction.Plane.HORIZONTAL)if(side.getAxis()==axis||shape==BeltTunnelBlock.Shape.CROSS||shape==BeltTunnelBlock.Shape.T_LEFT&&side==left||shape==BeltTunnelBlock.Shape.T_RIGHT&&side==left.getOpposite())holes=Shapes.or(holes,openings.get(side));
                shapes.put(shape,Shapes.join(solid,holes,BooleanOp.ONLY_FIRST));
            }CACHE.put(axis,shapes);
        }
    }
    public static VoxelShape getShape(BlockState state){return CACHE.get(state.getValue(BeltTunnelBlock.HORIZONTAL_AXIS)).get(state.getValue(BeltTunnelBlock.SHAPE));}
    private BeltTunnelShapes(){}
}
