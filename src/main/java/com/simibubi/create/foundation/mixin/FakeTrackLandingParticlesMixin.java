package com.simibubi.create.foundation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.trains.track.FakeTrackBlock;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Suppress only source fake-track landing dust, preserving native fall damage and landing block callbacks. */
@Mixin(LivingEntity.class)
abstract class FakeTrackLandingParticlesMixin {
    @WrapOperation(method="checkFallDamage(DZLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"))
    private <T extends ParticleOptions>int create$skipInvisibleCoverageDust(ServerLevel level,T particle,double x,double y,double z,int count,double dx,double dy,double dz,double speed,Operation<Integer> original){if(particle instanceof BlockParticleOption block&&block.getState().getBlock() instanceof FakeTrackBlock)return 0;return original.call(level,particle,x,y,z,count,dx,dy,dz,speed);}
}
