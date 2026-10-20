package com.simibubi.create.content.equipment.bell;

import com.simibubi.create.content.contraptions.StructureTransform;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;

/** Source AllShapes selection geometry; bell collision retains native BellBlock rules. */
public final class BellShapes {
    private static final Map<BellAttachType, Map<Direction,VoxelShape>> BELLS = new EnumMap<>(BellAttachType.class);
    private static final Map<Direction,VoxelShape> DESK = new EnumMap<>(Direction.class);
    static {
        var body = Block.box(3,1,3,13,13,13);
        for (var attachment : BellAttachType.values()) {
            var base = Shapes.or(body, switch (attachment) {
                case FLOOR -> Block.box(0,0,5,16,11,11);
                case CEILING -> Block.box(0,5,5,16,16,11);
                case SINGLE_WALL -> Block.box(5,5,8,11,11,16);
                case DOUBLE_WALL -> Block.box(5,5,0,11,11,16);
            });
            var map = new EnumMap<Direction,VoxelShape>(Direction.class);
            for (var facing : Direction.Plane.HORIZONTAL) map.put(facing, rotate(base,0,yaw(facing)));
            BELLS.put(attachment,map);
        }
        var base = Shapes.or(Block.box(3,0,3,13,3,13),Block.box(4,0,4,12,9,12));
        for (var facing : Direction.values()) DESK.put(facing,rotate(base,facing==Direction.UP?0:facing==Direction.DOWN?180:90,yaw(facing)));
    }
    private static double yaw(Direction facing) { return facing.getAxis().isVertical()?0:-facing.get2DDataValue()*90; }
    private static VoxelShape rotate(VoxelShape shape,double x,double y) {
        var parts = new ArrayList<VoxelShape>();
        shape.forAllBoxes((x0,y0,z0,x1,y1,z1)->{
            var a=turn(new Vec3(x0,y0,z0),x,y);var b=turn(new Vec3(x1,y1,z1),x,y);
            parts.add(Shapes.box(Math.min(a.x,b.x),Math.min(a.y,b.y),Math.min(a.z,b.z),Math.max(a.x,b.x),Math.max(a.y,b.y),Math.max(a.z,b.z)));
        });
        return Shapes.or(Shapes.empty(),parts.toArray(VoxelShape[]::new)).optimize();
    }
    private static Vec3 turn(Vec3 point,double x,double y) { var p=StructureTransform.rotate(StructureTransform.rotate(point.subtract(.5,.5,.5),x,Direction.Axis.X),y,Direction.Axis.Y).add(.5,.5,.5);return new Vec3(Math.rint(p.x*16)/16,Math.rint(p.y*16)/16,Math.rint(p.z*16)/16); }
    public static VoxelShape bell(BellAttachType type,Direction facing) { return BELLS.get(type).get(facing); }
    public static VoxelShape desk(Direction facing) { return DESK.get(facing); }
    private BellShapes() {}
}
