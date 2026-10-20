package com.simibubi.create.content.logistics.funnel;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.*;
import static net.minecraft.world.level.block.Block.box;

final class FunnelShapes {
    private static VoxelShape combine(double[]... boxes){var result=Shapes.empty();for(var b:boxes)result=Shapes.or(result,box(b[0],b[1],b[2],b[3],b[4],b[5]));return result;}
    private static final VoxelShape WALL=combine(new double[]{2,2,14,14,14,18},new double[]{1,8,12,15,15,14},new double[]{0.1,13,7,15.9,15,11},new double[]{0.1,9,8,15.9,13,12},new double[]{0.1,5,9,15.9,9,13},new double[]{0.1,1,10,15.9,5,14},new double[]{0.1,-1,11,15.9,1,15});
    private static final VoxelShape RETRACTED=Shapes.or(box(2,-2,14,14,14,18),box(0,-5,8,16,16,14));
    private static final VoxelShape EXTENDED=combine(new double[]{2,-2,14,14,14,18},new double[]{3,-4,10,13,13,14},new double[]{2,-4,6,14,14,10},new double[]{0,-5,0,16,16,6});
    private static final VoxelShape PERPENDICULAR=combine(new double[]{2,-2,14,14,14,18},new double[]{1,8,12,15,15,14},new double[]{0.1,13,7,15.9,15,11},new double[]{0.1,9,8,15.9,13,12},new double[]{0.1,5,9,15.9,9,13},new double[]{0.1,1,10,15.9,5,14},new double[]{0.1,-3,11,15.9,1,15});
    static VoxelShape rotate(VoxelShape shape,Direction direction){
        int turns=switch(direction){case EAST->1;case SOUTH->2;case WEST->3;default->0;};for(int i=0;i<turns;i++){var rotated=new java.util.ArrayList<VoxelShape>();shape.forAllBoxes((x0,y0,z0,x1,y1,z1)->rotated.add(Shapes.box(1-z1,y0,x0,1-z0,y1,x1)));shape=Shapes.or(Shapes.empty(),rotated.toArray(VoxelShape[]::new));}return shape.optimize();
    }
    private static VoxelShape buildNormal(Direction facing){return switch(facing){case UP->Shapes.or(box(2,-2,2,14,8,14),box(1,1,1,15,8,15),box(0,4,0,16,10,16));case DOWN->Shapes.or(box(2,8,2,14,18,14),box(1,8,1,15,15,15),box(0,6,0,16,12,16));default->rotate(WALL,facing);};}
    private static VoxelShape buildBelt(Direction facing,BeltFunnelBlock.Shape shape){return rotate(switch(shape){case RETRACTED->RETRACTED;case EXTENDED->EXTENDED;default->PERPENDICULAR;},facing);}
    private static VoxelShape buildCollision(Direction facing){return switch(facing){case NORTH->box(0,0,12,16,16,16);case SOUTH->box(0,0,0,16,16,4);case EAST->box(0,0,0,4,16,16);case WEST->box(12,0,0,16,16,16);case UP->box(0,0,0,16,4,16);case DOWN->box(0,12,0,16,16,16);};}
    private static final java.util.Map<Direction,VoxelShape> NORMAL=new java.util.EnumMap<>(Direction.class),COLLISION=new java.util.EnumMap<>(Direction.class);
    private static final java.util.Map<Direction,java.util.Map<BeltFunnelBlock.Shape,VoxelShape>> BELT=new java.util.EnumMap<>(Direction.class);
    static {
        for(var direction:Direction.values()){
            NORMAL.put(direction,buildNormal(direction));COLLISION.put(direction,buildCollision(direction));
            if(direction.getAxis().isVertical())continue;
            var shapes=new java.util.EnumMap<BeltFunnelBlock.Shape,VoxelShape>(BeltFunnelBlock.Shape.class);
            for(var shape:BeltFunnelBlock.Shape.values())shapes.put(shape,buildBelt(direction,shape));BELT.put(direction,shapes);
        }
    }
    static VoxelShape normal(Direction facing){return NORMAL.get(facing);}
    static VoxelShape belt(Direction facing,BeltFunnelBlock.Shape shape){return BELT.get(facing).get(shape);}
    static VoxelShape collision(Direction facing){return COLLISION.get(facing);}
    private FunnelShapes(){}
}
