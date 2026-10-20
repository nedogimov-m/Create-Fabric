package com.simibubi.create.content.redstone.link;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Source hit positions shared by common input and native client extraction. */
public final class RedstoneLinkFrequencySlot {
    public static Vec3 offset(Direction facing,boolean first){
        if(facing.getAxis().isHorizontal()){
            var point=new Vec3(8/16d,(5.5+(first?5:0))/16d,3.01/16d);
            return point.subtract(.5,.5,.5).yRot((float)Math.toRadians(-facing.toYRot())).add(.5,.5,.5);
        }
        var point=new Vec3(8/16d,3.01/16d,(5.5+(first?5:0))/16d);
        return facing==Direction.DOWN?point.subtract(.5,.5,.5).xRot((float)Math.PI).add(.5,.5,.5):point;
    }
    public static boolean test(BlockState state,boolean first,Vec3 localHit){
        return offset(state.getValue(RedstoneLinkBlock.FACING),first).distanceToSqr(localHit)<.25*.25;
    }
    public static VoxelShape shape(Direction facing){
        var min=facing.getAxis().isVertical()?new Vec3(2,2,-1).scale(1/16d):new Vec3(3,1,-1).scale(1/16d);
        var max=facing.getAxis().isVertical()?new Vec3(14,14,3).scale(1/16d):new Vec3(13,15,3).scale(1/16d);
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(int i=0;i<8;i++){
            var point=new Vec3((i&1)==0?min.x:max.x,(i&2)==0?min.y:max.y,(i&4)==0?min.z:max.z).subtract(.5,.5,.5);
            point=facing.getAxis().isHorizontal()?point.yRot((float)Math.toRadians(-facing.toYRot())):point.xRot(facing==Direction.UP?(float)Math.PI/2:-(float)Math.PI/2);
            point=point.add(.5,.5,.5);minX=Math.min(minX,point.x);minY=Math.min(minY,point.y);minZ=Math.min(minZ,point.z);maxX=Math.max(maxX,point.x);maxY=Math.max(maxY,point.y);maxZ=Math.max(maxZ,point.z);
        }
        return Shapes.create(new AABB(minX,minY,minZ,maxX,maxY,maxZ));
    }
    private RedstoneLinkFrequencySlot(){}
}
