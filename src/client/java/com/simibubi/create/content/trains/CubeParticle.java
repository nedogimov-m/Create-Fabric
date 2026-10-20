package com.simibubi.create.content.trains;

import com.simibubi.create.foundation.mixin.client.CarriageParticleCollisionAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.*;
import net.minecraft.util.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** All six source cube faces extracted into native RenderPearl particle/OIT data. */
public final class CubeParticle extends SingleQuadParticle {
  private final boolean hot;
  private boolean billowing;

  private CubeParticle(
      ClientLevel level,
      CubeParticleData data,
      double x,
      double y,
      double z,
      double dx,
      double dy,
      double dz,
      SpriteSet sprites,
      RandomSource random) {
    super(level, x, y, z, sprites.get(random));
    xd = dx;
    yd = dy;
    zd = dz;
    quadSize = data.scale();
    hot = data.hot();
    setColor(data.r(), data.g(), data.b());
    setSize(data.scale() * .5f, data.scale() * .5f);
    lifetime = Math.max(1, (int) (data.avgAge() + (random.nextDouble() * 2 - 1) * 8));
  }

  @Override
  protected Layer getLayer() {
    return Layer.TRANSLUCENT;
  }

  @Override
  public int getLightCoords(float partial) {
    return LightCoordsUtil.FULL_BRIGHT;
  }

  @Override
  public void tick() {
    if (hot && age > 0) {
      if (yo == y) {
        billowing = true;
        ((CarriageParticleCollisionAccessor) (Object) this).create$setStoppedByCollision(false);
        if (xd == 0 && zd == 0) {
          var diff = Vec3.atCenterOf(BlockPos.containing(x, y, z)).subtract(x, y, z);
          xd = -diff.x * .1;
          zd = -diff.z * .1;
        }
        xd *= 1.1;
        yd *= .9;
        zd *= 1.1;
      } else if (billowing) yd *= 1.2;
    }
    super.tick();
  }

  @Override
  public float getQuadSize(float partial) {
    double life = Math.clamp(age + partial, 0, lifetime);
    return (float) (quadSize * (1 - Math.pow(life / lifetime, 3)));
  }

  @Override
  public void extract(QuadParticleRenderState state, Camera camera, float partial) {
    var origin =
        new Vec3(Mth.lerp(partial, xo, x), Mth.lerp(partial, yo, y), Mth.lerp(partial, zo, z))
            .subtract(camera.position());
    float size = getQuadSize(partial);
    if (size <= 0) return;
    for (var side : Direction.values()) {
      var normal = side.getUnitVec3();
      var centre = origin.add(normal.scale(size));
      var orientation =
          new Quaternionf()
              .rotationTo(0, 0, 1, (float) normal.x, (float) normal.y, (float) normal.z);
      // Extraction freezes real centers/orientations/UV/color/light; native submit reads no
      // particle/world.
      extractRotatedQuad(
          state, orientation, (float) centre.x, (float) centre.y, (float) centre.z, partial);
    }
  }

  public static final class Factory implements ParticleProvider<CubeParticleData> {
    private final SpriteSet sprites;

    public Factory(SpriteSet sprites) {
      this.sprites = sprites;
    }

    @Override
    public Particle createParticle(
        CubeParticleData data,
        ClientLevel level,
        double x,
        double y,
        double z,
        double dx,
        double dy,
        double dz,
        RandomSource random) {
      return new CubeParticle(level, data, x, y, z, dx, dy, dz, sprites, random);
    }
  }
}
