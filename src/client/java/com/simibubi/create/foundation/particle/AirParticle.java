package com.simibubi.create.foundation.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.*;
import net.minecraft.world.phys.Vec3;

/** Original twirling airflow path, submitted by Minecraft's native animated particle renderer. */
public final class AirParticle extends SimpleAnimatedParticle {
    private final Vec3 origin,target;
    private final float drag,radius,angleOffset;
    private final boolean axisX;
    public AirParticle(ClientLevel level,AirParticleData data,double x,double y,double z,double dx,double dy,double dz,SpriteSet sprites,RandomSource random){
        super(level,x,y,z,sprites,random.nextFloat()*.5f);quadSize*=.75f;hasPhysics=false;setPos(x,y,z);xo=x;yo=y;zo=z;origin=new Vec3(x,y,z);target=origin.add(dx,dy,dz);drag=data.drag();radius=random.nextFloat()/6;angleOffset=random.nextFloat()*360;axisX=random.nextBoolean();double length=new Vec3(dx,dy,dz).length();lifetime=Math.clamp((int)(length/Math.max(data.speed(),.0001f)),1,60);setSprite(sprites.get(7,8));setAlpha(.25f);if(length==0){remove();setAlpha(0);}
    }
    @Override public Layer getLayer(){return Layer.TRANSLUCENT;}
    @Override public int getLightCoords(float partial){var pos=BlockPos.containing(x,y,z);return level.isLoaded(pos)?LightCoordsUtil.getLightCoords(level,pos):0;}
    @Override public void tick(){xo=x;yo=y;zo=z;if(age++>=lifetime){remove();return;}float progress=(float)Math.pow((float)age/lifetime,drag);double angle=Math.toRadians((progress*720+angleOffset)%360);double twirl=radius*Math.sin(angle),vertical=radius*Math.cos(angle);var next=origin.lerp(target,progress).add(axisX?0:-twirl,vertical,axisX?twirl:0);xd=next.x-x;yd=next.y-y;zd=next.z-z;setSpriteFromAge(sprites);move(xd,yd,zd);}
    public static final class Factory implements ParticleProvider<AirParticleData> {
        private final SpriteSet sprites;
        public Factory(SpriteSet sprites){this.sprites=sprites;}
        @Override public Particle createParticle(AirParticleData data,ClientLevel level,double x,double y,double z,double dx,double dy,double dz,RandomSource random){return new AirParticle(level,data,x,y,z,dx,dy,dz,sprites,random);}
    }
}
