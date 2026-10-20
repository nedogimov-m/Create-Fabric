package com.simibubi.create.content.contraptions.piston;

import java.util.EnumMap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Source twelve-voxel casing, four-voxel rod and vanilla short piston head. */
public final class PistonShapes {
    private static final EnumMap<Direction,VoxelShape> CASINGS=new EnumMap<>(Direction.class);
    static{for(var face:Direction.values())CASINGS.put(face,switch(face){
        case UP->Block.box(0,0,0,16,12,16);case DOWN->Block.box(0,4,0,16,16,16);
        case EAST->Block.box(0,0,0,12,16,16);case WEST->Block.box(4,0,0,16,16,16);
        case SOUTH->Block.box(0,0,0,16,16,12);case NORTH->Block.box(0,0,4,16,16,16);
    });}
    public static VoxelShape pole(Direction.Axis axis){return switch(axis){case X->Block.box(0,6,6,16,10,10);case Y->Block.box(6,0,6,10,16,10);case Z->Block.box(6,6,0,10,10,16);};}
    public static VoxelShape casing(Direction facing,boolean extended){return extended?Shapes.or(CASINGS.get(facing),pole(facing.getAxis())):CASINGS.get(facing);}
    public static VoxelShape head(Direction facing){return Blocks.PISTON_HEAD.defaultBlockState().setValue(DirectionalBlock.FACING,facing).setValue(PistonHeadBlock.SHORT,true).getShape(null,null);}
    private PistonShapes(){}
}
