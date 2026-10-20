package com.simibubi.create.content.equipment.bell;

import com.mojang.math.Axis;
import com.simibubi.create.AllParticleTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import org.joml.Quaternionf;

public class SoulBaseParticle extends CustomRotationParticle {

	private final SpriteSet animatedSprite;

	public SoulBaseParticle(ClientLevel worldIn, double x, double y, double z, double vx, double vy, double vz,
                            SpriteSet spriteSet) {
		super(worldIn, x, y, z, spriteSet, 0);
		this.animatedSprite = spriteSet;
		this.quadSize = 0.5f;
		this.setSize(this.quadSize, this.quadSize);
		this.loopLength = 16 + (int) (this.random.nextFloat() * 2f - 1f);
		this.lifetime = (int) (90.0F / (this.random.nextFloat() * 0.36F + 0.64F));
		this.selectSpriteLoopingWithAge(animatedSprite);
		this.hasPhysics = false;
	}

	@Override
	public void tick() {
		selectSpriteLoopingWithAge(animatedSprite);

		BlockPos pos = BlockPos.containing(x, y, z);
		if (age++ >= lifetime || !SoulPulseEffect.isDark(level, pos))
			remove();
	}

	@Override
	public Quaternionf getCustomRotation(Camera camera, float partialTicks) {
		return Axis.XP.rotationDegrees(90);
	}

    public static final class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Factory(SpriteSet sprites) { this.sprites=sprites; }
        @Override public Particle createParticle(SimpleParticleType data,ClientLevel level,double x,double y,double z,double dx,double dy,double dz,RandomSource random) { return new SoulBaseParticle(level,x,y,z,dx,dy,dz,sprites); }
    }
}
