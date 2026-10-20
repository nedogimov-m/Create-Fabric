// Ponder/Catnip 1.0.82 source, MIT; native26.3 particle execution/extraction adaptation.
package net.createmod.ponder.foundation;

import net.createmod.catnip.levelWrappers.WrappedClientLevel;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.mixin.client.PonderParticleEngineAccessor;
import net.createmod.ponder.mixin.client.PonderParticleAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.core.particles.ParticleOptions;

/** Actual native particles, local queues, ticks and render-state extraction; no live engine mutation. */
public final class PonderWorldParticles {
  private final PonderLevel world;
  private WrappedClientLevel clientWorld;
  private ParticleEngine engine;

  public PonderWorldParticles(PonderLevel world) { this.world=world; }

  private ParticleEngine engine() {
    if(engine==null) {
      clientWorld=WrappedClientLevel.of(world);
      var resources=((PonderParticleEngineAccessor)Minecraft.getInstance().particleEngine).ponder$resources();
      engine=new ParticleEngine(clientWorld,resources);
    }
    return engine;
  }

  public Particle create(ParticleOptions data,double x,double y,double z,double dx,double dy,double dz) {
    return engine().createParticle(data,x,y,z,dx,dy,dz);
  }

  public void addParticle(Particle particle) {
    if(particle==null)return;
    var owner=((PonderParticleAccessor)particle).ponder$world();
    if(!(owner instanceof WrappedClientLevel wrapped)||wrapped.getWrappedLevel()!=world)
      throw new IllegalArgumentException("A Ponder particle must be created in its actual owning world");
    engine().add(particle);
  }

  public void tick() {
    if(engine==null)return;
    clientWorld.refreshClock();
    engine.tick();
  }

  public ParticlesRenderState extract(Frustum frustum,Camera camera,float partial) {
    var state=new ParticlesRenderState();
    if(engine!=null)engine.extract(state,frustum,camera,partial);
    return state;
  }

  public void clearEffects() { if(engine!=null)engine.clearParticles(); }

  /** Drop particles with old sprites and release the old connection-owning wrapper. */
  public void invalidateResources() {
    clearEffects();
    engine=null;
    clientWorld=null;
  }
}
