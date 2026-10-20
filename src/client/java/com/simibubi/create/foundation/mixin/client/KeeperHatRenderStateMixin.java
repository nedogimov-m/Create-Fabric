package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.equipment.hats.EntityHats;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class KeeperHatRenderStateMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",at=@At("TAIL"))
    private void create$extractKeeperHat(LivingEntity entity,LivingEntityRenderState state,float partial,CallbackInfo ci){EntityHats.extract(entity,state);com.simibubi.create.foundation.render.PlayerSkyhookRenderer.extract(entity,state,partial);}
}
