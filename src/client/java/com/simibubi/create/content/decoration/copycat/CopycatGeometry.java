package com.simibubi.create.content.decoration.copycat;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.*;

/** Exact source cropAndMove geometry/UV math, adapted from16-unit sprite coordinates to native normalized UVs. */
public final class CopycatGeometry {
    private static Vec3 point(MutableQuadView q,int index){return new Vec3(q.x(index),q.y(index),q.z(index));}
    private static float u(TextureAtlasSprite sprite,float atlas){return (atlas-sprite.getU0())/(sprite.getU1()-sprite.getU0());}
    private static float v(TextureAtlasSprite sprite,float atlas){return (atlas-sprite.getV0())/(sprite.getV1()-sprite.getV0());}
    public static void cropAndMove(MutableQuadView q,TextureAtlasSprite sprite,AABB crop,Vec3 move){
        var a=point(q,0);var b=point(q,1);var c=point(q,2);var d=point(q,3);var center=a.add(b).add(c).add(d).scale(.25);var uAxis=d.add(c).scale(.5);var vAxis=b.add(c).scale(.5);
        float uScale=Math.round((u(sprite,q.u(3))-u(sprite,q.u(0)))*16/a.distanceTo(d));float vScale=Math.round((v(sprite,q.v(1))-v(sprite,q.v(0)))*16/a.distanceTo(b));
        if(uScale==0){uAxis=b.add(c).scale(.5);vAxis=d.add(c).scale(.5);uScale=Math.round((u(sprite,q.u(1))-u(sprite,q.u(0)))*16/a.distanceTo(b));vScale=Math.round((v(sprite,q.v(3))-v(sprite,q.v(0)))*16/a.distanceTo(d));}
        uAxis=uAxis.subtract(center).normalize();vAxis=vAxis.subtract(center).normalize();
        for(int index=0;index<4;index++){var old=point(q,index);var cropped=new Vec3(Math.clamp(old.x,crop.minX,crop.maxX),Math.clamp(old.y,crop.minY,crop.maxY),Math.clamp(old.z,crop.minZ,crop.maxZ));var difference=cropped.subtract(old);
            if(difference.lengthSqr()>0)q.uv(index,sprite.getU(u(sprite,q.u(index))+(float)uAxis.dot(difference)*uScale/16),sprite.getV(v(sprite,q.v(index))+(float)vAxis.dot(difference)*vScale/16));
            var shifted=cropped.add(move);q.pos(index,(float)shifted.x,(float)shifted.y,(float)shifted.z);
        }
    }
    private CopycatGeometry(){}
}
