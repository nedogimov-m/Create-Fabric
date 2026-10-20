package com.simibubi.create.content.logistics.packagerLink;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.*;
import org.joml.Quaternionf;

public final class WiFiParticle extends SimpleAnimatedParticle {
    private final boolean downward;
    public WiFiParticle(ClientLevel level,double x,double y,double z,double dy,SpriteSet sprites) { super(level,x,y+(dy<0?-1:1),z,sprites,0);downward=dy<0;quadSize=.5f;setSize(quadSize,quadSize);lifetime=16;hasPhysics=false;setSpriteFromAge(sprites); }
    @Override public Layer getLayer() { return Layer.TRANSLUCENT; }
    @Override public void tick() { setSpriteFromAge(sprites);if(age++>=lifetime)remove(); }
    @Override public int getLightCoords(float partial) { var pos=BlockPos.containing(x,y,z);return level.isLoaded(pos)?LightCoordsUtil.getLightCoords(level,pos):0; }
    @Override public void extract(QuadParticleRenderState state,Camera camera,float partial) { var origin=camera.position();extractRotatedQuad(state,new Quaternionf().rotateY(-camera.yRot()*Mth.DEG_TO_RAD).rotateZ(downward?Mth.PI:0),(float)(x-origin.x),(float)(y-origin.y),(float)(z-origin.z),partial); }
    public static final class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Factory(SpriteSet sprites) { this.sprites=sprites; }
        @Override public Particle createParticle(SimpleParticleType data,ClientLevel level,double x,double y,double z,double dx,double dy,double dz,RandomSource random) { return new WiFiParticle(level,x,y,z,dy,sprites); }
    }
}
