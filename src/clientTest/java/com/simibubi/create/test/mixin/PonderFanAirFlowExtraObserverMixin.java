package com.simibubi.create.test.mixin;

import com.simibubi.create.test.PonderFanAirFlowAudit;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Read-only immediate queue observation around the real source callback implementation. */
@Mixin(targets="com.simibubi.create.content.kinetics.fan.AirFlowParticle$Access",remap=false)
public abstract class PonderFanAirFlowExtraObserverMixin {
    @Inject(method="spawnExtraParticle(Lnet/minecraft/core/particles/ParticleOptions;F)V",at=@At("HEAD"))
    private void create$beforeExtra(ParticleOptions options,float multiplier,CallbackInfo callback){PonderFanAirFlowAudit.extraHead(this,options,multiplier);}
    @Inject(method="spawnExtraParticle(Lnet/minecraft/core/particles/ParticleOptions;F)V",at=@At("RETURN"))
    private void create$afterExtra(ParticleOptions options,float multiplier,CallbackInfo callback){PonderFanAirFlowAudit.extraReturn(this,options,multiplier);}
}
