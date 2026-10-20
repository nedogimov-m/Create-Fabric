package com.simibubi.create.content.contraptions.minecart;

import net.minecraft.world.phys.Vec3;

/** Source coupling vector operations on the actual native double-precision positions. */
public final class CartMath {
    public static Vec3 clamp(Vec3 value,double length){double square=value.lengthSqr();return square>length*length?value.scale(length/Math.sqrt(square)):value;}
    public static Vec3 project(Vec3 value,Vec3 axis){double square=axis.lengthSqr();return square==0?Vec3.ZERO:axis.scale(value.dot(axis)/square);}
    public static Vec3 intersectSphere(Vec3 start,Vec3 direction,Vec3 center,double radius){
        var offset=start.subtract(center);double a=direction.lengthSqr(),b=2*direction.dot(offset),c=offset.lengthSqr()-radius*radius,disc=b*b-4*a*c;if(a==0||disc<0)return null;
        double near=(-b-Math.sqrt(disc))/(2*a),far=(-b+Math.sqrt(disc))/(2*a);return start.add(direction.scale(near>=0?near:far));
    }
    private CartMath(){}
}
