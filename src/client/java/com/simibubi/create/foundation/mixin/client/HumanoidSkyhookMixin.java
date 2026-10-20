package com.simibubi.create.foundation.mixin.client;
import com.simibubi.create.foundation.render.PlayerSkyhookRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HumanoidModel.class)
public abstract class HumanoidSkyhookMixin {
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",at=@At("TAIL"))
    private void create$skyhookPose(HumanoidRenderState state,CallbackInfo ci){PlayerSkyhookRenderer.apply((HumanoidModel<?>)(Object)this,state);}
}
