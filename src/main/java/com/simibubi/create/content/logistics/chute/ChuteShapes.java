package com.simibubi.create.content.logistics.chute;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import static net.minecraft.world.level.block.Block.box;

public final class ChuteShapes {
    private static final Map<BlockState,VoxelShape> CACHE=new java.util.concurrent.ConcurrentHashMap<>(),COLLISIONS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final VoxelShape VERTICAL=Shapes.or(box(1,8,1,15,16,15),box(2,0,2,14,8,14)),INTERSECTION_MASK=box(0,-16,0,16,16,16),COLLISION_MASK=box(0,0,0,16,24,16);
    private static final Map<Direction,VoxelShape> SLOPES=new EnumMap<>(Direction.class);
    static {var base=Shapes.empty();for(int i=0;i<16;i++)base=Shapes.or(base,box(1,-15,0,15,4,1).move(0,i/16d,i/16d));SLOPES.put(Direction.SOUTH,base);for(var facing:new Direction[]{Direction.WEST,Direction.NORTH,Direction.EAST}){var boxes=new ArrayList<VoxelShape>();base.forAllBoxes((x0,y0,z0,x1,y1,z1)->boxes.add(Shapes.box(1-z1,y0,x0,1-z0,y1,x1)));base=Shapes.or(Shapes.empty(),boxes.toArray(VoxelShape[]::new));SLOPES.put(facing,base);}}
    public static VoxelShape getShape(BlockState state){return CACHE.computeIfAbsent(state,ChuteShapes::create);}
    private static VoxelShape create(BlockState state){if(state.getBlock() instanceof SmartChuteBlock)return Shapes.block();var shape=state.getValue(ChuteBlock.SHAPE);boolean full=shape==ChuteBlock.Shape.INTERSECTION||shape==ChuteBlock.Shape.ENCASED;var facing=state.getValue(ChuteBlock.FACING);if(facing==Direction.DOWN)return full?Shapes.block():VERTICAL;var slope=SLOPES.get(facing);return full?Shapes.joinUnoptimized(INTERSECTION_MASK,Shapes.or(Shapes.block(),slope),BooleanOp.AND):slope;}
    public static VoxelShape getCollisionShape(BlockState state){return COLLISIONS.computeIfAbsent(state,key->Shapes.joinUnoptimized(COLLISION_MASK,getShape(key),BooleanOp.AND));}
    private ChuteShapes(){}
}
