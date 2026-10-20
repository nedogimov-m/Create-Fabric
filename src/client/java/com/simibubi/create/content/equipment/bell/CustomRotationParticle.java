package com.simibubi.create.content.equipment.bell;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/** Source UV mirroring/orientation extracted into native quad state, without live render callbacks. */
public abstract class CustomRotationParticle extends SimpleAnimatedParticle {
    protected boolean mirror;
    protected int loopLength;
    protected CustomRotationParticle(ClientLevel level,double x,double y,double z,SpriteSet sprites,float acceleration) { super(level,x,y,z,sprites,acceleration);hasPhysics=false; }
    protected void selectSpriteLoopingWithAge(SpriteSet sprites) { setSprite(sprites.get(age%loopLength,loopLength)); }
    public Identifier spriteName() { return sprite.contents().name(); }
    protected Quaternionf getCustomRotation(Camera camera,float partial) { return new Quaternionf(camera.rotation()).rotateZ(Mth.lerp(partial,oRoll,roll)); }
    @Override protected float getU0() { return mirror?super.getU1():super.getU0(); }
    @Override protected float getU1() { return mirror?super.getU0():super.getU1(); }
    @Override public void extract(QuadParticleRenderState state,Camera camera,float partial) {
        // Native quads start at +X; the source starts at -X with opposite winding.
        // Reverse local X/Z to preserve both the source front face and its UV order.
        var rotation=getCustomRotation(camera,partial).rotateY((float)Math.PI);
        var origin=camera.position();extractRotatedQuad(state,rotation,(float)(Mth.lerp(partial,xo,x)-origin.x),(float)(Mth.lerp(partial,yo,y)-origin.y),(float)(Mth.lerp(partial,zo,z)-origin.z),partial);
    }
}
