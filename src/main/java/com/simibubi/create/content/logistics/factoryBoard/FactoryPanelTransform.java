package com.simibubi.create.content.logistics.factoryBoard;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class FactoryPanelTransform {
    public static Vec3 rotate(Vec3 v,double degrees,Direction.Axis axis) {
        double a=Math.toRadians(degrees),sin=Math.sin(a),cos=Math.cos(a);
        return switch(axis){case X->new Vec3(v.x,v.y*cos-v.z*sin,v.z*cos+v.y*sin);case Y->new Vec3(v.x*cos+v.z*sin,v.y,v.z*cos-v.x*sin);case Z->new Vec3(v.x*cos-v.y*sin,v.y*cos+v.x*sin,v.z);};
    }
    public static Vec3 rotateCentered(Vec3 v,double degrees,Direction.Axis axis) { return rotate(v.subtract(.5,.5,.5),degrees,axis).add(.5,.5,.5); }
    public static Vec3 normal(BlockState state,Vec3 v) { return rotate(rotate(rotate(v,180,Direction.Axis.Y),Math.toDegrees(FactoryPanelBlock.getXRot(state))+90,Direction.Axis.X),Math.toDegrees(FactoryPanelBlock.getYRot(state)),Direction.Axis.Y); }
    public static Vec3 point(BlockState state,Vec3 v) { return normal(state,v.subtract(.5,.5,.5)).add(.5,.5,.5); }
    private FactoryPanelTransform() {}
}
