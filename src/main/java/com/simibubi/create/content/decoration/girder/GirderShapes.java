package com.simibubi.create.content.decoration.girder;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.*;

/** Exact pinned AllShapes boxes, in native block units. */
public final class GirderShapes {
    public static final VoxelShape POLE=Block.box(4,0,4,12,16,12),X=Block.box(0,2,4,16,14,12),Z=Block.box(4,2,0,12,14,16);
    public static final VoxelShape CROSS=Shapes.or(Block.box(3,0,3,13,16,13),X,Z);
    public static VoxelShape shaft(Direction.Axis axis){return axis==Direction.Axis.X?Shapes.or(Z,Block.box(0,5,5,16,11,11)):Shapes.or(X,Block.box(5,5,0,11,11,16));}
    private GirderShapes(){}
}
