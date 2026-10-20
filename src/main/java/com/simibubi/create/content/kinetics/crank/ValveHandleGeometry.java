package com.simibubi.create.content.kinetics.crank;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.*;

/** Rotate the original UP-oriented source cuboids, preserving exact voxel bounds. */
public final class ValveHandleGeometry {
    public static VoxelShape shape(Direction facing) {
        return Shapes.or(box(facing,5,0,5,11,4,11), box(facing,1,3,1,15,8,15));
    }
    public static VoxelShape box(Direction facing, double x0,double y0,double z0,double x1,double y1,double z1) {
        return switch (facing) {
            case UP -> net.minecraft.world.level.block.Block.box(x0,y0,z0,x1,y1,z1);
            case DOWN -> net.minecraft.world.level.block.Block.box(x0,16-y1,16-z1,x1,16-y0,16-z0);
            case NORTH -> net.minecraft.world.level.block.Block.box(x0,z0,16-y1,x1,z1,16-y0);
            case SOUTH -> net.minecraft.world.level.block.Block.box(x0,16-z1,y0,x1,16-z0,y1);
            case WEST -> net.minecraft.world.level.block.Block.box(16-y1,z0,16-x1,16-y0,z1,16-x0);
            case EAST -> net.minecraft.world.level.block.Block.box(y0,z0,x0,y1,z1,x1);
        };
    }
    private ValveHandleGeometry() {}
}
