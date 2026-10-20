package com.simibubi.create.content.kinetics.steamEngine;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Four intersecting animated quads, extracted into Minecraft's native particle render state. */
public final class SteamJetParticle extends SimpleAnimatedParticle {
    private final float yaw,pitch;
    public SteamJetParticle(ClientLevel level,double x,double y,double z,double dx,double dy,double dz,SpriteSet sprites,RandomSource random){
        super(level,x,y,z,sprites,random.nextFloat()*0.5f);xd=yd=zd=0;gravity=0;quadSize=0.375f;setLifetime(21);setPos(x,y,z);roll=oRoll=random.nextFloat()*(float)Math.PI;
        yaw=(float)Mth.atan2(dx,dz)-(float)Math.PI;pitch=(float)Mth.atan2(dy,Math.sqrt(dx*dx+dz*dz))-(float)Math.PI/2;setSpriteFromAge(sprites);
    }
    @Override public Layer getLayer(){return Layer.OPAQUE;}
    @Override public int getLightCoords(float partial){var pos=BlockPos.containing(x,y,z);return level.isLoaded(pos)?LightCoordsUtil.getLightCoords(level,pos):0;}
    @Override public void extract(QuadParticleRenderState state,Camera camera,float partial){
        var origin=camera.position();float size=getQuadSize(partial),spin=Mth.lerp(partial,oRoll,roll);
        var base=new Quaternionf().rotationY(yaw).rotateX(pitch);var offset=new Vector3f(0,size,0).rotate(base);
        float cx=(float)(x-origin.x)+offset.x,cy=(float)(y-origin.y)+offset.y,cz=(float)(z-origin.z)+offset.z;
        for(int i=0;i<4;i++)extractRotatedQuad(state,new Quaternionf(base).rotateY(spin+(float)Math.PI/2*i+roll),cx,cy,cz,partial);
    }
    public static final class Factory implements ParticleProvider<SteamJetParticleData> {
        private final SpriteSet sprites;
        public Factory(SpriteSet sprites){this.sprites=sprites;}
        @Override public Particle createParticle(SteamJetParticleData data,ClientLevel level,double x,double y,double z,double dx,double dy,double dz,RandomSource random){return new SteamJetParticle(level,x,y,z,dx,dy,dz,sprites,random);}
    }
}
