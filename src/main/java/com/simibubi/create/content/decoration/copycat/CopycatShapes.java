package com.simibubi.create.content.decoration.copycat;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Literal source CASING_3PX and STEP_BOTTOM/TOP shapes, without a common client/math dependency. */
public final class CopycatShapes {
    public static VoxelShape panel(Direction facing){return switch(facing){case UP->Block.box(0,0,0,16,3,16);case DOWN->Block.box(0,13,0,16,16,16);case SOUTH->Block.box(0,0,0,16,16,3);case NORTH->Block.box(0,0,13,16,16,16);case EAST->Block.box(0,0,0,3,16,16);case WEST->Block.box(13,0,0,16,16,16);};}
    public static VoxelShape step(Direction facing,boolean top){int y=top?8:0;return switch(facing){case SOUTH->Block.box(0,y,8,16,y+8,16);case NORTH->Block.box(0,y,0,16,y+8,8);case EAST->Block.box(8,y,0,16,y+8,16);case WEST->Block.box(0,y,0,8,y+8,16);default->throw new IllegalArgumentException("Copycat Step has a horizontal source facing");};}
    private CopycatShapes(){}
}
