package com.simibubi.create.content.contraptions.minecart;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.*;
import net.minecraft.world.phys.Vec3;

/** Read-only extraction of the actual native Old rail smoothing or New weighted-step interpolation. */
public record NativeCartRenderPose(Vec3 position,Vec3 linear,float yaw,float pitch,boolean modern,Vec3 front,Vec3 back){
    public static NativeCartRenderPose extract(AbstractMinecart cart,float partial){
        var linear=cart.getPosition(partial);var position=linear;float yaw=cart.getYRot(partial),pitch=cart.getXRot(partial);boolean modern=cart.getBehavior() instanceof NewMinecartBehavior;
        Vec3 front=null,back=null;
        if(cart.getBehavior() instanceof NewMinecartBehavior behavior){
            if(behavior.cartHasPosRotLerp()){position=behavior.getCartLerpPosition(partial);yaw=behavior.getCartLerpYRot(partial);pitch=behavior.getCartLerpXRot(partial);}else{yaw=cart.getYRot();pitch=cart.getXRot();}
        }else if(cart.getBehavior() instanceof OldMinecartBehavior behavior){
            var onRail=behavior.getPos(linear.x,linear.y,linear.z);
            if(onRail!=null){front=behavior.getPosOffs(linear.x,linear.y,linear.z,.30000001192092896);back=behavior.getPosOffs(linear.x,linear.y,linear.z,-.30000001192092896);if(front==null)front=onRail;if(back==null)back=onRail;position=new Vec3(onRail.x,(front.y+back.y)/2,onRail.z);var diff=back.subtract(front);if(diff.lengthSqr()>0){diff=diff.normalize();yaw=(float)Math.toDegrees(Math.atan2(diff.z,diff.x));pitch=(float)(Math.atan(diff.y)*73);}}
            yaw=180-yaw;
        }
        if(front==null){var axis=new Vec3(.5,0,0).yRot((float)Math.toRadians(yaw));front=position.add(axis);back=position.subtract(axis);}
        return new NativeCartRenderPose(position,linear,yaw,-pitch,modern,front,back);
    }
    public Vec3 adjustment(){return position.subtract(linear);}
    public static Vec3 adjustmentForAncestor(Entity owner,float partial){
        var cursor=owner;for(int depth=0;depth<64&&cursor!=null;depth++){var next=cursor.getVehicle();if(next instanceof AbstractMinecart cart)return extract(cart,partial).adjustment();cursor=next;}return Vec3.ZERO;
    }
}
